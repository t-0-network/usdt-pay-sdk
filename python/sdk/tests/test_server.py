"""The pay contract's custom rules fire through every server factory and through validate.

valid_address and valid_tx_hash are proto2 extensions in this SDK's own generated code. These
tests make one fire through create_asgi_app, create_wsgi_app and validate, so a rule that could
not be resolved would show up here as a different error. The calls go to the app in-process,
signed by hand, so no HTTP server is needed.
"""

from __future__ import annotations

import asyncio
import io
import json
import struct
import time

import pytest
from google.protobuf.timestamp_pb2 import Timestamp
from t0_usdt_pay_sdk import (
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
    Code,
    ConnectError,
    create_asgi_app,
    create_wsgi_app,
    handler,
    handler_sync,
    legacy_keccak256,
    new_signer_from_hex,
    public_key_from_private_key,
    validate,
)
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.issuer import issuer_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.issuer.issuer_connect import (
    IssuerCallbackServiceASGIApplication,
    IssuerCallbackServiceWSGIApplication,
)

# The test plays t-0: it signs with the network key, and the app verifies against its public half.
NETWORK_PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
NETWORK_PUBLIC_KEY = public_key_from_private_key(NETWORK_PRIVATE_KEY)

PATH = "/tzero.v1.pay.issuer.IssuerCallbackService/CreatePaymentInstructions"
BAD_RESPONSE_MESSAGE = (
    "response validation failed: success.deposit_options[0].deposit_address: must be 34-42 characters"
)


def _in_an_hour() -> Timestamp:
    ts = Timestamp()
    ts.FromSeconds(int(time.time()) + 3600)
    return ts


def _bad_instructions() -> issuer_pb2.CreatePaymentInstructionsResponse:
    """Breaks exactly one rule: deposit_address is too short."""
    response = issuer_pb2.CreatePaymentInstructionsResponse()
    response.success.expires_at.CopyFrom(_in_an_hour())
    option = response.success.deposit_options.add()
    option.chain = common_pb2.BLOCKCHAIN_ETH
    option.deposit_address = "bad"
    option.token_contract = "0x" + "bb" * 20
    return response


def _valid_request_body() -> bytes:
    request = issuer_pb2.CreatePaymentInstructionsRequest(
        payment_intent_id=1,
        acquirer_id=2,
        amount_usdt=common_pb2.Decimal(unscaled=1000, exponent=-2),
    )
    request.expires_at.CopyFrom(_in_an_hour())
    return request.SerializeToString()


def _signed_headers(body: bytes) -> dict[str, str]:
    """X-Public-Key, X-Signature and X-Signature-Timestamp over Keccak256(body + 64-bit LE ms)."""
    timestamp_ms = int(time.time() * 1000)
    signature, public_key = new_signer_from_hex(NETWORK_PRIVATE_KEY)(
        legacy_keccak256(body + struct.pack("<Q", timestamp_ms))
    )
    return {
        PUBLIC_KEY_HEADER: "0x" + public_key.hex(),
        SIGNATURE_HEADER: "0x" + signature.hex(),
        SIGNATURE_TIMESTAMP_HEADER: str(timestamp_ms),
    }


def _error(status: int, body: bytes) -> tuple[str, str]:
    assert status != 200, "the response was served, not refused"
    error = json.loads(body)
    return error["code"], error["message"]


class _BadIssuer:
    async def create_payment_instructions(self, request, ctx):
        return _bad_instructions()


class _BadIssuerSync:
    def create_payment_instructions(self, request, ctx):
        return _bad_instructions()


async def _call_asgi(app, body: bytes) -> tuple[str, str]:
    headers = {"content-type": "application/proto", **_signed_headers(body)}
    scope = {
        "type": "http",
        "method": "POST",
        "path": PATH,
        "root_path": "",
        "query_string": b"",
        "headers": [(k.lower().encode(), v.encode()) for k, v in headers.items()],
    }
    sent: list[dict] = []

    async def receive():
        return {"type": "http.request", "body": body, "more_body": False}

    async def send(message):
        sent.append(message)

    await app(scope, receive, send)
    return _error(sent[0]["status"], b"".join(m.get("body", b"") for m in sent[1:]))


def _call_wsgi(app, body: bytes) -> tuple[str, str]:
    environ = {
        "REQUEST_METHOD": "POST",
        "PATH_INFO": PATH,
        "SCRIPT_NAME": "",
        "CONTENT_TYPE": "application/proto",
        "CONTENT_LENGTH": str(len(body)),
        "wsgi.input": io.BytesIO(body),
        "wsgi.errors": io.StringIO(),
    }
    for name, value in _signed_headers(body).items():
        environ["HTTP_" + name.upper().replace("-", "_")] = value
    statuses: list[int] = []

    def start_response(status, response_headers, exc_info=None):
        statuses.append(int(status.split()[0]))

    response = b"".join(app(environ, start_response))
    return _error(statuses[0], response)


def test_create_asgi_app_refuses_a_response_that_breaks_a_custom_rule():
    app = create_asgi_app(NETWORK_PUBLIC_KEY, handler(IssuerCallbackServiceASGIApplication, _BadIssuer()))
    assert asyncio.run(_call_asgi(app, _valid_request_body())) == ("internal", BAD_RESPONSE_MESSAGE)


def test_create_wsgi_app_refuses_a_response_that_breaks_a_custom_rule():
    app = create_wsgi_app(NETWORK_PUBLIC_KEY, handler_sync(IssuerCallbackServiceWSGIApplication, _BadIssuerSync()))
    assert _call_wsgi(app, _valid_request_body()) == ("internal", BAD_RESPONSE_MESSAGE)


def test_validate_fires_a_custom_rule():
    with pytest.raises(ConnectError) as exc_info:
        validate(_bad_instructions())
    assert exc_info.value.code is Code.INTERNAL
    assert exc_info.value.message == BAD_RESPONSE_MESSAGE
