"""Config validation tests."""

import pytest
from acquirer.config import ConfigurationError, load_config


@pytest.fixture(autouse=True)
def _clean_env(monkeypatch):
    for key in ["PROVIDER_PRIVATE_KEY", "NETWORK_PUBLIC_KEY", "TZERO_ENDPOINT", "PORT"]:
        monkeypatch.delenv(key, raising=False)


def test_missing_private_key(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    with pytest.raises(ConfigurationError, match="PROVIDER_PRIVATE_KEY is not set"):
        load_config()


def test_missing_network_public_key(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", "0x" + "ab" * 32)
    with pytest.raises(ConfigurationError, match="NETWORK_PUBLIC_KEY is not set"):
        load_config()


def test_invalid_network_public_key(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", "0x" + "ab" * 32)
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", "not-a-key")
    with pytest.raises(ConfigurationError, match="not a valid uncompressed"):
        load_config()


def test_network_public_key_with_trailing_newline_is_stripped(monkeypatch, tmp_path):
    """Env vars are stripped, so a trailing newline doesn't break config."""
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", "0x" + "ab" * 32)
    from t0_usdt_pay_sdk.keys import public_key_from_private_key

    valid_key = public_key_from_private_key("0x" + "ab" * 32)
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", valid_key + "\n")
    config = load_config()
    assert config.network_public_key == valid_key


def test_invalid_port(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", "0x" + "ab" * 32)
    from t0_usdt_pay_sdk.keys import public_key_from_private_key

    monkeypatch.setenv("NETWORK_PUBLIC_KEY", public_key_from_private_key("0x" + "ab" * 32))
    monkeypatch.setenv("PORT", "not-a-number")
    with pytest.raises(ConfigurationError, match="PORT is not a valid port"):
        load_config()


def test_valid_config(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", "0x" + "ab" * 32)
    from t0_usdt_pay_sdk.keys import public_key_from_private_key

    monkeypatch.setenv("NETWORK_PUBLIC_KEY", public_key_from_private_key("0x" + "ab" * 32))
    config = load_config()
    assert config.port == 8080
    assert config.tzero_endpoint == "https://usdt-pay-api-sandbox.t-0.network"
    assert config.public_key.startswith("0x04")
