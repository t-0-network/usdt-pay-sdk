"""Acquirer starter for the t-0 USDt pay flow.

Work through the numbered TODOs in order; the README explains each phase.
"""

from __future__ import annotations

import asyncio
import logging
import signal
import sys
from uuid import uuid4

import uvicorn
from t0_usdt_pay_sdk import create_asgi_app, create_client, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceASGIApplication,
    AcquirerServiceClient,
)

from acquirer.config import NETWORK_PUBLIC_KEY_HELP, ConfigurationError, load_config
from acquirer.handler import AcquirerCallbacks
from acquirer.internal.create_payment_intent import create_payment_intent
from acquirer.internal.decimals import decimal_from_string, decimal_to_string
from acquirer.internal.get_payment_quote import get_payment_quote
from acquirer.internal.outcome import Accepted, Rejected, Unknown

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)


async def run_demo_sale(t0: AcquirerServiceClient) -> None:
    # TODO: Step 2.1 — replace the demo sale with a real one from your POS.
    # One sale is one currency, one amount and one payment_ref: quote and intent
    # must describe the same sale or you price one thing and charge another.
    local_currency = "COP"
    local_amount = "100000"
    payment_ref = str(uuid4())
    idempotency_key = str(uuid4())

    quoted = await get_payment_quote(t0, local_currency=local_currency, local_amount=local_amount)

    if isinstance(quoted, Accepted):
        logger.info(
            "GetPaymentQuote accepted: quote_id=%s fx_rate=%s expires_at=%s",
            quoted.value.quote_id,
            decimal_to_string(quoted.value.fx_rate),
            quoted.value.expires_at.ToDatetime().isoformat(),
        )
    elif isinstance(quoted, Unknown):
        logger.warning("GetPaymentQuote unanswered — safe to retry")
    elif isinstance(quoted, Rejected):
        logger.warning("GetPaymentQuote rejected: %s", quoted.reason)

    if not isinstance(quoted, Accepted):
        return

    intent = await create_payment_intent(
        t0,
        payment_ref=payment_ref,
        idempotency_key=idempotency_key,
        amount=acquirer_pb2.LocalAmount(value=decimal_from_string(local_amount), currency=local_currency),
        quote_id=quoted.value.quote_id,
    )

    if isinstance(intent, Accepted):
        logger.info(
            "CreatePaymentIntent accepted: payment_intent_id=%s",
            intent.value.payment_intent_id,
        )
        for opt in intent.value.usdt_on_chain.deposit_options:
            logger.info(
                "  deposit option: chain=%s address=%s contract=%s decimals=%d",
                common_pb2.Blockchain.Name(opt.chain),
                opt.deposit_address,
                opt.token_contract,
                opt.token_decimals,
            )
    elif isinstance(intent, Unknown):
        logger.warning("CreatePaymentIntent unanswered — retry the same idempotency_key")
    elif isinstance(intent, Rejected):
        logger.warning("CreatePaymentIntent rejected: %s", intent.reason)


async def serve(server: uvicorn.Server) -> None:
    """``server.serve()``, with a failed bind raised as its ``OSError``.

    uvicorn logs a failed bind and calls ``sys.exit()`` inside its ``except OSError``, so the
    ``SystemExit`` carries the ``OSError`` as its context. Left alone, the ``SystemExit`` would
    end the process with uvicorn's exit code before ``main()`` could report the failure.
    """
    try:
        await server.serve()
    except SystemExit as e:
        if isinstance(e.__context__, Exception):
            raise e.__context__ from None
        raise RuntimeError(f"the callback server exited with status {e.code}") from None


def stop(server: uvicorn.Server) -> None:
    """SIGINT / SIGTERM: stop accepting calls, then drain."""
    server.should_exit = True


def log_if_failed(task: asyncio.Task[None]) -> None:
    if not task.cancelled() and (error := task.exception()) is not None:
        logger.error("Demo sale failed", exc_info=error)


async def main() -> None:
    config = load_config()

    logger.info("Acquirer public key: %s", config.public_key)
    # Phase 1: send this public key and your base URL to the t-0 onboarding
    # contact; put the NETWORK_PUBLIC_KEY you get back into .env.

    # Outbound: everything you call on t-0.
    t0 = create_client(config.tzero_endpoint, config.private_key, AcquirerServiceClient)

    # Inbound: the five callbacks t-0 pushes to you. Every inbound signature is
    # verified against NETWORK_PUBLIC_KEY, which the SDK parses here.
    try:
        app = create_asgi_app(
            config.network_public_key,
            handler(AcquirerCallbackServiceASGIApplication, AcquirerCallbacks()),
        )
    except ValueError as e:
        if not str(e).startswith("invalid network public key: "):
            raise
        raise ConfigurationError(str(e), NETWORK_PUBLIC_KEY_HELP) from None

    # Run ASGI server. host="" listens on all interfaces, IPv4 and IPv6 (where the host has it).
    # A shutdown waits up to 15 s for calls in flight.
    server_config = uvicorn.Config(app, host="", port=config.port, log_level="info", timeout_graceful_shutdown=15)
    server = uvicorn.Server(server_config)

    # uvicorn re-raises SIGINT / SIGTERM once serve() returns, to whatever handler was
    # installed before it started. Installing this one first makes that re-raise land
    # here instead of on the default, which would end the process with 130 or 143.
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, stop, server)

    serving = asyncio.create_task(serve(server))
    while not server.started:
        if serving.done():
            await serving  # raises why the server could not start
            return
        await asyncio.sleep(0.05)
    logger.info("Callback server listening on port %d", config.port)

    # ──────────────────────────────────────────────────────────────────
    # Phase 2 — price a sale, then open an intent for it.
    #
    # The demo sale runs alongside the callback server, which is already
    # listening; a shutdown cancels it if it is still waiting on t-0.
    # ──────────────────────────────────────────────────────────────────

    demo = asyncio.create_task(run_demo_sale(t0))
    demo.add_done_callback(log_if_failed)

    # TODO: Step 2.3 — deploy this service and give the t-0 team its base URL,
    #       so the Phase 3 callbacks can reach you.

    # Phase 3 — the callbacks: at-least-once, dedupe on the key each
    # callback carries. Already wired above; add your business logic
    # where each handler's TODO says.
    #
    # Phase 4 (fiat only) — confirm receipt with settlement_received.
    # Nothing runs on a timer: the bank statement drives this.

    await serving

    demo.cancel()
    await asyncio.wait([demo])


def run() -> int:
    """Run the acquirer; returns the process exit code."""
    try:
        asyncio.run(main())
    except ConfigurationError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        print(e.help_text, file=sys.stderr)
        return 1
    except Exception as e:
        print(f"Acquirer failed to start: {e}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(run())
