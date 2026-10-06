"""Client factory for outbound calls to t-0."""

from __future__ import annotations

from typing import TypeVar

from t0_provider_sdk.crypto.signer import SignFn
from t0_provider_sdk.network import new_service_client, new_service_client_sync

T = TypeVar("T")


def create_client(endpoint: str, signer: str | SignFn, client_class: type[T]) -> T:
    """Create an async ConnectRPC client for a t-0 pay service.

    ``endpoint`` is required — the underlying provider client defaults to
    ``api.t-0.network``, which is a different API.

    ``signer`` is either a hex private key or a ``SignFn`` for HSM/KMS use.
    """
    # None would make the provider client fall back to its default API; "" makes it refuse.
    base_url = "" if endpoint is None else endpoint
    if callable(signer):
        return new_service_client("", client_class, base_url=base_url, sign_fn=signer)
    return new_service_client(signer, client_class, base_url=base_url)


def create_client_sync(endpoint: str, signer: str | SignFn, client_class: type[T]) -> T:
    """Create a sync ConnectRPC client for a t-0 pay service.

    See :func:`create_client` for parameter details.
    """
    # None would make the provider client fall back to its default API; "" makes it refuse.
    base_url = "" if endpoint is None else endpoint
    if callable(signer):
        return new_service_client_sync("", client_class, base_url=base_url, sign_fn=signer)
    return new_service_client_sync(signer, client_class, base_url=base_url)
