"""ASGI callback server: all five callbacks respond."""

import asyncio
import logging
import socket
from unittest.mock import AsyncMock

import pytest
import uvicorn
from acquirer.handler import AcquirerCallbacks
from acquirer.main import run_demo_sale
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from google.protobuf.timestamp_pb2 import Timestamp
from t0_usdt_pay_sdk import create_asgi_app, create_client, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceASGIApplication,
    AcquirerCallbackServiceClient,
    AcquirerServiceClient,
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
        NETWORK_PRIVATE_KEY,
        AcquirerCallbackServiceClient,
        base_url=f"http://127.0.0.1:{port}",
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
        reason=common_pb2.PAYMENT_FAILURE_REASON_AMOUNT_MISMATCH,
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


@pytest.mark.parametrize("method", ["get_payment_quote", "create_payment_intent"])
@pytest.mark.parametrize(
    "error",
    [
        ConnectError(Code.UNAVAILABLE, "t-0 is down"),
        ConnectError(Code.INVALID_ARGUMENT, "bad field"),
        RuntimeError("local failure"),
    ],
)
async def test_demo_failure_logs_once_and_callbacks_keep_serving(server_and_client, caplog, method, error):
    t0 = AsyncMock(spec=AcquirerServiceClient)
    t0.get_payment_quote.return_value = acquirer_pb2.GetPaymentQuoteResponse(
        success=acquirer_pb2.GetPaymentQuoteResponse.Success(
            quote_id=42,
            fx_rate=common_pb2.Decimal(unscaled=4000, exponent=0),
            expires_at=_now_ts(),
        ),
    )
    getattr(t0, method).side_effect = error
    caplog.set_level(logging.WARNING, logger="acquirer.main")

    await run_demo_sale(t0)

    records = [record for record in caplog.records if record.name == "acquirer.main"]
    assert len(records) == 1
    call_name = "GetPaymentQuote" if method == "get_payment_quote" else "CreatePaymentIntent"
    assert records[0].getMessage().startswith(call_name)
    assert (error.message if isinstance(error, ConnectError) else str(error)) in records[0].getMessage()
    assert records[0].exc_info is None
    assert "\n" not in logging.Formatter("%(message)s").format(records[0])
    if method == "get_payment_quote":
        t0.create_payment_intent.assert_not_awaited()

    response = await server_and_client.payment_expired(
        acquirer_pb2.PaymentExpiredRequest(payment_intent_id=300, payment_ref="order-3", expired_at=_now_ts()),
    )
    assert isinstance(response, acquirer_pb2.PaymentExpiredResponse)
