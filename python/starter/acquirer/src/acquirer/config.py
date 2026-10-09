"""Configuration from .env / environment variables."""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv
from t0_usdt_pay_sdk import public_key_from_private_key

NETWORK_PUBLIC_KEY_HELP = "Ask the t-0 team for the network public key and put it in .env."


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


def _parse_port(value: str) -> int:
    """PORT: ASCII digits, 1 to 65535. Unset or empty (after the trim) means 8080."""
    digits = value.strip()
    if not digits:
        return 8080
    # Past its leading zeros a port has at most five digits; checking that first also
    # keeps int() clear of its 4300-digit limit.
    significant = digits.lstrip("0")
    if not (digits.isascii() and digits.isdigit() and len(significant) <= 5 and 1 <= int(significant or "0") <= 65535):
        raise ConfigurationError(
            f"PORT is not a valid port number: {value}",
            "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
        )
    return int(significant)


def load_config() -> Config:
    env_path = Path(".env").resolve()
    if env_path.exists():
        load_dotenv(env_path)
    else:
        print(f"No .env at {env_path} — taking configuration from the environment instead", file=sys.stderr)

    private_key = os.environ.get("PROVIDER_PRIVATE_KEY", "").strip()
    network_public_key = os.environ.get("NETWORK_PUBLIC_KEY", "").strip()
    # An empty value counts as unset.
    tzero_endpoint = os.getenv("TZERO_ENDPOINT") or "https://usdt-pay-api-sandbox.t-0.network"

    if not private_key:
        raise ConfigurationError(
            "PROVIDER_PRIVATE_KEY is not set",
            f".env is read from the working directory, and we looked in {env_path}. "
            "Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY "
            "in the environment. Only a project with no .env at all starts one from .env.example "
            "— an existing .env holds the key generated for you, and its private half is not "
            "recoverable.",
        )

    if not network_public_key:
        raise ConfigurationError("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP)

    port = _parse_port(os.environ.get("PORT", ""))

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
