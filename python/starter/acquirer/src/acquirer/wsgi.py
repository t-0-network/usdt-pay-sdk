"""Sync entry point for gunicorn / waitress.

Usage:
    gunicorn acquirer.wsgi:app
    waitress-serve --port=8080 acquirer.wsgi:app
"""

from __future__ import annotations

import logging
import sys

from t0_usdt_pay_sdk import create_client_sync, create_wsgi_app, handler_sync
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceWSGIApplication,
    AcquirerServiceClientSync,
)

from acquirer.config import ConfigurationError, load_config
from acquirer.handler_sync import AcquirerCallbacksSync

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

try:
    config = load_config()
except ConfigurationError as e:
    print(f"ERROR: {e}", file=sys.stderr)
    print(e.help_text, file=sys.stderr)
    sys.exit(1)

logger.info("Acquirer public key: %s", config.public_key)

# Outbound: everything you call on t-0 (sync).
t0 = create_client_sync(config.tzero_endpoint, config.private_key, AcquirerServiceClientSync)

# Inbound: callbacks from t-0.
callbacks = AcquirerCallbacksSync()
app = create_wsgi_app(
    config.network_public_key,
    handler_sync(AcquirerCallbackServiceWSGIApplication, callbacks),
)

# TODO: wire your sale flow — call get_payment_quote_sync, create_payment_intent_sync
#   from acquirer.internal using the `t0` client above.
_ = t0
