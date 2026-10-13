import assert from "node:assert/strict";
import { test } from "node:test";
import { createClient, createRouterTransport } from "@connectrpc/connect";
import { Code, ConnectError, LpService, type Client } from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { reportFiatSettlementSent } from "../src/internal/fiat_settlement_sent.js";
import { publishQuotes } from "../src/internal/publish_quote.js";

const calls = [
  {
    name: "PublishQuote",
    prefix: "PublishQuote failed:",
    call: (t0: Client<typeof LpService>) => publishQuotes(t0, [{
      quoteRef: "quote-ref-1",
      localCurrency: "COP",
      fxRate: decimalFromString("4000.00"),
      expiresAt: new Date(Date.now() + 300_000),
    }]),
  },
  {
    name: "FiatSettlementSent",
    prefix: "FiatSettlementSent failed for ref bank-ref-1:",
    call: (t0: Client<typeof LpService>) => reportFiatSettlementSent(t0, {
      bankTransferRef: "bank-ref-1",
      settledExecutionIds: [7n],
      localCurrency: "COP",
      settlementAmount: decimalFromString("400000.00"),
      settledAt: new Date("2026-01-01T00:00:00Z"),
    }),
  },
];

for (const { name, prefix, call } of calls) {
  for (const error of [
    new ConnectError("t-0 is down", Code.Unavailable),
    new ConnectError("bad field", Code.InvalidArgument),
    new ConnectError("execution pending", Code.FailedPrecondition),
    new Error("local failure"),
  ]) {
    test(`${name} logs only the message for ${error.message}`, async (t) => {
      const t0 = createClient(LpService, createRouterTransport(() => {}));
      t.mock.method(t0, "publishQuote", async () => { throw error; });
      t.mock.method(t0, "fiatSettlementSent", async () => { throw error; });
      const stderr = t.mock.method(console, "error", () => {});

      if (error instanceof ConnectError) {
        const outcome = await call(t0);
        const retryable = error.code === Code.Unavailable ||
          (name === "FiatSettlementSent" && error.code === Code.FailedPrecondition);
        assert.equal(outcome.kind, retryable ? "unknown" : "rejected");
        assert.equal(outcome.shouldRetry, retryable);
      } else {
        await assert.rejects(call(t0), (thrown) => thrown === error);
      }

      // A local PublishQuote error is logged by its caller (the demo timer), not twice.
      const expected = name === "PublishQuote" && !(error instanceof ConnectError)
        ? [] : [[`${prefix} ${error.message}`]];
      assert.deepEqual(stderr.mock.calls.map(({ arguments: args }) => args), expected);
    });
  }
}
