"""Acquirer starter for the t-0 QR payment flow.

Work through the numbered TODOs in order; the README explains each phase.
"""

from __future__ import annotations

import asyncio
import logging
import sys

import uvicorn
from t0_usdt_pay_sdk import create_asgi_app, create_client, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceASGIApplication,
    AcquirerServiceClient,
)

from acquirer.config import ConfigurationError, load_config
from acquirer.handler import AcquirerCallbacks

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)


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
    # Phase 2 -- open a payment.
    #
    # Nothing runs at startup: every outbound call is driven by the sale,
    # not by a timer.
    #
    # TODO 2.1 (fiat): get_payment_quote(t0, ...) -> quote_id, settlement_amount,
    #          fx_rate, expires_at.
    # TODO 2.2: create_payment_intent(t0, ...) -> payment_intent_id, expires_at,
    #           settlement_amount, deposit options, settlement mode.
    # TODO 2.3: hand each payment_uri to the POS unchanged; show until expires_at.
    #
    # Phase 3 -- the callbacks: at-least-once, dedupe on the key each
    # callback carries. Already wired above; add your business logic
    # where each handler's TODO says.
    #
    # Phase 4 (fiat only) -- confirm receipt with settlement_received.
    # Nothing runs on a timer: the bank statement drives this.
    # ──────────────────────────────────────────────────────────────────
    _ = t0  # used by your sale flow

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
