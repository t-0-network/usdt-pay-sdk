"""Key utilities for the pay contract."""

from t0_provider_sdk.crypto.keys import private_key_from_hex, public_key_to_bytes


def public_key_from_private_key(private_key_hex: str) -> str:
    """Derive the uncompressed public key from a hex private key.

    Returns ``0x04…`` (65 bytes as hex).
    """
    return "0x" + public_key_to_bytes(private_key_from_hex(private_key_hex).public_key).hex()
