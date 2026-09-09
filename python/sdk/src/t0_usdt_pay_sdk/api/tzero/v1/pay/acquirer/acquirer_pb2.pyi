import datetime

from buf.validate import validate_pb2 as _validate_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from tzero.v1.pay import common_pb2 as _common_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class LocalAmount(_message.Message):
    __slots__ = ("value", "currency")
    VALUE_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    value: _common_pb2.Decimal
    currency: str
    def __init__(self, value: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., currency: _Optional[str] = ...) -> None: ...

class FiatSettlement(_message.Message):
    __slots__ = ("quote_id", "fx_rate", "local")
    QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
    FX_RATE_FIELD_NUMBER: _ClassVar[int]
    LOCAL_FIELD_NUMBER: _ClassVar[int]
    quote_id: int
    fx_rate: _common_pb2.Decimal
    local: LocalAmount
    def __init__(self, quote_id: _Optional[int] = ..., fx_rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., local: _Optional[_Union[LocalAmount, _Mapping]] = ...) -> None: ...

class GetPaymentQuoteRequest(_message.Message):
    __slots__ = ("local_currency", "local_amount")
    LOCAL_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    LOCAL_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    local_currency: str
    local_amount: _common_pb2.Decimal
    def __init__(self, local_currency: _Optional[str] = ..., local_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...

class GetPaymentQuoteResponse(_message.Message):
    __slots__ = ("success", "failure")
    class Success(_message.Message):
        __slots__ = ("quote_id", "settlement_amount", "fx_rate", "expires_at")
        QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
        SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        FX_RATE_FIELD_NUMBER: _ClassVar[int]
        EXPIRES_AT_FIELD_NUMBER: _ClassVar[int]
        quote_id: int
        settlement_amount: _common_pb2.Decimal
        fx_rate: _common_pb2.Decimal
        expires_at: _timestamp_pb2.Timestamp
        def __init__(self, quote_id: _Optional[int] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., fx_rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expires_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[GetPaymentQuoteResponse.Failure.Reason]
            REASON_QUOTE_UNAVAILABLE: _ClassVar[GetPaymentQuoteResponse.Failure.Reason]
        REASON_UNSPECIFIED: GetPaymentQuoteResponse.Failure.Reason
        REASON_QUOTE_UNAVAILABLE: GetPaymentQuoteResponse.Failure.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: GetPaymentQuoteResponse.Failure.Reason
        def __init__(self, reason: _Optional[_Union[GetPaymentQuoteResponse.Failure.Reason, str]] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    success: GetPaymentQuoteResponse.Success
    failure: GetPaymentQuoteResponse.Failure
    def __init__(self, success: _Optional[_Union[GetPaymentQuoteResponse.Success, _Mapping]] = ..., failure: _Optional[_Union[GetPaymentQuoteResponse.Failure, _Mapping]] = ...) -> None: ...

class CreatePaymentIntentRequest(_message.Message):
    __slots__ = ("payment_ref", "idempotency_key", "settlement", "local", "quote_id")
    class SettlementAmount(_message.Message):
        __slots__ = ("value",)
        VALUE_FIELD_NUMBER: _ClassVar[int]
        value: _common_pb2.Decimal
        def __init__(self, value: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...
    PAYMENT_REF_FIELD_NUMBER: _ClassVar[int]
    IDEMPOTENCY_KEY_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
    LOCAL_FIELD_NUMBER: _ClassVar[int]
    QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
    payment_ref: str
    idempotency_key: str
    settlement: CreatePaymentIntentRequest.SettlementAmount
    local: LocalAmount
    quote_id: int
    def __init__(self, payment_ref: _Optional[str] = ..., idempotency_key: _Optional[str] = ..., settlement: _Optional[_Union[CreatePaymentIntentRequest.SettlementAmount, _Mapping]] = ..., local: _Optional[_Union[LocalAmount, _Mapping]] = ..., quote_id: _Optional[int] = ...) -> None: ...

class CreatePaymentIntentResponse(_message.Message):
    __slots__ = ("success", "failure")
    class Success(_message.Message):
        __slots__ = ("payment_intent_id", "expires_at", "settlement_amount", "usdt_on_chain", "fiat", "onchain")
        class UsdtOnChainInstructions(_message.Message):
            __slots__ = ("deposit_options",)
            DEPOSIT_OPTIONS_FIELD_NUMBER: _ClassVar[int]
            deposit_options: _containers.RepeatedCompositeFieldContainer[_common_pb2.DepositOption]
            def __init__(self, deposit_options: _Optional[_Iterable[_Union[_common_pb2.DepositOption, _Mapping]]] = ...) -> None: ...
        class OnchainSettlement(_message.Message):
            __slots__ = ()
            def __init__(self) -> None: ...
        PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
        EXPIRES_AT_FIELD_NUMBER: _ClassVar[int]
        SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
        USDT_ON_CHAIN_FIELD_NUMBER: _ClassVar[int]
        FIAT_FIELD_NUMBER: _ClassVar[int]
        ONCHAIN_FIELD_NUMBER: _ClassVar[int]
        payment_intent_id: int
        expires_at: _timestamp_pb2.Timestamp
        settlement_amount: _common_pb2.Decimal
        usdt_on_chain: CreatePaymentIntentResponse.Success.UsdtOnChainInstructions
        fiat: FiatSettlement
        onchain: CreatePaymentIntentResponse.Success.OnchainSettlement
        def __init__(self, payment_intent_id: _Optional[int] = ..., expires_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., usdt_on_chain: _Optional[_Union[CreatePaymentIntentResponse.Success.UsdtOnChainInstructions, _Mapping]] = ..., fiat: _Optional[_Union[FiatSettlement, _Mapping]] = ..., onchain: _Optional[_Union[CreatePaymentIntentResponse.Success.OnchainSettlement, _Mapping]] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            REASON_ISSUER_UNAVAILABLE: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            REASON_ADDRESS_POOL_EMPTY: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            REASON_AMOUNT_OUT_OF_RANGE: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            REASON_QUOTE_EXPIRED: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            REASON_QUOTE_INSUFFICIENT_HEADROOM: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
            REASON_QUOTE_UNAVAILABLE: _ClassVar[CreatePaymentIntentResponse.Failure.Reason]
        REASON_UNSPECIFIED: CreatePaymentIntentResponse.Failure.Reason
        REASON_ISSUER_UNAVAILABLE: CreatePaymentIntentResponse.Failure.Reason
        REASON_ADDRESS_POOL_EMPTY: CreatePaymentIntentResponse.Failure.Reason
        REASON_AMOUNT_OUT_OF_RANGE: CreatePaymentIntentResponse.Failure.Reason
        REASON_QUOTE_EXPIRED: CreatePaymentIntentResponse.Failure.Reason
        REASON_QUOTE_INSUFFICIENT_HEADROOM: CreatePaymentIntentResponse.Failure.Reason
        REASON_QUOTE_UNAVAILABLE: CreatePaymentIntentResponse.Failure.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: CreatePaymentIntentResponse.Failure.Reason
        def __init__(self, reason: _Optional[_Union[CreatePaymentIntentResponse.Failure.Reason, str]] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    success: CreatePaymentIntentResponse.Success
    failure: CreatePaymentIntentResponse.Failure
    def __init__(self, success: _Optional[_Union[CreatePaymentIntentResponse.Success, _Mapping]] = ..., failure: _Optional[_Union[CreatePaymentIntentResponse.Failure, _Mapping]] = ...) -> None: ...

class SettlementReceivedRequest(_message.Message):
    __slots__ = ("lp_id", "bank_transfer_ref", "local_currency", "amount_received", "received_at")
    LP_ID_FIELD_NUMBER: _ClassVar[int]
    BANK_TRANSFER_REF_FIELD_NUMBER: _ClassVar[int]
    LOCAL_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_RECEIVED_FIELD_NUMBER: _ClassVar[int]
    RECEIVED_AT_FIELD_NUMBER: _ClassVar[int]
    lp_id: int
    bank_transfer_ref: str
    local_currency: str
    amount_received: _common_pb2.Decimal
    received_at: _timestamp_pb2.Timestamp
    def __init__(self, lp_id: _Optional[int] = ..., bank_transfer_ref: _Optional[str] = ..., local_currency: _Optional[str] = ..., amount_received: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., received_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class SettlementReceivedResponse(_message.Message):
    __slots__ = ("accepted", "rejected")
    class Accepted(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[SettlementReceivedResponse.Rejected.Reason]
            REASON_AMOUNT_MISMATCH: _ClassVar[SettlementReceivedResponse.Rejected.Reason]
            REASON_UNKNOWN_TRANSFER: _ClassVar[SettlementReceivedResponse.Rejected.Reason]
            REASON_CURRENCY_MISMATCH: _ClassVar[SettlementReceivedResponse.Rejected.Reason]
        REASON_UNSPECIFIED: SettlementReceivedResponse.Rejected.Reason
        REASON_AMOUNT_MISMATCH: SettlementReceivedResponse.Rejected.Reason
        REASON_UNKNOWN_TRANSFER: SettlementReceivedResponse.Rejected.Reason
        REASON_CURRENCY_MISMATCH: SettlementReceivedResponse.Rejected.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: SettlementReceivedResponse.Rejected.Reason
        def __init__(self, reason: _Optional[_Union[SettlementReceivedResponse.Rejected.Reason, str]] = ...) -> None: ...
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    accepted: SettlementReceivedResponse.Accepted
    rejected: SettlementReceivedResponse.Rejected
    def __init__(self, accepted: _Optional[_Union[SettlementReceivedResponse.Accepted, _Mapping]] = ..., rejected: _Optional[_Union[SettlementReceivedResponse.Rejected, _Mapping]] = ...) -> None: ...

class PaymentAuthorizedRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_ref", "usdt_on_chain", "approved_at", "settlement_amount", "received_at", "fiat_settlement")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_REF_FIELD_NUMBER: _ClassVar[int]
    USDT_ON_CHAIN_FIELD_NUMBER: _ClassVar[int]
    APPROVED_AT_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    RECEIVED_AT_FIELD_NUMBER: _ClassVar[int]
    FIAT_SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_ref: str
    usdt_on_chain: _common_pb2.UsdtOnChainPayment
    approved_at: _timestamp_pb2.Timestamp
    settlement_amount: _common_pb2.Decimal
    received_at: _timestamp_pb2.Timestamp
    fiat_settlement: FiatSettlement
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_ref: _Optional[str] = ..., usdt_on_chain: _Optional[_Union[_common_pb2.UsdtOnChainPayment, _Mapping]] = ..., approved_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., received_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., fiat_settlement: _Optional[_Union[FiatSettlement, _Mapping]] = ...) -> None: ...

class PaymentAuthorizedResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class SettlementInitiatedRequest(_message.Message):
    __slots__ = ("fiat_settlement_id", "lp_id", "bank_transfer_ref", "settled_payment_intent_ids", "local", "acquirer_id", "initiated_at", "settled_at")
    FIAT_SETTLEMENT_ID_FIELD_NUMBER: _ClassVar[int]
    LP_ID_FIELD_NUMBER: _ClassVar[int]
    BANK_TRANSFER_REF_FIELD_NUMBER: _ClassVar[int]
    SETTLED_PAYMENT_INTENT_IDS_FIELD_NUMBER: _ClassVar[int]
    LOCAL_FIELD_NUMBER: _ClassVar[int]
    ACQUIRER_ID_FIELD_NUMBER: _ClassVar[int]
    INITIATED_AT_FIELD_NUMBER: _ClassVar[int]
    SETTLED_AT_FIELD_NUMBER: _ClassVar[int]
    fiat_settlement_id: int
    lp_id: int
    bank_transfer_ref: str
    settled_payment_intent_ids: _containers.RepeatedScalarFieldContainer[int]
    local: LocalAmount
    acquirer_id: int
    initiated_at: _timestamp_pb2.Timestamp
    settled_at: _timestamp_pb2.Timestamp
    def __init__(self, fiat_settlement_id: _Optional[int] = ..., lp_id: _Optional[int] = ..., bank_transfer_ref: _Optional[str] = ..., settled_payment_intent_ids: _Optional[_Iterable[int]] = ..., local: _Optional[_Union[LocalAmount, _Mapping]] = ..., acquirer_id: _Optional[int] = ..., initiated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., settled_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class SettlementInitiatedResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class SettlementCompletedRequest(_message.Message):
    __slots__ = ("settlement_id", "settlement_amount", "settled_payment_intent_ids", "settled_at", "settlement", "acquirer_id")
    SETTLEMENT_ID_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    SETTLED_PAYMENT_INTENT_IDS_FIELD_NUMBER: _ClassVar[int]
    SETTLED_AT_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
    ACQUIRER_ID_FIELD_NUMBER: _ClassVar[int]
    settlement_id: int
    settlement_amount: _common_pb2.Decimal
    settled_payment_intent_ids: _containers.RepeatedScalarFieldContainer[int]
    settled_at: _timestamp_pb2.Timestamp
    settlement: _common_pb2.OnChainSettlementDetails
    acquirer_id: int
    def __init__(self, settlement_id: _Optional[int] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settled_payment_intent_ids: _Optional[_Iterable[int]] = ..., settled_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., settlement: _Optional[_Union[_common_pb2.OnChainSettlementDetails, _Mapping]] = ..., acquirer_id: _Optional[int] = ...) -> None: ...

class SettlementCompletedResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class PaymentExpiredRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_ref", "expired_at", "fiat_settlement")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_REF_FIELD_NUMBER: _ClassVar[int]
    EXPIRED_AT_FIELD_NUMBER: _ClassVar[int]
    FIAT_SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_ref: str
    expired_at: _timestamp_pb2.Timestamp
    fiat_settlement: FiatSettlement
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_ref: _Optional[str] = ..., expired_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., fiat_settlement: _Optional[_Union[FiatSettlement, _Mapping]] = ...) -> None: ...

class PaymentExpiredResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class PaymentFailedRequest(_message.Message):
    __slots__ = ("payment_intent_id", "payment_ref", "amount_usdt", "usdt_on_chain", "disposition", "failed_at", "fiat_settlement")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_REF_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_USDT_FIELD_NUMBER: _ClassVar[int]
    USDT_ON_CHAIN_FIELD_NUMBER: _ClassVar[int]
    DISPOSITION_FIELD_NUMBER: _ClassVar[int]
    FAILED_AT_FIELD_NUMBER: _ClassVar[int]
    FIAT_SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    payment_ref: str
    amount_usdt: _common_pb2.Decimal
    usdt_on_chain: _common_pb2.UsdtOnChainPayment
    disposition: _common_pb2.FundsDisposition
    failed_at: _timestamp_pb2.Timestamp
    fiat_settlement: FiatSettlement
    def __init__(self, payment_intent_id: _Optional[int] = ..., payment_ref: _Optional[str] = ..., amount_usdt: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., usdt_on_chain: _Optional[_Union[_common_pb2.UsdtOnChainPayment, _Mapping]] = ..., disposition: _Optional[_Union[_common_pb2.FundsDisposition, str]] = ..., failed_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., fiat_settlement: _Optional[_Union[FiatSettlement, _Mapping]] = ...) -> None: ...

class PaymentFailedResponse(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...
