"""Generated stubs: every Application instantiates, every Client accepts http_client."""

from __future__ import annotations

from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerCallbackServiceASGIApplication,
    AcquirerCallbackServiceClient,
    AcquirerCallbackServiceClientSync,
    AcquirerCallbackServiceWSGIApplication,
    AcquirerServiceASGIApplication,
    AcquirerServiceClient,
    AcquirerServiceClientSync,
    AcquirerServiceWSGIApplication,
)
from t0_usdt_pay_sdk.api.tzero.v1.pay.issuer import issuer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.issuer.issuer_connect import (
    IssuerCallbackServiceASGIApplication,
    IssuerCallbackServiceClient,
    IssuerCallbackServiceClientSync,
    IssuerCallbackServiceWSGIApplication,
    IssuerServiceASGIApplication,
    IssuerServiceClient,
    IssuerServiceClientSync,
    IssuerServiceWSGIApplication,
)
from t0_usdt_pay_sdk.api.tzero.v1.pay.lp import lp_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.lp.lp_connect import (
    LpCallbackServiceASGIApplication,
    LpCallbackServiceClient,
    LpCallbackServiceClientSync,
    LpCallbackServiceWSGIApplication,
    LpServiceASGIApplication,
    LpServiceClient,
    LpServiceClientSync,
    LpServiceWSGIApplication,
)


class _UniversalStub:
    """Implements every method from every service so any Application can
    instantiate with it — both ASGI (lazy) and WSGI (eagerly resolved)."""

    # AcquirerService
    async def get_payment_quote(self, request, ctx):
        return acquirer_pb2.GetPaymentQuoteResponse()

    async def create_payment_intent(self, request, ctx):
        return acquirer_pb2.CreatePaymentIntentResponse()

    async def settlement_received(self, request, ctx):
        return acquirer_pb2.SettlementReceivedResponse()

    # AcquirerCallbackService
    async def payment_authorized(self, request, ctx):
        return acquirer_pb2.PaymentAuthorizedResponse()

    async def settlement_initiated(self, request, ctx):
        return acquirer_pb2.SettlementInitiatedResponse()

    async def settlement_completed(self, request, ctx):
        return acquirer_pb2.SettlementCompletedResponse()

    async def payment_expired(self, request, ctx):
        return acquirer_pb2.PaymentExpiredResponse()

    async def payment_failed(self, request, ctx):
        return acquirer_pb2.PaymentFailedResponse()

    # IssuerService
    async def payment_received(self, request, ctx):
        return issuer_pb2.PaymentReceivedResponse()

    async def settlement_sent(self, request, ctx):
        return issuer_pb2.SettlementSentResponse()

    # IssuerCallbackService
    async def create_payment_instructions(self, request, ctx):
        return issuer_pb2.CreatePaymentInstructionsResponse()

    # LpService
    async def publish_quote(self, request, ctx):
        return lp_pb2.PublishQuoteResponse()

    async def fiat_settlement_sent(self, request, ctx):
        return lp_pb2.FiatSettlementSentResponse()

    # LpCallbackService
    async def execute_quote(self, request, ctx):
        return lp_pb2.ExecuteQuoteResponse()


_ASGI_APPS = [
    (AcquirerServiceASGIApplication, "/tzero.v1.pay.acquirer.AcquirerService"),
    (AcquirerCallbackServiceASGIApplication, "/tzero.v1.pay.acquirer.AcquirerCallbackService"),
    (IssuerServiceASGIApplication, "/tzero.v1.pay.issuer.IssuerService"),
    (IssuerCallbackServiceASGIApplication, "/tzero.v1.pay.issuer.IssuerCallbackService"),
    (LpServiceASGIApplication, "/tzero.v1.pay.lp.LpService"),
    (LpCallbackServiceASGIApplication, "/tzero.v1.pay.lp.LpCallbackService"),
]

_WSGI_APPS = [
    (AcquirerServiceWSGIApplication, "/tzero.v1.pay.acquirer.AcquirerService"),
    (AcquirerCallbackServiceWSGIApplication, "/tzero.v1.pay.acquirer.AcquirerCallbackService"),
    (IssuerServiceWSGIApplication, "/tzero.v1.pay.issuer.IssuerService"),
    (IssuerCallbackServiceWSGIApplication, "/tzero.v1.pay.issuer.IssuerCallbackService"),
    (LpServiceWSGIApplication, "/tzero.v1.pay.lp.LpService"),
    (LpCallbackServiceWSGIApplication, "/tzero.v1.pay.lp.LpCallbackService"),
]

_ASYNC_CLIENTS = [
    AcquirerServiceClient,
    AcquirerCallbackServiceClient,
    IssuerServiceClient,
    IssuerCallbackServiceClient,
    LpServiceClient,
    LpCallbackServiceClient,
]

_SYNC_CLIENTS = [
    AcquirerServiceClientSync,
    AcquirerCallbackServiceClientSync,
    IssuerServiceClientSync,
    IssuerCallbackServiceClientSync,
    LpServiceClientSync,
    LpCallbackServiceClientSync,
]


def test_asgi_applications_have_correct_path():
    stub = _UniversalStub()
    for cls, expected_path in _ASGI_APPS:
        app = cls(stub)
        assert app.path == expected_path, f"{cls.__name__}.path = {app.path!r}"


def test_wsgi_applications_have_correct_path():
    stub = _UniversalStub()
    for cls, expected_path in _WSGI_APPS:
        app = cls(stub)
        assert app.path == expected_path, f"{cls.__name__}.path = {app.path!r}"


def test_async_clients_accept_http_client():
    for cls in _ASYNC_CLIENTS:
        client = cls("http://localhost:0", http_client=None)
        assert client is not None, f"{cls.__name__} failed to instantiate"


def test_sync_clients_accept_http_client():
    for cls in _SYNC_CLIENTS:
        client = cls("http://localhost:0", http_client=None)
        assert client is not None, f"{cls.__name__} failed to instantiate"
