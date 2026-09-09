from buf.validate import validate_pb2 as _validate_pb2
from tzero.v1.pay import validate_pb2 as _validate_pb2_1
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class Blockchain(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    BLOCKCHAIN_UNSPECIFIED: _ClassVar[Blockchain]
    BLOCKCHAIN_TRON: _ClassVar[Blockchain]
    BLOCKCHAIN_ETH: _ClassVar[Blockchain]
    BLOCKCHAIN_BSC: _ClassVar[Blockchain]

class FundsDisposition(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    FUNDS_DISPOSITION_UNSPECIFIED: _ClassVar[FundsDisposition]
    FUNDS_DISPOSITION_RETURNED_TO_SENDER: _ClassVar[FundsDisposition]
    FUNDS_DISPOSITION_RETAINED_BY_ISSUER: _ClassVar[FundsDisposition]
BLOCKCHAIN_UNSPECIFIED: Blockchain
BLOCKCHAIN_TRON: Blockchain
BLOCKCHAIN_ETH: Blockchain
BLOCKCHAIN_BSC: Blockchain
FUNDS_DISPOSITION_UNSPECIFIED: FundsDisposition
FUNDS_DISPOSITION_RETURNED_TO_SENDER: FundsDisposition
FUNDS_DISPOSITION_RETAINED_BY_ISSUER: FundsDisposition

class Decimal(_message.Message):
    __slots__ = ("unscaled", "exponent")
    UNSCALED_FIELD_NUMBER: _ClassVar[int]
    EXPONENT_FIELD_NUMBER: _ClassVar[int]
    unscaled: int
    exponent: int
    def __init__(self, unscaled: _Optional[int] = ..., exponent: _Optional[int] = ...) -> None: ...

class UsdtOnChainPayment(_message.Message):
    __slots__ = ("chain", "on_chain_tx_hash", "sender_address")
    CHAIN_FIELD_NUMBER: _ClassVar[int]
    ON_CHAIN_TX_HASH_FIELD_NUMBER: _ClassVar[int]
    SENDER_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    chain: Blockchain
    on_chain_tx_hash: str
    sender_address: str
    def __init__(self, chain: _Optional[_Union[Blockchain, str]] = ..., on_chain_tx_hash: _Optional[str] = ..., sender_address: _Optional[str] = ...) -> None: ...

class DepositOption(_message.Message):
    __slots__ = ("chain", "deposit_address", "payment_uri", "token_contract")
    CHAIN_FIELD_NUMBER: _ClassVar[int]
    DEPOSIT_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_URI_FIELD_NUMBER: _ClassVar[int]
    TOKEN_CONTRACT_FIELD_NUMBER: _ClassVar[int]
    chain: Blockchain
    deposit_address: str
    payment_uri: str
    token_contract: str
    def __init__(self, chain: _Optional[_Union[Blockchain, str]] = ..., deposit_address: _Optional[str] = ..., payment_uri: _Optional[str] = ..., token_contract: _Optional[str] = ...) -> None: ...

class OnChainSettlementDetails(_message.Message):
    __slots__ = ("on_chain_tx_hash", "chain", "destination_address")
    ON_CHAIN_TX_HASH_FIELD_NUMBER: _ClassVar[int]
    CHAIN_FIELD_NUMBER: _ClassVar[int]
    DESTINATION_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    on_chain_tx_hash: str
    chain: Blockchain
    destination_address: str
    def __init__(self, on_chain_tx_hash: _Optional[str] = ..., chain: _Optional[_Union[Blockchain, str]] = ..., destination_address: _Optional[str] = ...) -> None: ...
