"""Key utilities for the pay contract."""

from t0_provider_sdk.crypto.keys import private_key_from_hex


def public_key_from_private_key(private_key_hex: str) -> str:
    """Derive the uncompressed public key from a hex private key.

    Returns ``0x04…`` (65 bytes as hex).
    """
    pk = private_key_from_hex(private_key_hex)
    return "0x" + pk.public_key.format(compressed=False).hex()
