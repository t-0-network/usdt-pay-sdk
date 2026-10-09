"""Client factory for outbound calls to t-0."""

from __future__ import annotations

from typing import TypeVar

from t0_provider_sdk.crypto.signer import SignFn
from t0_provider_sdk.network import new_service_client, new_service_client_sync

T = TypeVar("T")


def create_client(signer: str | SignFn, client_class: type[T], *, base_url: str) -> T:
    """Create an async ConnectRPC client for a t-0 pay service.

    ``signer`` is either a hex private key or a ``SignFn`` for HSM/KMS use.

    ``base_url`` is the pay API's base URL; always pass it. ``None`` gives the provider
    client's default, ``api.t-0.network``, which is a different API.
    """
    return new_service_client(signer, client_class, base_url=base_url)


def create_client_sync(signer: str | SignFn, client_class: type[T], *, base_url: str) -> T:
    """Create a sync ConnectRPC client for a t-0 pay service.

    See :func:`create_client` for parameter details.
    """
    return new_service_client_sync(signer, client_class, base_url=base_url)
