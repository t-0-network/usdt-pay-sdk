"""Public API surface: __all__ and network key validation."""

from __future__ import annotations

import pytest
import t0_usdt_pay_sdk


def test_all_is_complete():
    expected = {
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
    }
    assert set(t0_usdt_pay_sdk.__all__) == expected


def test_all_entries_are_importable():
    for name in t0_usdt_pay_sdk.__all__:
        assert hasattr(t0_usdt_pay_sdk, name), f"{name} in __all__ but not an attribute"


def test_reexports_are_the_provider_sdk_and_connectrpc_objects():
    from connectrpc.code import Code
    from connectrpc.errors import ConnectError
    from t0_provider_sdk.crypto import public_key_from_private_key

    assert t0_usdt_pay_sdk.ConnectError is ConnectError
    assert t0_usdt_pay_sdk.Code is Code
    assert t0_usdt_pay_sdk.public_key_from_private_key is public_key_from_private_key


def test_create_asgi_app_rejects_empty_key():
    with pytest.raises(t0_usdt_pay_sdk.NetworkPublicKeyRequiredError, match="network public key is not set"):
        t0_usdt_pay_sdk.create_asgi_app("")


def test_create_wsgi_app_rejects_empty_key():
    with pytest.raises(ValueError, match="network public key is not set"):
        t0_usdt_pay_sdk.create_wsgi_app("")
