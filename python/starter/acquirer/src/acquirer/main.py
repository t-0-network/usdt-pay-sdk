"""Acquirer starter for the t-0 USDt pay flow.

Work through the numbered TODOs in order; the README explains each phase.
"""

from __future__ import annotations

import asyncio
import logging
import sys
from uuid import uuid4

import uvicorn
from t0_usdt_pay_sdk import create_asgi_app, create_client, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceASGIApplication,
    AcquirerServiceClient,
)

from acquirer.config import ConfigurationError, load_config
from acquirer.handler import AcquirerCallbacks
from acquirer.internal.create_payment_intent import create_payment_intent
from acquirer.internal.decimals import decimal_from_string, decimal_to_string
from acquirer.internal.get_payment_quote import get_payment_quote
from acquirer.internal.outcome import Accepted

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
    elif quoted.should_retry:
        logger.warning("GetPaymentQuote unanswered — safe to retry")
    else:
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
            logger.info("  deposit option: %s", opt.payment_uri)
    elif intent.should_retry:
        logger.warning("CreatePaymentIntent unanswered — retry the same idempotency_key")
    else:
        logger.warning("CreatePaymentIntent rejected: %s", intent.reason)


async def main() -> None:
    config = load_config()

    logger.info("Acquirer public key: %s", config.public_key)
    # Phase 1: send this public key and your base URL to the t-0 onboarding
    # contact; put the NETWORK_PUBLIC_KEY you get back into .env.

    # Outbound: everything you call on t-0. Each internal/ helper sets its own
    # timeout per call.
    t0 = create_client(config.tzero_endpoint, config.private_key, AcquirerServiceClient)

    # Inbound: the five callbacks t-0 pushes to you. Every inbound signature is
    # verified against NETWORK_PUBLIC_KEY.
    callbacks = AcquirerCallbacks()
    app = create_asgi_app(
        config.network_public_key,
        handler(AcquirerCallbackServiceASGIApplication, callbacks),
    )

    server_config = uvicorn.Config(app, host="0.0.0.0", port=config.port, log_level="info")
    server = uvicorn.Server(server_config)

    logger.info("Callback server listening on port %d", config.port)

    # ──────────────────────────────────────────────────────────────────
    # Phase 2 — price a sale, then open an intent for it.
    #
    # The demo runs before the server starts because uvicorn's serve()
    # blocks. Nothing depends on the acquirer's callback server being
    # up when CreatePaymentIntent is sent (t-0 calls the issuer inline,
    # not the acquirer).
    # ──────────────────────────────────────────────────────────────────

    await run_demo_sale(t0)

    # TODO: Step 2.3 — deploy this service and give the t-0 team its base URL,
    #       so the Phase 3 callbacks can reach you.

    # Phase 3 — the callbacks: at-least-once, dedupe on the key each
    # callback carries. Already wired above; add your business logic
    # where each handler's TODO says.
    #
    # Phase 4 (fiat only) — confirm receipt with settlement_received.
    # Nothing runs on a timer: the bank statement drives this.

    await server.serve()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except ConfigurationError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        print(e.help_text, file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        pass
