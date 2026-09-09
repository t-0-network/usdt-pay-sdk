from acquirer.internal.outcome import Accepted, Rejected, Unknown, outcome_from_error
from connectrpc.code import Code
from connectrpc.errors import ConnectError


def test_accepted():
    a = Accepted(value=42)
    assert a.value == 42
    assert a.should_retry is False


def test_rejected():
    r = Rejected(reason="bad input")
    assert r.reason == "bad input"
    assert r.should_retry is False


def test_unknown():
    u = Unknown(detail="timeout")
    assert u.detail == "timeout"
    assert u.should_retry is True


def test_outcome_from_error_invalid_argument():
    err = ConnectError(Code.INVALID_ARGUMENT, "bad field")
    result = outcome_from_error(err)
    assert isinstance(result, Rejected)
    assert "INVALID_ARGUMENT" in result.reason


def test_outcome_from_error_unauthenticated():
    err = ConnectError(Code.UNAUTHENTICATED, "bad key")
    result = outcome_from_error(err)
    assert isinstance(result, Rejected)
    assert "UNAUTHENTICATED" in result.reason


def test_outcome_from_error_permission_denied():
    err = ConnectError(Code.PERMISSION_DENIED, "nope")
    result = outcome_from_error(err)
    assert isinstance(result, Rejected)
    assert "PERMISSION_DENIED" in result.reason


def test_outcome_from_error_unimplemented():
    err = ConnectError(Code.UNIMPLEMENTED, "not available")
    result = outcome_from_error(err)
    assert isinstance(result, Rejected)


def test_outcome_from_error_failed_precondition():
    err = ConnectError(Code.FAILED_PRECONDITION, "local in on-chain mode")
    result = outcome_from_error(err)
    assert isinstance(result, Rejected)
    assert "FAILED_PRECONDITION" in result.reason


def test_outcome_from_error_transient():
    err = ConnectError(Code.UNAVAILABLE, "service down")
    result = outcome_from_error(err)
    assert isinstance(result, Unknown)
    assert result.should_retry is True


def test_outcome_from_error_internal():
    err = ConnectError(Code.INTERNAL, "oops")
    result = outcome_from_error(err)
    assert isinstance(result, Unknown)


def test_outcome_from_error_non_connect_reraises():
    import pytest

    with pytest.raises(ValueError, match="not a connect error"):
        outcome_from_error(ValueError("not a connect error"))
