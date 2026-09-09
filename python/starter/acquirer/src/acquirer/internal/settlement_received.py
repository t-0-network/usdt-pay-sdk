"""SettlementReceived -- confirm fiat receipt (fiat settlement mode only)."""

from __future__ import annotations

from datetime import datetime

from google.protobuf.timestamp_pb2 import Timestamp
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import (
    AcquirerServiceClient,
    AcquirerServiceClientSync,
)

from acquirer.internal.decimals import decimal_from_string
from acquirer.internal.outcome import Accepted, Outcome, Rejected, Unknown, outcome_from_error


def build_request(
    *,
    lp_id: int,
    bank_transfer_ref: str,
    local_currency: str,
    amount_received: str,
    received_at: datetime,
) -> acquirer_pb2.SettlementReceivedRequest:
    ts = Timestamp()
    ts.FromDatetime(received_at)
    return acquirer_pb2.SettlementReceivedRequest(
        lp_id=lp_id,
        bank_transfer_ref=bank_transfer_ref,
        local_currency=local_currency,
        amount_received=decimal_from_string(amount_received),
        received_at=ts,
    )


def outcome_from_response(
    resp: acquirer_pb2.SettlementReceivedResponse,
) -> Outcome[acquirer_pb2.SettlementReceivedResponse.Accepted]:
    which = resp.WhichOneof("result")
    if which == "accepted":
        return Accepted(resp.accepted)
    if which == "rejected":
        reason_name = acquirer_pb2.SettlementReceivedResponse.Rejected.Reason.Name(resp.rejected.reason)
        return Rejected(reason=reason_name)
    return Unknown("response carried an unrecognised result variant")


async def settlement_received(
    t0: AcquirerServiceClient,
    *,
    lp_id: int,
    bank_transfer_ref: str,
    local_currency: str,
    amount_received: str,
    received_at: datetime,
) -> Outcome[acquirer_pb2.SettlementReceivedResponse.Accepted]:
    req = build_request(
        lp_id=lp_id,
        bank_transfer_ref=bank_transfer_ref,
        local_currency=local_currency,
        amount_received=amount_received,
        received_at=received_at,
    )
    try:
        resp = await t0.settlement_received(req, timeout_ms=15_000)
    except Exception as e:
        return outcome_from_error(e)
    return outcome_from_response(resp)


def settlement_received_sync(
    t0: AcquirerServiceClientSync,
    *,
    lp_id: int,
    bank_transfer_ref: str,
    local_currency: str,
    amount_received: str,
    received_at: datetime,
) -> Outcome[acquirer_pb2.SettlementReceivedResponse.Accepted]:
    req = build_request(
        lp_id=lp_id,
        bank_transfer_ref=bank_transfer_ref,
        local_currency=local_currency,
        amount_received=amount_received,
        received_at=received_at,
    )
    try:
        resp = t0.settlement_received(req, timeout_ms=15_000)
    except Exception as e:
        return outcome_from_error(e)
    return outcome_from_response(resp)
