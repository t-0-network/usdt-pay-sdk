"""GetPaymentQuote -- price a fiat payment (fiat settlement mode only)."""

from __future__ import annotations

from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerServiceClient,
    AcquirerServiceClientSync,
)

from acquirer.internal.decimals import decimal_from_string
from acquirer.internal.outcome import Accepted, Outcome, Rejected, Unknown, outcome_from_error


def build_request(
    *,
    local_currency: str,
    local_amount: str,
) -> acquirer_pb2.GetPaymentQuoteRequest:
    return acquirer_pb2.GetPaymentQuoteRequest(
        local_currency=local_currency,
        local_amount=decimal_from_string(local_amount),
    )


def outcome_from_response(
    resp: acquirer_pb2.GetPaymentQuoteResponse,
) -> Outcome[acquirer_pb2.GetPaymentQuoteResponse.Success]:
    which = resp.WhichOneof("result")
    if which == "success":
        return Accepted(resp.success)
    if which == "failure":
        reason_name = acquirer_pb2.GetPaymentQuoteResponse.Failure.Reason.Name(resp.failure.reason)
        return Rejected(reason=reason_name)
    return Unknown("response carried an unrecognised result variant")


async def get_payment_quote(
    t0: AcquirerServiceClient,
    *,
    local_currency: str,
    local_amount: str,
) -> Outcome[acquirer_pb2.GetPaymentQuoteResponse.Success]:
    req = build_request(local_currency=local_currency, local_amount=local_amount)
    try:
        resp = await t0.get_payment_quote(req, timeout_ms=10_000)
    except Exception as e:
        return outcome_from_error(e)
    return outcome_from_response(resp)


def get_payment_quote_sync(
    t0: AcquirerServiceClientSync,
    *,
    local_currency: str,
    local_amount: str,
) -> Outcome[acquirer_pb2.GetPaymentQuoteResponse.Success]:
    req = build_request(local_currency=local_currency, local_amount=local_amount)
    try:
        resp = t0.get_payment_quote(req, timeout_ms=10_000)
    except Exception as e:
        return outcome_from_error(e)
    return outcome_from_response(resp)
