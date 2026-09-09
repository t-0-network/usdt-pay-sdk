"""ASGI callback server: all five callbacks respond."""

import asyncio
import socket

import pytest
import uvicorn
from acquirer.handler import AcquirerCallbacks
from google.protobuf.timestamp_pb2 import Timestamp
from t0_usdt_pay_sdk import create_asgi_app, create_client, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceASGIApplication,
    AcquirerCallbackServiceClient,
)
from t0_usdt_pay_sdk.keys import public_key_from_private_key

NETWORK_PRIVATE_KEY = "0x" + "ab" * 32
NETWORK_PUBLIC_KEY = public_key_from_private_key(NETWORK_PRIVATE_KEY)


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _now_ts() -> Timestamp:
    ts = Timestamp()
    ts.GetCurrentTime()
    return ts


@pytest.fixture()
async def server_and_client():
    port = _free_port()
    callbacks = AcquirerCallbacks()
    app = create_asgi_app(
        NETWORK_PUBLIC_KEY,
        handler(AcquirerCallbackServiceASGIApplication, callbacks),
    )
    uv_config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="error")
    server = uvicorn.Server(uv_config)
    task = asyncio.create_task(server.serve())
    for _ in range(50):
        if server.started:
            break
        await asyncio.sleep(0.05)

    client = create_client(
        f"http://127.0.0.1:{port}",
        NETWORK_PRIVATE_KEY,
        AcquirerCallbackServiceClient,
    )
    yield client

    server.should_exit = True
    await task


async def test_payment_authorized(server_and_client):
    client = server_and_client
    req = acquirer_pb2.PaymentAuthorizedRequest(
        payment_intent_id=100,
        payment_ref="order-1",
        settlement_amount=common_pb2.Decimal(unscaled=1000, exponent=-2),
        approved_at=_now_ts(),
        received_at=_now_ts(),
    )
    req.usdt_on_chain.chain = common_pb2.BLOCKCHAIN_ETH
    req.usdt_on_chain.on_chain_tx_hash = "0x" + "aa" * 32
    req.usdt_on_chain.sender_address = "0x" + "bb" * 20
    resp = await client.payment_authorized(req)
    assert isinstance(resp, acquirer_pb2.PaymentAuthorizedResponse)


async def test_settlement_initiated(server_and_client):
    client = server_and_client
    req = acquirer_pb2.SettlementInitiatedRequest(
        fiat_settlement_id=2,
        lp_id=10,
        bank_transfer_ref="REF-002",
        settled_payment_intent_ids=[100, 101],
        acquirer_id=42,
        initiated_at=_now_ts(),
        settled_at=_now_ts(),
    )
    req.local.value.CopyFrom(common_pb2.Decimal(unscaled=200000, exponent=0))
    req.local.currency = "COP"
    resp = await client.settlement_initiated(req)
    assert isinstance(resp, acquirer_pb2.SettlementInitiatedResponse)


async def test_payment_expired(server_and_client):
    client = server_and_client
    req = acquirer_pb2.PaymentExpiredRequest(
        payment_intent_id=300,
        payment_ref="order-3",
        expired_at=_now_ts(),
    )
    resp = await client.payment_expired(req)
    assert isinstance(resp, acquirer_pb2.PaymentExpiredResponse)


async def test_payment_failed(server_and_client):
    client = server_and_client
    req = acquirer_pb2.PaymentFailedRequest(
        payment_intent_id=400,
        payment_ref="order-4",
        amount_usdt=common_pb2.Decimal(unscaled=500, exponent=-2),
        disposition=common_pb2.FUNDS_DISPOSITION_RETURNED_TO_SENDER,
        failed_at=_now_ts(),
    )
    req.usdt_on_chain.chain = common_pb2.BLOCKCHAIN_ETH
    req.usdt_on_chain.on_chain_tx_hash = "0x" + "cc" * 32
    req.usdt_on_chain.sender_address = "0x" + "dd" * 20
    resp = await client.payment_failed(req)
    assert isinstance(resp, acquirer_pb2.PaymentFailedResponse)


async def test_settlement_completed(server_and_client):
    client = server_and_client
    req = acquirer_pb2.SettlementCompletedRequest(
        settlement_id=500,
        settlement_amount=common_pb2.Decimal(unscaled=1000, exponent=-2),
        settled_payment_intent_ids=[100, 101],
        settled_at=_now_ts(),
        acquirer_id=42,
    )
    req.settlement.on_chain_tx_hash = "0x" + "ee" * 32
    req.settlement.chain = common_pb2.BLOCKCHAIN_ETH
    req.settlement.destination_address = "0x" + "ff" * 20
    resp = await client.settlement_completed(req)
    assert isinstance(resp, acquirer_pb2.SettlementCompletedResponse)
