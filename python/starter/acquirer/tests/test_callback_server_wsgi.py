"""WSGI callback server: callbacks respond via waitress."""

import socket
import threading

import pytest
from acquirer.handler_sync import AcquirerCallbacksSync
from google.protobuf.timestamp_pb2 import Timestamp
from t0_usdt_pay_sdk import create_client_sync, create_wsgi_app, handler_sync
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceClientSync,
    AcquirerCallbackServiceWSGIApplication,
)
from t0_usdt_pay_sdk.keys import public_key_from_private_key
from waitress import create_server

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
def wsgi_server_and_client():
    port = _free_port()
    callbacks = AcquirerCallbacksSync()
    app = create_wsgi_app(
        NETWORK_PUBLIC_KEY,
        handler_sync(AcquirerCallbackServiceWSGIApplication, callbacks),
    )
    server = create_server(app, host="127.0.0.1", port=port)
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()

    client = create_client_sync(
        f"http://127.0.0.1:{port}",
        NETWORK_PRIVATE_KEY,
        AcquirerCallbackServiceClientSync,
    )
    yield client

    server.close()


def test_payment_authorized_wsgi(wsgi_server_and_client):
    client = wsgi_server_and_client
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
    resp = client.payment_authorized(req)
    assert isinstance(resp, acquirer_pb2.PaymentAuthorizedResponse)


def test_payment_expired_wsgi(wsgi_server_and_client):
    client = wsgi_server_and_client
    req = acquirer_pb2.PaymentExpiredRequest(
        payment_intent_id=300,
        payment_ref="order-3",
        expired_at=_now_ts(),
    )
    resp = client.payment_expired(req)
    assert isinstance(resp, acquirer_pb2.PaymentExpiredResponse)
