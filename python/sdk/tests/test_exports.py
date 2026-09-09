"""Public API surface: __all__ and endpoint validation."""

from __future__ import annotations

import pytest
import t0_usdt_pay_sdk


def test_all_is_complete():
    expected = {
        "__version__",
        "BuildHandler",
        "BuildHandlerSync",
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
        "signature_verification_middleware",
        "signature_verification_middleware_wsgi",
        "validate",
        "verify_signature",
    }
    assert set(t0_usdt_pay_sdk.__all__) == expected


def test_all_entries_are_importable():
    for name in t0_usdt_pay_sdk.__all__:
        assert hasattr(t0_usdt_pay_sdk, name), f"{name} in __all__ but not an attribute"


def test_create_client_rejects_empty_endpoint():
    from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerServiceClient

    with pytest.raises(ValueError, match="endpoint is required"):
        t0_usdt_pay_sdk.create_client("", "0x" + "ab" * 32, AcquirerServiceClient)


def test_create_client_sync_rejects_empty_endpoint():
    from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerServiceClientSync

    with pytest.raises(ValueError, match="endpoint is required"):
        t0_usdt_pay_sdk.create_client_sync("  ", "0x" + "ab" * 32, AcquirerServiceClientSync)


def test_create_asgi_app_rejects_empty_key():
    with pytest.raises(ValueError, match="network_public_key is required"):
        t0_usdt_pay_sdk.create_asgi_app("")


def test_create_wsgi_app_rejects_empty_key():
    with pytest.raises(ValueError, match="network_public_key is required"):
        t0_usdt_pay_sdk.create_wsgi_app("")
