"""Client factory: timeout propagation and SignFn support."""

from __future__ import annotations

from t0_usdt_pay_sdk import create_client, create_client_sync
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceClient,
    AcquirerCallbackServiceClientSync,
)


def test_create_client_timeout_propagates():
    priv_hex = "0x" + "ab" * 32
    client = create_client("http://localhost:9999", priv_hex, AcquirerCallbackServiceClient, timeout=30.0)
    assert client._timeout_ms == 30000


def test_create_client_sync_timeout_propagates():
    priv_hex = "0x" + "ab" * 32
    client = create_client_sync("http://localhost:9999", priv_hex, AcquirerCallbackServiceClientSync, timeout=5.0)
    assert client._timeout_ms == 5000


def test_create_client_accepts_sign_fn():
    from coincurve import PrivateKey
    from t0_provider_sdk.crypto.signer import new_signer

    priv = PrivateKey()
    sign_fn = new_signer(priv)
    client = create_client("http://localhost:9999", sign_fn, AcquirerCallbackServiceClient)
    assert client is not None
