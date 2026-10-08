"""Key utilities for the pay contract.

``public_key_from_private_key`` derives the uncompressed public key (``0x04…``, 65 bytes as
hex) from a hex private key. It is provider-sdk's.
"""

from t0_provider_sdk.crypto import public_key_from_private_key

__all__ = ["public_key_from_private_key"]
