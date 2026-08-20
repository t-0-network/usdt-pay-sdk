import assert from "node:assert/strict";
import { test } from "node:test";
import {
  Code,
  ConnectError,
  createClient,
  createRouterTransport,
  type ServiceImpl,
} from "@connectrpc/connect";
import {
  Blockchain,
  IssuerService,
  SettlementSentResponse_Rejected_Reason,
} from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { reportSettlementSent } from "../src/internal/settlement_sent.js";

/**
 * How to test against t-0 without t-0: stand a fake up in memory and hand the helper a
 * real client pointed at it. Copy this shape for your own tests — it needs no server,
 * no signing keys and no network.
 */
function fakeT0(settlementSent: ServiceImpl<typeof IssuerService>["settlementSent"]) {
  return createClient(
    IssuerService,
    createRouterTransport(({ service }) => {
      service(IssuerService, { settlementSent } as ServiceImpl<typeof IssuerService>);
    }),
  );
}

const settlement = {
  settlementRef: "settlement-ref-1",
  amountUsdt: decimalFromString("100.00"),
  chain: Blockchain.TRON,
  txHash: "0xdeadbeef",
  destinationAddress: "TAcquirerWallet",
  settledPaymentIntentIds: [7n, 8n],
  settledAt: new Date("2026-01-01T00:00:00Z"),
};

test("accepted when t-0 verifies the transfer on-chain", async () => {
  let seen: { settlementRef: string; ids: bigint[] } | undefined;
  const t0 = fakeT0(async (request) => {
    seen = { settlementRef: request.settlementRef, ids: request.settledPaymentIntentIds };
    return { result: { case: "accepted", value: {} } };
  });

  const outcome = await reportSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "accepted");
  assert.equal(outcome.shouldRetry, false);
  // The caller's ref reaches the wire unchanged: it is the idempotency key, and a ref
  // invented inside the helper would be unresendable.
  assert.equal(seen?.settlementRef, "settlement-ref-1");
  assert.deepEqual(seen?.ids, [7n, 8n]);
});

test("rejected is an answer, so do not retry it", async () => {
  const t0 = fakeT0(async () => ({
    result: {
      case: "rejected",
      value: {
        reason: SettlementSentResponse_Rejected_Reason.WRONG_DESTINATION,
      },
    },
  }));

  const outcome = await reportSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "rejected");
  assert.equal(outcome.shouldRetry, false);
  assert.equal(outcome.kind === "rejected" && outcome.reason, "WRONG_DESTINATION");
});

/**
 * The branch a happy-path test against a sandbox never reaches, and the one that costs
 * money: no answer came back, so the settlement may or may not be recorded. Retry the
 * same ref — never broadcast a second transfer.
 */
test("no answer becomes unknown, which must be retried under the same ref", async () => {
  const t0 = fakeT0(async () => {
    throw new ConnectError("t-0 is down", Code.Unavailable);
  });

  const outcome = await reportSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "unknown");
  assert.equal(outcome.shouldRetry, true);
});

/**
 * A refused request is not a lost one. Retrying bytes t-0 has already read and
 * rejected just spins forever, so the permanent codes come back as `rejected`.
 */
test("a request t-0 refuses outright is rejected, not retried", async () => {
  const t0 = fakeT0(async () => {
    throw new ConnectError("settled_payment_intent_ids must not be empty", Code.InvalidArgument);
  });

  const outcome = await reportSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "rejected");
  assert.equal(outcome.shouldRetry, false);
});
