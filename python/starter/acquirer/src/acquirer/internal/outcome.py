"""What a call to t-0 did, from the caller's point of view."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, TypeVar

from connectrpc.code import Code
from connectrpc.errors import ConnectError

T = TypeVar("T")


@dataclass(frozen=True, slots=True)
class Accepted(Generic[T]):
    """t-0 accepted it. The operation is done; record it and move on."""

    value: T
    should_retry: bool = False


@dataclass(frozen=True, slots=True)
class Rejected:
    """t-0 refused this payload and will keep refusing it.

    The key is *not* consumed: correct the fields named by ``reason`` and
    resend under the same key.
    """

    reason: str
    should_retry: bool = False


@dataclass(frozen=True, slots=True)
class Unknown:
    """No answer came back.

    t-0 may or may not have committed the call. Retry with the same key and
    identical content until you get an ``Accepted`` or a ``Rejected``.
    """

    detail: str
    should_retry: bool = True


type Outcome[S] = Accepted[S] | Rejected | Unknown


def outcome_from_error(error: Exception) -> Rejected | Unknown:
    """Classify a thrown call.

    Transport failures are ``Unknown`` -- the call may still have committed.
    Five codes mean t-0 read the request and refused it permanently.
    """
    if not isinstance(error, ConnectError):
        raise error

    permanent = error.code in (
        Code.INVALID_ARGUMENT,
        Code.UNAUTHENTICATED,
        Code.PERMISSION_DENIED,
        Code.UNIMPLEMENTED,
        Code.FAILED_PRECONDITION,
    )
    if permanent:
        return Rejected(reason=f"{error.code.name}: {error.message}")
    return Unknown(detail=str(error))
