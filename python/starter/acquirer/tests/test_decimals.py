import pytest
from acquirer.internal.decimals import decimal_from_string, decimal_to_string
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2


def test_simple_integer():
    d = decimal_from_string("100")
    assert d.unscaled == 100
    assert d.exponent == 0
    assert decimal_to_string(d) == "100"


def test_two_decimal_places():
    d = decimal_from_string("123.45")
    assert d.unscaled == 12345
    assert d.exponent == -2
    assert decimal_to_string(d) == "123.45"


def test_leading_zero():
    d = decimal_from_string("0.5")
    assert d.unscaled == 5
    assert d.exponent == -1
    assert decimal_to_string(d) == "0.5"


def test_usdt_two_dp():
    d = decimal_from_string("99.99")
    assert d.unscaled == 9999
    assert d.exponent == -2
    assert decimal_to_string(d) == "99.99"


def test_whole_unit_currencies():
    """COP, CLP, PYG, JPY are kept in whole units."""
    d = decimal_from_string("100000")
    assert d.unscaled == 100000
    assert d.exponent == 0
    assert decimal_to_string(d) == "100000"


def test_negative():
    d = decimal_from_string("-0.5")
    assert d.unscaled == -5
    assert d.exponent == -1
    assert decimal_to_string(d) == "-0.5"


def test_roundtrip_large():
    d = decimal_from_string("1000000.00")
    assert decimal_to_string(d) == "1000000.00"


def test_positive_exponent_roundtrip():
    d = common_pb2.Decimal(unscaled=10, exponent=8)
    assert decimal_to_string(d) == "1000000000"


def test_rejects_scientific_notation():
    with pytest.raises(ValueError, match="not a plain decimal"):
        decimal_from_string("1e9")


def test_rejects_too_many_decimals():
    with pytest.raises(ValueError, match="exponent"):
        decimal_from_string("1.123456789")


def test_zero():
    d = decimal_from_string("0")
    assert d.unscaled == 0
    assert d.exponent == 0
    assert decimal_to_string(d) == "0"
