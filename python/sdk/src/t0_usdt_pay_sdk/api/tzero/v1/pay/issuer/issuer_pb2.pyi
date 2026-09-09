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

class PaymentReceivedRequest(_message.Message):
    __slots__ = ("payment_intent_id", "amount_usdt", "usdt_on_chain", "received_at", "authorized", "unprocessable")
    class Authorized(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Unprocessable(_message.Message):
        __slots__ = ("disposition",)
        DISPOSITION_FIELD_NUMBER: _ClassVar[int]
        disposition: _common_pb2.FundsDisposition
        def __init__(self, disposition: _Optional[_Union[_common_pb2.FundsDisposition, str]] = ...) -> None: ...
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_USDT_FIELD_NUMBER: _ClassVar[int]
    USDT_ON_CHAIN_FIELD_NUMBER: _ClassVar[int]
    RECEIVED_AT_FIELD_NUMBER: _ClassVar[int]
    AUTHORIZED_FIELD_NUMBER: _ClassVar[int]
    UNPROCESSABLE_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    amount_usdt: _common_pb2.Decimal
    usdt_on_chain: _common_pb2.UsdtOnChainPayment
    received_at: _timestamp_pb2.Timestamp
    authorized: PaymentReceivedRequest.Authorized
    unprocessable: PaymentReceivedRequest.Unprocessable
    def __init__(self, payment_intent_id: _Optional[int] = ..., amount_usdt: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., usdt_on_chain: _Optional[_Union[_common_pb2.UsdtOnChainPayment, _Mapping]] = ..., received_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., authorized: _Optional[_Union[PaymentReceivedRequest.Authorized, _Mapping]] = ..., unprocessable: _Optional[_Union[PaymentReceivedRequest.Unprocessable, _Mapping]] = ...) -> None: ...

class PaymentReceivedResponse(_message.Message):
    __slots__ = ("accepted", "rejected")
    class Accepted(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[PaymentReceivedResponse.Rejected.Reason]
            REASON_INTENT_EXPIRED: _ClassVar[PaymentReceivedResponse.Rejected.Reason]
            REASON_UNKNOWN_INTENT: _ClassVar[PaymentReceivedResponse.Rejected.Reason]
            REASON_AMOUNT_MISMATCH: _ClassVar[PaymentReceivedResponse.Rejected.Reason]
            REASON_TRANSFER_RECORDED_FOR_ANOTHER_INTENT: _ClassVar[PaymentReceivedResponse.Rejected.Reason]
        REASON_UNSPECIFIED: PaymentReceivedResponse.Rejected.Reason
        REASON_INTENT_EXPIRED: PaymentReceivedResponse.Rejected.Reason
        REASON_UNKNOWN_INTENT: PaymentReceivedResponse.Rejected.Reason
        REASON_AMOUNT_MISMATCH: PaymentReceivedResponse.Rejected.Reason
        REASON_TRANSFER_RECORDED_FOR_ANOTHER_INTENT: PaymentReceivedResponse.Rejected.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: PaymentReceivedResponse.Rejected.Reason
        def __init__(self, reason: _Optional[_Union[PaymentReceivedResponse.Rejected.Reason, str]] = ...) -> None: ...
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    accepted: PaymentReceivedResponse.Accepted
    rejected: PaymentReceivedResponse.Rejected
    def __init__(self, accepted: _Optional[_Union[PaymentReceivedResponse.Accepted, _Mapping]] = ..., rejected: _Optional[_Union[PaymentReceivedResponse.Rejected, _Mapping]] = ...) -> None: ...

class SettlementSentRequest(_message.Message):
    __slots__ = ("settlement_ref", "amount_usdt", "settlement", "settled_payment_intent_ids", "settled_at")
    SETTLEMENT_REF_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_USDT_FIELD_NUMBER: _ClassVar[int]
    SETTLEMENT_FIELD_NUMBER: _ClassVar[int]
    SETTLED_PAYMENT_INTENT_IDS_FIELD_NUMBER: _ClassVar[int]
    SETTLED_AT_FIELD_NUMBER: _ClassVar[int]
    settlement_ref: str
    amount_usdt: _common_pb2.Decimal
    settlement: _common_pb2.OnChainSettlementDetails
    settled_payment_intent_ids: _containers.RepeatedScalarFieldContainer[int]
    settled_at: _timestamp_pb2.Timestamp
    def __init__(self, settlement_ref: _Optional[str] = ..., amount_usdt: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., settlement: _Optional[_Union[_common_pb2.OnChainSettlementDetails, _Mapping]] = ..., settled_payment_intent_ids: _Optional[_Iterable[int]] = ..., settled_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class SettlementSentResponse(_message.Message):
    __slots__ = ("accepted", "rejected")
    class Accepted(_message.Message):
        __slots__ = ()
        def __init__(self) -> None: ...
    class Rejected(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[SettlementSentResponse.Rejected.Reason]
            REASON_ON_CHAIN_UNCONFIRMED: _ClassVar[SettlementSentResponse.Rejected.Reason]
            REASON_AMOUNT_MISMATCH: _ClassVar[SettlementSentResponse.Rejected.Reason]
            REASON_WRONG_DESTINATION: _ClassVar[SettlementSentResponse.Rejected.Reason]
            REASON_INTENT_NOT_SETTLEABLE: _ClassVar[SettlementSentResponse.Rejected.Reason]
            REASON_SETTLEMENT_REF_CONFLICT: _ClassVar[SettlementSentResponse.Rejected.Reason]
        REASON_UNSPECIFIED: SettlementSentResponse.Rejected.Reason
        REASON_ON_CHAIN_UNCONFIRMED: SettlementSentResponse.Rejected.Reason
        REASON_AMOUNT_MISMATCH: SettlementSentResponse.Rejected.Reason
        REASON_WRONG_DESTINATION: SettlementSentResponse.Rejected.Reason
        REASON_INTENT_NOT_SETTLEABLE: SettlementSentResponse.Rejected.Reason
        REASON_SETTLEMENT_REF_CONFLICT: SettlementSentResponse.Rejected.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: SettlementSentResponse.Rejected.Reason
        def __init__(self, reason: _Optional[_Union[SettlementSentResponse.Rejected.Reason, str]] = ...) -> None: ...
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    accepted: SettlementSentResponse.Accepted
    rejected: SettlementSentResponse.Rejected
    def __init__(self, accepted: _Optional[_Union[SettlementSentResponse.Accepted, _Mapping]] = ..., rejected: _Optional[_Union[SettlementSentResponse.Rejected, _Mapping]] = ...) -> None: ...

class CreatePaymentInstructionsRequest(_message.Message):
    __slots__ = ("payment_intent_id", "acquirer_id", "amount_usdt", "expires_at")
    PAYMENT_INTENT_ID_FIELD_NUMBER: _ClassVar[int]
    ACQUIRER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_USDT_FIELD_NUMBER: _ClassVar[int]
    EXPIRES_AT_FIELD_NUMBER: _ClassVar[int]
    payment_intent_id: int
    acquirer_id: int
    amount_usdt: _common_pb2.Decimal
    expires_at: _timestamp_pb2.Timestamp
    def __init__(self, payment_intent_id: _Optional[int] = ..., acquirer_id: _Optional[int] = ..., amount_usdt: _Optional[_Union[_common_pb2.Decimal, _Mapping]] = ..., expires_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class CreatePaymentInstructionsResponse(_message.Message):
    __slots__ = ("success", "failure")
    class Success(_message.Message):
        __slots__ = ("deposit_options", "expires_at")
        DEPOSIT_OPTIONS_FIELD_NUMBER: _ClassVar[int]
        EXPIRES_AT_FIELD_NUMBER: _ClassVar[int]
        deposit_options: _containers.RepeatedCompositeFieldContainer[_common_pb2.DepositOption]
        expires_at: _timestamp_pb2.Timestamp
        def __init__(self, deposit_options: _Optional[_Iterable[_Union[_common_pb2.DepositOption, _Mapping]]] = ..., expires_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
    class Failure(_message.Message):
        __slots__ = ("reason",)
        class Reason(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
            __slots__ = ()
            REASON_UNSPECIFIED: _ClassVar[CreatePaymentInstructionsResponse.Failure.Reason]
            REASON_ISSUER_UNAVAILABLE: _ClassVar[CreatePaymentInstructionsResponse.Failure.Reason]
            REASON_ADDRESS_POOL_EMPTY: _ClassVar[CreatePaymentInstructionsResponse.Failure.Reason]
            REASON_AMOUNT_OUT_OF_RANGE: _ClassVar[CreatePaymentInstructionsResponse.Failure.Reason]
        REASON_UNSPECIFIED: CreatePaymentInstructionsResponse.Failure.Reason
        REASON_ISSUER_UNAVAILABLE: CreatePaymentInstructionsResponse.Failure.Reason
        REASON_ADDRESS_POOL_EMPTY: CreatePaymentInstructionsResponse.Failure.Reason
        REASON_AMOUNT_OUT_OF_RANGE: CreatePaymentInstructionsResponse.Failure.Reason
        REASON_FIELD_NUMBER: _ClassVar[int]
        reason: CreatePaymentInstructionsResponse.Failure.Reason
        def __init__(self, reason: _Optional[_Union[CreatePaymentInstructionsResponse.Failure.Reason, str]] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    FAILURE_FIELD_NUMBER: _ClassVar[int]
    success: CreatePaymentInstructionsResponse.Success
    failure: CreatePaymentInstructionsResponse.Failure
    def __init__(self, success: _Optional[_Union[CreatePaymentInstructionsResponse.Success, _Mapping]] = ..., failure: _Optional[_Union[CreatePaymentInstructionsResponse.Failure, _Mapping]] = ...) -> None: ...
