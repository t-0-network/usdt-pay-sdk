"""Fixed-point Decimal helpers -- integer arithmetic only.

Money never travels as a ``float``, and it should not become one on your side
either. ``unscaled`` is a 64-bit integer; a USDt amount routed through a float
loses cents at amounts a POS actually rings up.
"""

from __future__ import annotations

import re

from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2

MIN_EXPONENT = -8
MAX_EXPONENT = 8

INT64_MIN = -(2**63)
INT64_MAX = 2**63 - 1

_PLAIN_DECIMAL = re.compile(r"^-?\d+(\.\d+)?$")


def decimal_from_string(value: str) -> common_pb2.Decimal:
    """Parse a plain decimal string into a proto ``Decimal``.

    Raises ``ValueError`` if the value carries more precision or more magnitude
    than the contract can hold.
    """
    if not _PLAIN_DECIMAL.fullmatch(value):
        raise ValueError(f"'{value}' is not a plain decimal number")

    parts = value.split(".")
    whole = parts[0]
    fraction = parts[1] if len(parts) > 1 else ""
    exponent = -len(fraction)

    if exponent < MIN_EXPONENT:
        raise ValueError(
            f"{value} needs exponent {exponent}, outside the contract's "
            f"[{MIN_EXPONENT}, {MAX_EXPONENT}] -- round it first"
        )

    unscaled = int(whole + fraction)
    if unscaled < INT64_MIN or unscaled > INT64_MAX:
        raise ValueError(f"{value} does not fit the contract's 64-bit unscaled value")

    return common_pb2.Decimal(unscaled=unscaled, exponent=exponent)


def decimal_to_string(value: common_pb2.Decimal) -> str:
    """Exact decimal string from a proto ``Decimal``."""
    negative = value.unscaled < 0
    sign = "-" if negative else ""
    digits = str(abs(value.unscaled))

    if value.exponent >= 0:
        return sign + digits + "0" * value.exponent

    # Pad so there is at least one digit left of the point.
    short_by = 1 - (len(digits) + value.exponent)
    if short_by > 0:
        digits = "0" * short_by + digits

    point = len(digits) + value.exponent
    return sign + digits[:point] + "." + digits[point:]
