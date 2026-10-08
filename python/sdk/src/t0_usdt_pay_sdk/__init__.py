"""T-0 Network USDt Pay SDK for Python.

Import order below is load-bearing: ``t0_provider_sdk`` must be imported before
our ``api/`` goes on ``sys.path``, so provider-sdk's ``buf.validate`` descriptors
win in the pool. Our ``api/tzero/`` has no ``__init__.py`` (PEP 420 namespace),
so it merges with provider-sdk's ``tzero/v1/`` packages.
"""

import sys
from pathlib import Path

import t0_provider_sdk  # noqa: F401 — registers buf.validate descriptors

_api_dir = str(Path(__file__).parent / "api")
if _api_dir not in sys.path:
    sys.path.insert(0, _api_dir)

from connectrpc.code import Code  # noqa: E402
from connectrpc.errors import ConnectError  # noqa: E402

import t0_usdt_pay_sdk.registry  # noqa: F401, E402 — registers pay descriptors
from t0_usdt_pay_sdk._version import __version__  # noqa: E402
from t0_usdt_pay_sdk.client import create_client, create_client_sync  # noqa: E402
from t0_usdt_pay_sdk.crypto import (  # noqa: E402
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
    SignatureErrorInterceptor,
    SignatureErrorInterceptorSync,
    SignFn,
    legacy_keccak256,
    new_signer_from_hex,
    new_verify_signature,
    signature_error_var,
    verify_signature,
)
from t0_usdt_pay_sdk.keys import public_key_from_private_key  # noqa: E402
from t0_usdt_pay_sdk.server import (  # noqa: E402
    DEFAULT_MAX_BODY_SIZE,
    BuildHandler,
    BuildHandlerSync,
    NetworkPublicKeyRequiredError,
    create_asgi_app,
    create_wsgi_app,
    handler,
    handler_sync,
    validate,
)

__all__ = [
    "__version__",
    "DEFAULT_MAX_BODY_SIZE",
    "PUBLIC_KEY_HEADER",
    "SIGNATURE_HEADER",
    "SIGNATURE_TIMESTAMP_HEADER",
    "BuildHandler",
    "BuildHandlerSync",
    "Code",
    "ConnectError",
    "NetworkPublicKeyRequiredError",
    "SignFn",
    "SignatureErrorInterceptor",
    "SignatureErrorInterceptorSync",
    "create_asgi_app",
    "create_client",
    "create_client_sync",
    "create_wsgi_app",
    "handler",
    "handler_sync",
    "legacy_keccak256",
    "new_signer_from_hex",
    "new_verify_signature",
    "public_key_from_private_key",
    "signature_error_var",
    "validate",
    "verify_signature",
]
