"""Registry: every pay descriptor is in the pool and custom predefined rules resolve."""

from __future__ import annotations

import subprocess
import sys

import protovalidate
from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2


def _validator() -> protovalidate.Validator:
    return protovalidate.Validator()


def test_valid_create_payment_intent_request_passes():
    req = acquirer_pb2.CreatePaymentIntentRequest()
    req.payment_ref = "order-42"
    req.idempotency_key = "key-1"
    sa = acquirer_pb2.CreatePaymentIntentRequest.SettlementAmount()
    sa.value.CopyFrom(common_pb2.Decimal(unscaled=100, exponent=-2))
    req.settlement.CopyFrom(sa)
    _validator().validate(req)


def test_invalid_tx_hash_fails_with_rule_id():
    payment = common_pb2.UsdtOnChainPayment()
    payment.chain = common_pb2.BLOCKCHAIN_ETH
    payment.on_chain_tx_hash = "short"
    payment.sender_address = "a" * 34

    violations = _validator().collect_violations(payment)
    rule_ids = [v.proto.rule_id for v in violations]
    assert "string.valid_tx_hash" in rule_ids


def test_short_address_fails_with_rule_id():
    payment = common_pb2.UsdtOnChainPayment()
    payment.chain = common_pb2.BLOCKCHAIN_ETH
    payment.on_chain_tx_hash = "0x" + "a" * 64
    payment.sender_address = "too_short"

    violations = _validator().collect_violations(payment)
    rule_ids = [v.proto.rule_id for v in violations]
    assert "string.valid_address" in rule_ids


def test_import_order_sdk_first():
    """Import t0_usdt_pay_sdk first in a fresh interpreter — rules must resolve."""
    code = (
        "import t0_usdt_pay_sdk\n"
        "from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2\n"
        "import protovalidate\n"
        "p = common_pb2.UsdtOnChainPayment(chain=common_pb2.BLOCKCHAIN_ETH,\n"
        "    on_chain_tx_hash='short', sender_address='a'*34)\n"
        "v = protovalidate.Validator().collect_violations(p)\n"
        "ids = [x.proto.rule_id for x in v]\n"
        "assert 'string.valid_tx_hash' in ids, f'missing valid_tx_hash in {ids}'\n"
    )
    result = subprocess.run(
        [sys.executable, "-c", code],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, f"stdout={result.stdout}\nstderr={result.stderr}"


def test_import_order_provider_sdk_first():
    """Import t0_provider_sdk first — the reverse order must also work."""
    code = (
        "import t0_provider_sdk\n"
        "import t0_usdt_pay_sdk\n"
        "from t0_usdt_pay_sdk.api.tzero.v1.pay import common_pb2\n"
        "import protovalidate\n"
        "p = common_pb2.UsdtOnChainPayment(chain=common_pb2.BLOCKCHAIN_ETH,\n"
        "    on_chain_tx_hash='short', sender_address='a'*34)\n"
        "v = protovalidate.Validator().collect_violations(p)\n"
        "ids = [x.proto.rule_id for x in v]\n"
        "assert 'string.valid_tx_hash' in ids, f'missing valid_tx_hash in {ids}'\n"
    )
    result = subprocess.run(
        [sys.executable, "-c", code],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, f"stdout={result.stdout}\nstderr={result.stderr}"
