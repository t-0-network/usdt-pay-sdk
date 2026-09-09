"""CreatePaymentIntent -- open a payment."""

from __future__ import annotations

from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerServiceClient,
    AcquirerServiceClientSync,
)

from acquirer.internal.outcome import Accepted, Outcome, Rejected, Unknown, outcome_from_error


def build_request(
    *,
    payment_ref: str,
    idempotency_key: str,
    amount: acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount | acquirer_pb2.LocalAmount,
    quote_id: int | None = None,
) -> acquirer_pb2.CreatePaymentIntentRequest:
    req = acquirer_pb2.CreatePaymentIntentRequest(
        payment_ref=payment_ref,
        idempotency_key=idempotency_key,
    )
    if isinstance(amount, acquirer_pb2.LocalAmount):
        req.local.CopyFrom(amount)
    else:
        req.settlement.CopyFrom(amount)
    if quote_id is not None:
        req.quote_id = quote_id
    return req


def outcome_from_response(
    resp: acquirer_pb2.CreatePaymentIntentResponse,
) -> Outcome[acquirer_pb2.CreatePaymentIntentResponse.Success]:
    which = resp.WhichOneof("result")
    if which == "success":
        return Accepted(resp.success)
    if which == "failure":
        reason_name = acquirer_pb2.CreatePaymentIntentResponse.Failure.Reason.Name(resp.failure.reason)
        return Rejected(reason=reason_name)
    return Unknown("response carried an unrecognised result variant")


async def create_payment_intent(
    t0: AcquirerServiceClient,
    *,
    payment_ref: str,
    idempotency_key: str,
    amount: acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount | acquirer_pb2.LocalAmount,
    quote_id: int | None = None,
) -> Outcome[acquirer_pb2.CreatePaymentIntentResponse.Success]:
    req = build_request(
        payment_ref=payment_ref,
        idempotency_key=idempotency_key,
        amount=amount,
        quote_id=quote_id,
    )
    try:
        resp = await t0.create_payment_intent(req, timeout_ms=15_000)
    except Exception as e:
        return outcome_from_error(e)
    return outcome_from_response(resp)


def create_payment_intent_sync(
    t0: AcquirerServiceClientSync,
    *,
    payment_ref: str,
    idempotency_key: str,
    amount: acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount | acquirer_pb2.LocalAmount,
    quote_id: int | None = None,
) -> Outcome[acquirer_pb2.CreatePaymentIntentResponse.Success]:
    req = build_request(
        payment_ref=payment_ref,
        idempotency_key=idempotency_key,
        amount=amount,
        quote_id=quote_id,
    )
    try:
        resp = t0.create_payment_intent(req, timeout_ms=15_000)
    except Exception as e:
        return outcome_from_error(e)
    return outcome_from_response(resp)
