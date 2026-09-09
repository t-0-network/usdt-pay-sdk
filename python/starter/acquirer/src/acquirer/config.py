"""Configuration from .env / environment variables."""

from __future__ import annotations

import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv
from t0_usdt_pay_sdk import public_key_from_private_key

_NETWORK_PUBLIC_KEY_PATTERN = re.compile(r"^(0x)?[0-9a-fA-F]{130}$")


class ConfigurationError(Exception):
    def __init__(self, message: str, help_text: str) -> None:
        super().__init__(message)
        self.help_text = help_text


@dataclass(frozen=True)
class Config:
    private_key: str
    network_public_key: str
    tzero_endpoint: str
    port: int
    public_key: str


def load_config() -> Config:
    env_path = Path(".env").resolve()
    if env_path.exists():
        load_dotenv(env_path)
    else:
        print(f"No .env at {env_path} -- taking configuration from the environment instead", file=sys.stderr)

    private_key = os.environ.get("PROVIDER_PRIVATE_KEY", "").strip()
    network_public_key = os.environ.get("NETWORK_PUBLIC_KEY", "").strip()
    tzero_endpoint = os.environ.get("TZERO_ENDPOINT", "https://usdt-pay-api-sandbox.t-0.network")
    port_raw = os.environ.get("PORT", "8080")

    if not private_key:
        raise ConfigurationError(
            "PROVIDER_PRIVATE_KEY is not set",
            f".env is read from the working directory, and we looked in {env_path}. "
            "Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY "
            "in the environment.",
        )

    if not network_public_key:
        raise ConfigurationError(
            "NETWORK_PUBLIC_KEY is not set",
            "Ask the t-0 team for the network public key and put it in .env.",
        )

    if not _NETWORK_PUBLIC_KEY_PATTERN.match(network_public_key):
        raise ConfigurationError(
            "NETWORK_PUBLIC_KEY is not a valid uncompressed secp256k1 public key",
            f"Expected 130 hex characters (65 bytes), optionally 0x-prefixed; "
            f"got {len(network_public_key)} characters.",
        )

    try:
        port = int(port_raw)
    except ValueError:
        raise ConfigurationError(
            f"PORT is not a valid port number: {port_raw}",
            "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
        )
    if port < 1 or port > 65535:
        raise ConfigurationError(
            f"PORT is not a valid port number: {port_raw}",
            "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
        )

    try:
        public_key = public_key_from_private_key(private_key)
    except Exception as e:
        raise ConfigurationError(
            f"PROVIDER_PRIVATE_KEY is not usable: {e}",
            "Any 32 random bytes will do: openssl rand -hex 32.",
        )

    return Config(
        private_key=private_key,
        network_public_key=network_public_key,
        tzero_endpoint=tzero_endpoint,
        port=port,
        public_key=public_key,
    )
