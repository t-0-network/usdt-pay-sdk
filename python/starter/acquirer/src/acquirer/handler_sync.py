"""Sync callback handlers for the acquirer role.

Same logic as handler.py, no ``await``. Use with gunicorn / waitress via wsgi.py.
"""

from __future__ import annotations

import logging

from connectrpc.request import RequestContext
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2

from acquirer.internal.decimals import decimal_to_string

logger = logging.getLogger(__name__)


class AcquirerCallbacksSync:
    def payment_authorized(
        self,
        request: acquirer_pb2.PaymentAuthorizedRequest,
        ctx: RequestContext,  # type: ignore[type-arg]
    ) -> acquirer_pb2.PaymentAuthorizedResponse:
        logger.info(
            "PaymentAuthorized: intent=%d ref=%s amount=%s USDt",
            request.payment_intent_id,
            request.payment_ref,
            decimal_to_string(request.settlement_amount) if request.HasField("settlement_amount") else "?",
        )
        # TODO: record this event idempotently under payment_intent_id, then
        #   notify the merchant / POS that the payment is approved.
        return acquirer_pb2.PaymentAuthorizedResponse()

    def settlement_initiated(
        self,
        request: acquirer_pb2.SettlementInitiatedRequest,
        ctx: RequestContext,  # type: ignore[type-arg]
    ) -> acquirer_pb2.SettlementInitiatedResponse:
        logger.info(
            "SettlementInitiated: fiat_settlement=%d lp=%d ref=%s intents=%s",
            request.fiat_settlement_id,
            request.lp_id,
            request.bank_transfer_ref,
            list(request.settled_payment_intent_ids),
        )
        # TODO: record idempotently under fiat_settlement_id. Store the expected
        #   transfer (lp_id, bank_transfer_ref) so you can match it on your bank
        #   statement and then call settlement_received.
        return acquirer_pb2.SettlementInitiatedResponse()

    def settlement_completed(
        self,
        request: acquirer_pb2.SettlementCompletedRequest,
        ctx: RequestContext,  # type: ignore[type-arg]
    ) -> acquirer_pb2.SettlementCompletedResponse:
        logger.info(
            "SettlementCompleted: settlement=%d intents=%s",
            request.settlement_id,
            list(request.settled_payment_intent_ids),
        )
        # TODO: record idempotently under settlement_id. Mark the listed intents
        #   as SETTLED — this is terminal in USDt settlement mode.
        return acquirer_pb2.SettlementCompletedResponse()

    def payment_expired(
        self,
        request: acquirer_pb2.PaymentExpiredRequest,
        ctx: RequestContext,  # type: ignore[type-arg]
    ) -> acquirer_pb2.PaymentExpiredResponse:
        logger.info("PaymentExpired: intent=%d ref=%s", request.payment_intent_id, request.payment_ref)
        # TODO: record idempotently under payment_intent_id. Clear the pending
        #   order — the QR window lapsed with no payment.
        return acquirer_pb2.PaymentExpiredResponse()

    def payment_failed(
        self,
        request: acquirer_pb2.PaymentFailedRequest,
        ctx: RequestContext,  # type: ignore[type-arg]
    ) -> acquirer_pb2.PaymentFailedResponse:
        logger.info(
            "PaymentFailed: intent=%d ref=%s disposition=%s",
            request.payment_intent_id,
            request.payment_ref,
            common_pb2.FundsDisposition.Name(request.disposition) if request.disposition else "UNKNOWN",
        )
        # TODO: record idempotently under payment_intent_id. Close the pending
        #   order and tell the customer the outcome. request.disposition says
        #   where the funds went (RETURNED_TO_SENDER or RETAINED_BY_ISSUER).
        return acquirer_pb2.PaymentFailedResponse()
