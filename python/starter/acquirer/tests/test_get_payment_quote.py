"""GetPaymentQuote: pure functions."""

from acquirer.internal.get_payment_quote import build_request, outcome_from_response
from acquirer.internal.outcome import Accepted, Rejected, Unknown
from google.protobuf.timestamp_pb2 import Timestamp
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2


def test_build_request():
    req = build_request(local_currency="COP", local_amount="100000")
    assert req.local_currency == "COP"
    assert req.local_amount.unscaled == 100000


def test_outcome_from_response_success():
    resp = acquirer_pb2.GetPaymentQuoteResponse()
    ts = Timestamp()
    ts.GetCurrentTime()
    resp.success.quote_id = 42
    resp.success.fx_rate.CopyFrom(common_pb2.Decimal(unscaled=4200, exponent=-2))
    resp.success.settlement_amount.CopyFrom(common_pb2.Decimal(unscaled=2381, exponent=-2))
    resp.success.expires_at.CopyFrom(ts)
    result = outcome_from_response(resp)
    assert isinstance(result, Accepted)
    assert result.value.quote_id == 42


def test_outcome_from_response_failure():
    resp = acquirer_pb2.GetPaymentQuoteResponse()
    resp.failure.reason = acquirer_pb2.GetPaymentQuoteResponse.Failure.REASON_QUOTE_UNAVAILABLE
    result = outcome_from_response(resp)
    assert isinstance(result, Rejected)
    assert "QUOTE_UNAVAILABLE" in result.reason


def test_outcome_from_response_empty():
    resp = acquirer_pb2.GetPaymentQuoteResponse()
    result = outcome_from_response(resp)
    assert isinstance(result, Unknown)
