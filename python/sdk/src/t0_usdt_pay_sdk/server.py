"""Server factories for inbound callbacks from t-0."""

from __future__ import annotations

import logging

from t0_provider_sdk.provider.handler import (
    BuildHandler,
    BuildHandlerSync,
    handler,
    handler_sync,
)
from t0_provider_sdk.provider.handler import (
    new_asgi_app as _new_asgi_app,
)
from t0_provider_sdk.provider.handler import (
    new_wsgi_app as _new_wsgi_app,
)
from t0_provider_sdk.provider.middleware import ASGIApp
from t0_provider_sdk.provider.middleware_wsgi import WSGIApp
from t0_provider_sdk.provider.validate import validate

from t0_usdt_pay_sdk._version import __version__


def create_asgi_app(
    network_public_key: str,
    *build_handlers: BuildHandler,
    logger: logging.Logger | None = None,
) -> ASGIApp:
    """Create an ASGI app for receiving t-0 callbacks with signature verification."""
    if not network_public_key or not network_public_key.strip():
        raise ValueError("network_public_key is required — an empty key disables signature verification")
    return _new_asgi_app(network_public_key, *build_handlers, logger=logger, version=__version__)


def create_wsgi_app(
    network_public_key: str,
    *build_handlers: BuildHandlerSync,
    logger: logging.Logger | None = None,
) -> WSGIApp:
    """Create a WSGI app for receiving t-0 callbacks with signature verification."""
    if not network_public_key or not network_public_key.strip():
        raise ValueError("network_public_key is required — an empty key disables signature verification")
    return _new_wsgi_app(network_public_key, *build_handlers, logger=logger, version=__version__)


__all__ = [
    "BuildHandler",
    "BuildHandlerSync",
    "create_asgi_app",
    "create_wsgi_app",
    "handler",
    "handler_sync",
    "validate",
]
