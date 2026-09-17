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
  LpService,
  FiatSettlementSentResponse_Rejected_Reason,
} from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { reportFiatSettlementSent } from "../src/internal/fiat_settlement_sent.js";

function fakeT0(fiatSettlementSent: ServiceImpl<typeof LpService>["fiatSettlementSent"]) {
  return createClient(
    LpService,
    createRouterTransport(({ service }) => {
      service(LpService, { fiatSettlementSent } as ServiceImpl<typeof LpService>);
    }),
  );
}

const settlement = {
  bankTransferRef: "bank-ref-1",
  settledExecutionIds: [7n, 8n],
  localCurrency: "COP",
  settlementAmount: decimalFromString("400000.00"),
  settledAt: new Date("2026-01-01T00:00:00Z"),
};

test("accepted when t-0 verifies the settlement", async () => {
  let seen: { bankTransferRef: string; ids: bigint[] } | undefined;
  const t0 = fakeT0(async (request) => {
    seen = { bankTransferRef: request.bankTransferRef, ids: request.settledExecutionIds };
    return { result: { case: "accepted", value: {} } };
  });

  const outcome = await reportFiatSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "accepted");
  assert.equal(outcome.shouldRetry, false);
  assert.equal(seen?.bankTransferRef, "bank-ref-1");
  assert.deepEqual(seen?.ids, [7n, 8n]);
});

test("rejected is an answer, so do not retry it", async () => {
  const t0 = fakeT0(async () => ({
    result: {
      case: "rejected",
      value: {
        reason: FiatSettlementSentResponse_Rejected_Reason.EXECUTION_ALREADY_COVERED,
      },
    },
  }));

  const outcome = await reportFiatSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "rejected");
  assert.equal(outcome.shouldRetry, false);
  assert.equal(outcome.kind === "rejected" && outcome.reason, "EXECUTION_ALREADY_COVERED");
});

test("no answer becomes unknown, which must be retried under the same ref", async () => {
  const t0 = fakeT0(async () => {
    throw new ConnectError("t-0 is down", Code.Unavailable);
  });

  const outcome = await reportFiatSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "unknown");
  assert.equal(outcome.shouldRetry, true);
});

/**
 * FAILED_PRECONDITION means an execution has no durable result yet — the contract
 * says retry the same request. It must come back as `unknown` so the caller retries.
 */
test("FAILED_PRECONDITION is unknown and retryable", async () => {
  const t0 = fakeT0(async () => {
    throw new ConnectError("execution pending", Code.FailedPrecondition);
  });

  const outcome = await reportFiatSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "unknown");
  assert.equal(outcome.shouldRetry, true);
});

test("a request t-0 refuses outright is rejected, not retried", async () => {
  const t0 = fakeT0(async () => {
    throw new ConnectError("settled_execution_ids must not be empty", Code.InvalidArgument);
  });

  const outcome = await reportFiatSettlementSent(t0, settlement);

  assert.equal(outcome.kind, "rejected");
  assert.equal(outcome.shouldRetry, false);
});
