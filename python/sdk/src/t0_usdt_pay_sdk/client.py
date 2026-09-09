"""Client factory for outbound calls to t-0."""

from __future__ import annotations

from typing import TypeVar

from t0_provider_sdk.crypto.signer import SignFn, new_signer_from_hex
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient

T = TypeVar("T")


def create_client(
    endpoint: str,
    signer: str | SignFn,
    client_class: type[T],
    *,
    timeout: float = 15.0,
) -> T:
    """Create an async ConnectRPC client for a t-0 pay service.

    ``endpoint`` is required and must be non-empty — the underlying provider
    client defaults to ``api.t-0.network``, which is a different API.

    ``signer`` is either a hex private key or a ``SignFn`` for HSM/KMS use.
    """
    if not endpoint or not endpoint.strip():
        raise ValueError("endpoint is required — omitting it would send requests to the wrong API")
    sign_fn = signer if callable(signer) else new_signer_from_hex(signer)
    http_client = SigningClient(sign_fn)
    return client_class(endpoint, http_client=http_client, timeout_ms=int(timeout * 1000))  # type: ignore[call-arg]


def create_client_sync(
    endpoint: str,
    signer: str | SignFn,
    client_class: type[T],
    *,
    timeout: float = 15.0,
) -> T:
    """Create a sync ConnectRPC client for a t-0 pay service.

    See :func:`create_client` for parameter details.
    """
    if not endpoint or not endpoint.strip():
        raise ValueError("endpoint is required — omitting it would send requests to the wrong API")
    sign_fn = signer if callable(signer) else new_signer_from_hex(signer)
    http_client = SigningSyncClient(sign_fn)
    return client_class(endpoint, http_client=http_client, timeout_ms=int(timeout * 1000))  # type: ignore[call-arg]
