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

class PublishQuoteRequest(_message.Message):
    __slots__ = ("quotes",)
    class Quote(_message.Message):
        __slots__ = ("quote_ref", "local_currency", "fx_rate", "expires_at")
        QUOTE_REF_FIELD_NUMBER: _ClassVar[int]
        LOCAL_CURRENCY_FIELD_NUMBER: _ClassVar[int]
        FX_RATE_FIELD_NUMBER: _ClassVar[int]
        EXPIRES_AT_FIELD_NUMBER: _ClassVar[int]
        quote_ref: str
        local_currency: str
        fx_rate: _common_pb2.Decimal
        expires_at: _timestamp_pb2.Timestamp
        def __init__(self, quote_ref: _Optional[str] = ..., local_currency: _Optional[str] = ..., fx_rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expires_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
    QUOTES_FIELD_NUMBER: _ClassVar[int]
    quotes: _containers.RepeatedCompositeFieldContainer[PublishQuoteRequest.Quote]
    def __init__(self, quotes: _Optional[_Iterable[_Union[PublishQuoteRequest.Quote, _Mapping]]] = ...) -> None: ...

class PublishQuoteResponse(_message.Message):
    __slots__ = ("success", "failure")
    class Success(_message.Message):
        __slots__ = ("quotes",)
        class PublishedQuote(_message.Message):
            __slots__ = ("quote_ref", "quote_id")
            QUOTE_REF_FIELD_NUMBER: _ClassVar[int]
            QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
            quote_ref: str
            quote_id: int
            def __init__(self, quote_ref: _Optional[str] = ..., quote_id: _Optional[int] = ...) -> None: ...
        QUOTES_FIELD_NUMBER: _ClassVar[int]
        quotes: _containers.RepeatedCompositeFieldContainer[PublishQuoteResponse.Success.PublishedQuote]
        def __init__(self, quotes: _Optional[_Iterable[_Union[PublishQuoteResponse.Success.PublishedQuote, _Mapping]]] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[PublishQuoteResponse.Failure.Reason]
            REASON_VALIDITY_INVALID: _ClassVar[PublishQuoteResponse.Failure.Reason]
        REASON_UNSPECIFIED: PublishQuoteResponse.Failure.Reason
        REASON_VALIDITY_INVALID: PublishQuoteResponse.Failure.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: PublishQuoteResponse.Failure.Reason
        def __init__(self, reason: _Optional[_Union[PublishQuoteResponse.Failure.Reason, str]] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    success: PublishQuoteResponse.Success
    failure: PublishQuoteResponse.Failure
    def __init__(self, success: _Optional[_Union[PublishQuoteResponse.Success, _Mapping]] = ..., failure: _Optional[_Union[PublishQuoteResponse.Failure, _Mapping]] = ...) -> None: ...

class FiatSettlementSentRequest(_message.Message):
    __slots__ = ("bank_transfer_ref", "settled_execution_ids", "local_currency", "settlement_amount", "settled_at")
    BANK_TRANSFER_REF_FIELD_NUMBER: _ClassVar[int]
    SETTLED_EXECUTION_IDS_FIELD_NUMBER: _ClassVar[int]
    LOCAL_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    SETTLED_AT_FIELD_NUMBER: _ClassVar[int]
    bank_transfer_ref: str
    settled_execution_ids: _containers.RepeatedScalarFieldContainer[int]
    local_currency: str
    settlement_amount: _common_pb2.Decimal
    settled_at: _timestamp_pb2.Timestamp
    def __init__(self, bank_transfer_ref: _Optional[str] = ..., settled_execution_ids: _Optional[_Iterable[int]] = ..., local_currency: _Optional[str] = ..., settlement_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settled_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class FiatSettlementSentResponse(_message.Message):
    __slots__ = ("accepted", "rejected")
    class Accepted(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[FiatSettlementSentResponse.Rejected.Reason]
            REASON_EXECUTION_UNKNOWN: _ClassVar[FiatSettlementSentResponse.Rejected.Reason]
            REASON_EXECUTION_ALREADY_COVERED: _ClassVar[FiatSettlementSentResponse.Rejected.Reason]
            REASON_CURRENCY_MISMATCH: _ClassVar[FiatSettlementSentResponse.Rejected.Reason]
            REASON_AMOUNT_MISMATCH: _ClassVar[FiatSettlementSentResponse.Rejected.Reason]
            REASON_ACQUIRER_MIXED: _ClassVar[FiatSettlementSentResponse.Rejected.Reason]
            REASON_BANK_TRANSFER_REF_CONFLICT: _ClassVar[FiatSettlementSentResponse.Rejected.Reason]
        REASON_UNSPECIFIED: FiatSettlementSentResponse.Rejected.Reason
        REASON_EXECUTION_UNKNOWN: FiatSettlementSentResponse.Rejected.Reason
        REASON_EXECUTION_ALREADY_COVERED: FiatSettlementSentResponse.Rejected.Reason
        REASON_CURRENCY_MISMATCH: FiatSettlementSentResponse.Rejected.Reason
        REASON_AMOUNT_MISMATCH: FiatSettlementSentResponse.Rejected.Reason
        REASON_ACQUIRER_MIXED: FiatSettlementSentResponse.Rejected.Reason
        REASON_BANK_TRANSFER_REF_CONFLICT: FiatSettlementSentResponse.Rejected.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: FiatSettlementSentResponse.Rejected.Reason
        def __init__(self, reason: _Optional[_Union[FiatSettlementSentResponse.Rejected.Reason, str]] = ...) -> None: ...
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    accepted: FiatSettlementSentResponse.Accepted
    rejected: FiatSettlementSentResponse.Rejected
    def __init__(self, accepted: _Optional[_Union[FiatSettlementSentResponse.Accepted, _Mapping]] = ..., rejected: _Optional[_Union[FiatSettlementSentResponse.Rejected, _Mapping]] = ...) -> None: ...

class ExecuteQuoteRequest(_message.Message):
    __slots__ = ("execution_id", "quote_id", "quote_ref", "acquirer_id", "local_amount", "amount_usdt", "executed_at", "local_currency", "fx_rate")
    EXECUTION_ID_FIELD_NUMBER: _ClassVar[int]
    QUOTE_ID_FIELD_NUMBER: _ClassVar[int]
    QUOTE_REF_FIELD_NUMBER: _ClassVar[int]
    ACQUIRER_ID_FIELD_NUMBER: _ClassVar[int]
    LOCAL_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_USDT_FIELD_NUMBER: _ClassVar[int]
    EXECUTED_AT_FIELD_NUMBER: _ClassVar[int]
    LOCAL_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    FX_RATE_FIELD_NUMBER: _ClassVar[int]
    execution_id: int
    quote_id: int
    quote_ref: str
    acquirer_id: int
    local_amount: _common_pb2.Decimal
    amount_usdt: _common_pb2.Decimal
    executed_at: _timestamp_pb2.Timestamp
    local_currency: str
    fx_rate: _common_pb2.Decimal
    def __init__(self, execution_id: _Optional[int] = ..., quote_id: _Optional[int] = ..., quote_ref: _Optional[str] = ..., acquirer_id: _Optional[int] = ..., local_amount: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., amount_usdt: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., executed_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., local_currency: _Optional[str] = ..., fx_rate: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ...) -> None: ...

class ExecuteQuoteResponse(_message.Message):
    __slots__ = ("accepted", "rejected")
    class Accepted(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ("reason", "details")
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[ExecuteQuoteResponse.Rejected.Reason]
            REASON_OTHER: _ClassVar[ExecuteQuoteResponse.Rejected.Reason]
        REASON_UNSPECIFIED: ExecuteQuoteResponse.Rejected.Reason
        REASON_OTHER: ExecuteQuoteResponse.Rejected.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        DETAILS_FIELD_NUMBER: _ClassVar[int]
        reason: ExecuteQuoteResponse.Rejected.Reason
        details: str
        def __init__(self, reason: _Optional[_Union[ExecuteQuoteResponse.Rejected.Reason, str]] = ..., details: _Optional[str] = ...) -> None: ...
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    accepted: ExecuteQuoteResponse.Accepted
    rejected: ExecuteQuoteResponse.Rejected
    def __init__(self, accepted: _Optional[_Union[ExecuteQuoteResponse.Accepted, _Mapping]] = ..., rejected: _Optional[_Union[ExecuteQuoteResponse.Rejected, _Mapping]] = ...) -> None: ...
