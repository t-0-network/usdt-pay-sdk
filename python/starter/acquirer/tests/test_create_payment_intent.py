"""CreatePaymentIntent tests: fake t-0 AcquirerService, async + sync."""

import asyncio
import socket
import threading

import pytest
import uvicorn
from acquirer.internal.create_payment_intent import (
    build_request,
    create_payment_intent,
    create_payment_intent_sync,
    outcome_from_response,
)
from acquirer.internal.outcome import Accepted, Rejected, Unknown
from connectrpc.request import RequestContext
from google.protobuf.timestamp_pb2 import Timestamp
from t0_usdt_pay_sdk import create_asgi_app, create_client, create_client_sync, create_wsgi_app, handler, handler_sync
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerServiceASGIApplication,
    AcquirerServiceClient,
    AcquirerServiceClientSync,
    AcquirerServiceWSGIApplication,
)
from t0_usdt_pay_sdk.keys import public_key_from_private_key
from waitress import create_server

# The acquirer's key (the one sending requests to t-0)
ACQUIRER_PRIVATE_KEY = "0x" + "11" * 32
ACQUIRER_PUBLIC_KEY = public_key_from_private_key(ACQUIRER_PRIVATE_KEY)


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _now_ts() -> Timestamp:
    ts = Timestamp()
    ts.GetCurrentTime()
    return ts


class FakeAcquirerService:
    """Async fake t-0 AcquirerService that accepts or rejects based on payment_ref."""

    async def get_payment_quote(self, request, ctx):
        raise NotImplementedError

    async def create_payment_intent(
        self,
        request: acquirer_pb2.CreatePaymentIntentRequest,
        ctx: RequestContext,
    ) -> acquirer_pb2.CreatePaymentIntentResponse:
        if request.payment_ref == "reject-me":
            resp = acquirer_pb2.CreatePaymentIntentResponse()
            resp.failure.reason = acquirer_pb2.CreatePaymentIntentResponse.Failure.REASON_AMOUNT_OUT_OF_RANGE
            return resp

        resp = acquirer_pb2.CreatePaymentIntentResponse()
        success = resp.success
        success.payment_intent_id = 12345
        success.expires_at.CopyFrom(_now_ts())
        success.settlement_amount.CopyFrom(common_pb2.Decimal(unscaled=1000, exponent=-2))

        deposit = success.usdt_on_chain.deposit_options.add()
        deposit.chain = common_pb2.BLOCKCHAIN_ETH
        deposit.deposit_address = "0x" + "aa" * 20
        deposit.payment_uri = "ethereum:0x" + "aa" * 20
        deposit.token_contract = "0x" + "bb" * 20

        success.onchain.SetInParent()
        return resp

    async def settlement_received(self, request, ctx):
        raise NotImplementedError


class FakeAcquirerServiceSync:
    """Sync fake t-0 AcquirerService."""

    def get_payment_quote(self, request, ctx):
        raise NotImplementedError

    def create_payment_intent(
        self,
        request: acquirer_pb2.CreatePaymentIntentRequest,
        ctx: RequestContext,
    ) -> acquirer_pb2.CreatePaymentIntentResponse:
        if request.payment_ref == "reject-me":
            resp = acquirer_pb2.CreatePaymentIntentResponse()
            resp.failure.reason = acquirer_pb2.CreatePaymentIntentResponse.Failure.REASON_AMOUNT_OUT_OF_RANGE
            return resp

        resp = acquirer_pb2.CreatePaymentIntentResponse()
        success = resp.success
        success.payment_intent_id = 12345
        success.expires_at.CopyFrom(_now_ts())
        success.settlement_amount.CopyFrom(common_pb2.Decimal(unscaled=1000, exponent=-2))
        deposit = success.usdt_on_chain.deposit_options.add()
        deposit.chain = common_pb2.BLOCKCHAIN_ETH
        deposit.deposit_address = "0x" + "aa" * 20
        deposit.payment_uri = "ethereum:0x" + "aa" * 20
        deposit.token_contract = "0x" + "bb" * 20
        success.onchain.SetInParent()
        return resp

    def settlement_received(self, request, ctx):
        raise NotImplementedError


def test_build_request_settlement_amount():
    sa = acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount(
        value=common_pb2.Decimal(unscaled=1000, exponent=-2),
    )
    req = build_request(
        payment_ref="order-1",
        idempotency_key="key-1",
        amount=sa,
    )
    assert req.WhichOneof("amount") == "settlement"
    assert req.payment_ref == "order-1"


def test_build_request_local_amount():
    la = acquirer_pb2.LocalAmount(
        value=common_pb2.Decimal(unscaled=100000, exponent=0),
        currency="COP",
    )
    req = build_request(
        payment_ref="order-2",
        idempotency_key="key-2",
        amount=la,
    )
    assert req.WhichOneof("amount") == "local"


def test_build_request_with_quote_id():
    sa = acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount(
        value=common_pb2.Decimal(unscaled=1000, exponent=-2),
    )
    req = build_request(
        payment_ref="order-3",
        idempotency_key="key-3",
        amount=sa,
        quote_id=42,
    )
    assert req.quote_id == 42


def test_outcome_from_response_success():
    resp = acquirer_pb2.CreatePaymentIntentResponse()
    resp.success.payment_intent_id = 1
    resp.success.expires_at.CopyFrom(_now_ts())
    resp.success.settlement_amount.CopyFrom(common_pb2.Decimal(unscaled=100, exponent=-2))
    deposit = resp.success.usdt_on_chain.deposit_options.add()
    deposit.chain = common_pb2.BLOCKCHAIN_ETH
    deposit.deposit_address = "0x" + "aa" * 20
    deposit.payment_uri = "ethereum:test"
    deposit.token_contract = "0x" + "bb" * 20
    resp.success.onchain.SetInParent()
    result = outcome_from_response(resp)
    assert isinstance(result, Accepted)
    assert result.value.payment_intent_id == 1


def test_outcome_from_response_failure():
    resp = acquirer_pb2.CreatePaymentIntentResponse()
    resp.failure.reason = acquirer_pb2.CreatePaymentIntentResponse.Failure.REASON_AMOUNT_OUT_OF_RANGE
    result = outcome_from_response(resp)
    assert isinstance(result, Rejected)
    assert "AMOUNT_OUT_OF_RANGE" in result.reason


def test_outcome_from_response_unknown_variant():
    resp = acquirer_pb2.CreatePaymentIntentResponse()
    result = outcome_from_response(resp)
    assert isinstance(result, Unknown)


@pytest.fixture()
async def fake_t0_async():
    port = _free_port()
    fake = FakeAcquirerService()
    app = create_asgi_app(
        ACQUIRER_PUBLIC_KEY,
        handler(AcquirerServiceASGIApplication, fake),
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
        ACQUIRER_PRIVATE_KEY,
        AcquirerServiceClient,
    )
    yield client

    server.should_exit = True
    await task


async def test_create_payment_intent_accepted_async(fake_t0_async):
    sa = acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount(
        value=common_pb2.Decimal(unscaled=1000, exponent=-2),
    )
    result = await create_payment_intent(
        fake_t0_async,
        payment_ref="order-1",
        idempotency_key="key-1",
        amount=sa,
    )
    assert isinstance(result, Accepted)
    assert result.value.payment_intent_id == 12345


async def test_create_payment_intent_rejected_async(fake_t0_async):
    sa = acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount(
        value=common_pb2.Decimal(unscaled=1000, exponent=-2),
    )
    result = await create_payment_intent(
        fake_t0_async,
        payment_ref="reject-me",
        idempotency_key="key-r",
        amount=sa,
    )
    assert isinstance(result, Rejected)
    assert "AMOUNT_OUT_OF_RANGE" in result.reason


@pytest.fixture()
def fake_t0_sync():
    port = _free_port()
    fake = FakeAcquirerServiceSync()
    app = create_wsgi_app(
        ACQUIRER_PUBLIC_KEY,
        handler_sync(AcquirerServiceWSGIApplication, fake),
    )
    server = create_server(app, host="127.0.0.1", port=port)
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()

    client = create_client_sync(
        f"http://127.0.0.1:{port}",
        ACQUIRER_PRIVATE_KEY,
        AcquirerServiceClientSync,
    )
    yield client

    server.close()


def test_create_payment_intent_accepted_sync(fake_t0_sync):
    sa = acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount(
        value=common_pb2.Decimal(unscaled=1000, exponent=-2),
    )
    result = create_payment_intent_sync(
        fake_t0_sync,
        payment_ref="order-1",
        idempotency_key="key-1",
        amount=sa,
    )
    assert isinstance(result, Accepted)
    assert result.value.payment_intent_id == 12345


def test_create_payment_intent_rejected_sync(fake_t0_sync):
    sa = acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount(
        value=common_pb2.Decimal(unscaled=1000, exponent=-2),
    )
    result = create_payment_intent_sync(
        fake_t0_sync,
        payment_ref="reject-me",
        idempotency_key="key-r",
        amount=sa,
    )
    assert isinstance(result, Rejected)
