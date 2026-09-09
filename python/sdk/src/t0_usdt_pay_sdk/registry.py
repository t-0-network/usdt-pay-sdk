"""Pay contract registry: imports every pay proto module so the descriptors
are in the pool for ``protovalidate`` and for integrators building messages by
name. A new proto file in a sync means a new import here.
"""

from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2 as _common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay import validate_pb2 as _validate_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2 as _acquirer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.issuer import issuer_pb2 as _issuer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.lp import lp_pb2 as _lp_pb2

__all__: list[str] = []

_MODULES = (_common_pb2, _validate_pb2, _acquirer_pb2, _issuer_pb2, _lp_pb2)
