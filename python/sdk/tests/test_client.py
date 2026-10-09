"""Client factory: delegation to provider-sdk and SignFn support."""

from __future__ import annotations

import pytest
from t0_usdt_pay_sdk import create_client, create_client_sync
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceClient,
    AcquirerCallbackServiceClientSync,
)


def test_create_client_rejects_empty_base_url():
    with pytest.raises(ValueError, match="base URL is not set"):
        create_client("0x" + "ab" * 32, AcquirerCallbackServiceClient, base_url="")


def test_create_client_sync_rejects_empty_base_url():
    with pytest.raises(ValueError, match="base URL is not set"):
        create_client_sync("0x" + "ab" * 32, AcquirerCallbackServiceClientSync, base_url="")


def test_create_client_accepts_sign_fn():
    from coincurve import PrivateKey
    from t0_provider_sdk.crypto.signer import new_signer

    sign_fn = new_signer(PrivateKey())
    assert create_client(sign_fn, AcquirerCallbackServiceClient, base_url="http://localhost:9999") is not None
    assert create_client_sync(sign_fn, AcquirerCallbackServiceClientSync, base_url="http://localhost:9999") is not None
