"""Config validation tests: the exact messages and help a misconfigured start prints."""

import sys

import pytest
import uvicorn
from acquirer import main
from acquirer.config import ConfigurationError, load_config
from t0_usdt_pay_sdk import create_asgi_app
from t0_usdt_pay_sdk.keys import public_key_from_private_key

PRIVATE_KEY = "0x" + "ab" * 32
NETWORK_PUBLIC_KEY = public_key_from_private_key("0x" + "cd" * 32)

PRIVATE_KEY_HELP = (
    ".env is read from the working directory, and we looked in {env_path}. Run the app from the "
    "directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. Only a project "
    "with no .env at all starts one from .env.example — an existing .env holds the key generated "
    "for you, and its private half is not recoverable."
)
NETWORK_PUBLIC_KEY_HELP = "Ask the t-0 team for the network public key and put it in .env."
PORT_HELP = "Set PORT to an integer between 1 and 65535, or leave it unset for 8080."


@pytest.fixture(autouse=True)
def _clean_env(monkeypatch, tmp_path):
    for key in ["PROVIDER_PRIVATE_KEY", "NETWORK_PUBLIC_KEY", "TZERO_ENDPOINT", "PORT", "PYTHON_DOTENV_DISABLED"]:
        # setenv first so monkeypatch records the key and removes it at teardown, including
        # a value load_dotenv() wrote into os.environ during the test.
        monkeypatch.setenv(key, "")
        monkeypatch.delenv(key)
    monkeypatch.chdir(tmp_path)


@pytest.fixture()
def keys(monkeypatch):
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", PRIVATE_KEY)
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", NETWORK_PUBLIC_KEY)


def _env_path(tmp_path):
    return (tmp_path / ".env").resolve()


def test_missing_env_file_notice(keys, tmp_path, capsys):
    load_config()
    assert capsys.readouterr().err == (
        f"No .env at {_env_path(tmp_path)} — taking configuration from the environment instead\n"
    )


def test_env_file_is_read(tmp_path, capsys):
    (tmp_path / ".env").write_text(f"PROVIDER_PRIVATE_KEY={PRIVATE_KEY}\nNETWORK_PUBLIC_KEY={NETWORK_PUBLIC_KEY}\n")
    config = load_config()
    assert config.private_key == PRIVATE_KEY
    assert capsys.readouterr().err == ""


def test_environment_wins_over_env_file(monkeypatch, tmp_path):
    (tmp_path / ".env").write_text(
        f"PROVIDER_PRIVATE_KEY={PRIVATE_KEY}\nNETWORK_PUBLIC_KEY={NETWORK_PUBLIC_KEY}\nPORT=9000\n"
    )
    monkeypatch.setenv("PORT", "9100")
    assert load_config().port == 9100


def test_missing_private_key(monkeypatch, tmp_path):
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", NETWORK_PUBLIC_KEY)
    with pytest.raises(ConfigurationError) as raised:
        load_config()
    assert str(raised.value) == "PROVIDER_PRIVATE_KEY is not set"
    assert raised.value.help_text == PRIVATE_KEY_HELP.format(env_path=_env_path(tmp_path))


def test_blank_private_key_counts_as_missing(monkeypatch):
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", "  \n")
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", NETWORK_PUBLIC_KEY)
    with pytest.raises(ConfigurationError, match="^PROVIDER_PRIVATE_KEY is not set$"):
        load_config()


def test_malformed_private_key(monkeypatch):
    # The reason is the SDK's own message, so take it from the SDK.
    with pytest.raises(ValueError) as sdk_error:
        public_key_from_private_key("0x1234")
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", "0x1234")
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", NETWORK_PUBLIC_KEY)
    with pytest.raises(ConfigurationError) as raised:
        load_config()
    assert str(raised.value) == f"PROVIDER_PRIVATE_KEY is not usable: {sdk_error.value}"
    assert raised.value.help_text == "Any 32 random bytes will do: openssl rand -hex 32."


def test_missing_network_public_key(monkeypatch):
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", PRIVATE_KEY)
    with pytest.raises(ConfigurationError) as raised:
        load_config()
    assert str(raised.value) == "NETWORK_PUBLIC_KEY is not set"
    assert raised.value.help_text == NETWORK_PUBLIC_KEY_HELP


def test_keys_are_stripped(monkeypatch):
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", f" {PRIVATE_KEY}\n")
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", NETWORK_PUBLIC_KEY + "\n")
    config = load_config()
    assert config.private_key == PRIVATE_KEY
    assert config.network_public_key == NETWORK_PUBLIC_KEY


@pytest.mark.parametrize(
    "value",
    [
        "not-a-number",
        "0",
        "65536",
        "-1",
        "+80",
        "8_080",
        "0x50",
        "8e3",
        "80.0",
        "١٢٣",
        "８０",
        " 0 ",
        pytest.param("1" * 5000, id="5000-digits"),
    ],
)
def test_invalid_port(keys, monkeypatch, value):
    monkeypatch.setenv("PORT", value)
    with pytest.raises(ConfigurationError) as raised:
        load_config()
    assert str(raised.value) == f"PORT is not a valid port number: {value}"
    assert raised.value.help_text == PORT_HELP


@pytest.mark.parametrize(
    ("value", "port"),
    [("", 8080), ("   ", 8080), (" 9000\n", 9000), ("1", 1), ("65535", 65535), ("0080", 80)],
)
def test_valid_port(keys, monkeypatch, value, port):
    monkeypatch.setenv("PORT", value)
    assert load_config().port == port


@pytest.mark.parametrize("value", [None, ""])
def test_tzero_endpoint_default(keys, monkeypatch, value):
    if value is not None:
        monkeypatch.setenv("TZERO_ENDPOINT", value)
    assert load_config().tzero_endpoint == "https://usdt-pay-api-sandbox.t-0.network"


def test_valid_config(keys):
    config = load_config()
    assert config.port == 8080
    assert config.tzero_endpoint == "https://usdt-pay-api-sandbox.t-0.network"
    assert config.public_key == public_key_from_private_key(PRIVATE_KEY)


def test_run_prints_configuration_error(monkeypatch, capsys):
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", NETWORK_PUBLIC_KEY)
    assert main.run() == 1
    lines = capsys.readouterr().err.splitlines()
    assert lines[-2] == "ERROR: PROVIDER_PRIVATE_KEY is not set"
    assert lines[-1].startswith(".env is read from the working directory")


def test_run_prints_malformed_network_public_key(monkeypatch, capsys):
    # The message is the SDK's own, so take it from the SDK.
    with pytest.raises(ValueError) as sdk_error:
        create_asgi_app("04zz")
    assert str(sdk_error.value).startswith("invalid network public key: ")
    monkeypatch.setenv("PROVIDER_PRIVATE_KEY", PRIVATE_KEY)
    monkeypatch.setenv("NETWORK_PUBLIC_KEY", "04zz")
    assert main.run() == 1
    lines = capsys.readouterr().err.splitlines()
    assert lines[-2] == f"ERROR: {sdk_error.value}"
    assert lines[-1] == NETWORK_PUBLIC_KEY_HELP


def test_run_prints_startup_failure(keys, monkeypatch, capsys):
    async def startup(self, sockets=None):
        # What uvicorn does when the bind fails: log the OSError, then sys.exit() inside the except.
        try:
            raise OSError(48, "error while attempting to bind on address ('0.0.0.0', 8080): address already in use")
        except OSError:
            sys.exit(3)

    monkeypatch.setattr(uvicorn.Server, "startup", startup)
    assert main.run() == 1
    lines = capsys.readouterr().err.splitlines()
    assert lines[-1] == (
        "Acquirer failed to start: [Errno 48] error while attempting to bind on address ('0.0.0.0', 8080): "
        "address already in use"
    )
