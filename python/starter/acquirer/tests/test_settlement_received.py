"""SettlementReceived: pure functions and end-to-end call."""

import asyncio
import socket
from datetime import UTC, datetime

import pytest
import uvicorn
from acquirer.internal.outcome import Accepted, Rejected
from acquirer.internal.settlement_received import build_request, outcome_from_response, settlement_received
from connectrpc.request import RequestContext
from t0_usdt_pay_sdk import create_asgi_app, create_client, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerServiceASGIApplication,
    AcquirerServiceClient,
)
from t0_usdt_pay_sdk.keys import public_key_from_private_key

ACQUIRER_PRIVATE_KEY = "0x" + "11" * 32
ACQUIRER_PUBLIC_KEY = public_key_from_private_key(ACQUIRER_PRIVATE_KEY)


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class FakeAcquirerService:
    async def get_payment_quote(self, request, ctx):
        raise NotImplementedError

    async def create_payment_intent(self, request, ctx):
        raise NotImplementedError

    async def settlement_received(
        self,
        request: acquirer_pb2.SettlementReceivedRequest,
        ctx: RequestContext,
    ) -> acquirer_pb2.SettlementReceivedResponse:
        if request.bank_transfer_ref == "reject-me":
            resp = acquirer_pb2.SettlementReceivedResponse()
            resp.rejected.reason = acquirer_pb2.SettlementReceivedResponse.Rejected.REASON_AMOUNT_MISMATCH
            return resp

        resp = acquirer_pb2.SettlementReceivedResponse()
        resp.accepted.SetInParent()
        return resp


def test_build_request():
    req = build_request(
        lp_id=10,
        bank_transfer_ref="REF-001",
        local_currency="COP",
        amount_received="100000",
        received_at=datetime(2025, 1, 1, tzinfo=UTC),
    )
    assert req.lp_id == 10
    assert req.bank_transfer_ref == "REF-001"
    assert req.local_currency == "COP"
    assert req.amount_received.unscaled == 100000


def test_outcome_from_response_accepted():
    resp = acquirer_pb2.SettlementReceivedResponse()
    resp.accepted.SetInParent()
    result = outcome_from_response(resp)
    assert isinstance(result, Accepted)


def test_outcome_from_response_rejected():
    resp = acquirer_pb2.SettlementReceivedResponse()
    resp.rejected.reason = acquirer_pb2.SettlementReceivedResponse.Rejected.REASON_AMOUNT_MISMATCH
    result = outcome_from_response(resp)
    assert isinstance(result, Rejected)
    assert "AMOUNT_MISMATCH" in result.reason


@pytest.fixture()
async def fake_t0():
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


async def test_settlement_received_accepted(fake_t0):
    result = await settlement_received(
        fake_t0,
        lp_id=10,
        bank_transfer_ref="REF-001",
        local_currency="COP",
        amount_received="100000",
        received_at=datetime(2025, 1, 1, tzinfo=UTC),
    )
    assert isinstance(result, Accepted)


async def test_settlement_received_rejected(fake_t0):
    result = await settlement_received(
        fake_t0,
        lp_id=10,
        bank_transfer_ref="reject-me",
        local_currency="COP",
        amount_received="100000",
        received_at=datetime(2025, 1, 1, tzinfo=UTC),
    )
    assert isinstance(result, Rejected)
    assert "AMOUNT_MISMATCH" in result.reason
