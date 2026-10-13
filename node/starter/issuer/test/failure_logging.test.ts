import assert from "node:assert/strict";
import { test } from "node:test";
import { createClient, createRouterTransport } from "@connectrpc/connect";
import { Blockchain, Code, ConnectError, IssuerService, type Client } from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { reportPaymentReceived } from "../src/internal/payment_received.js";
import { reportSettlementSent } from "../src/internal/settlement_sent.js";

const calls = [
  {
    name: "PaymentReceived",
    prefix: "PaymentReceived failed for intent 7:",
    call: (t0: Client<typeof IssuerService>) => reportPaymentReceived(t0, {
      paymentIntentId: 7n,
      amountUsdt: decimalFromString("100.00"),
      chain: Blockchain.TRON,
      txHash: "0xdeadbeef",
      senderAddress: "TSenderWallet",
      receivedAt: new Date("2026-01-01T00:00:00Z"),
    }),
  },
  {
    name: "SettlementSent",
    prefix: "SettlementSent failed for ref settlement-ref-1:",
    call: (t0: Client<typeof IssuerService>) => reportSettlementSent(t0, {
      settlementRef: "settlement-ref-1",
      amountUsdt: decimalFromString("100.00"),
      chain: Blockchain.TRON,
      txHash: "0xdeadbeef",
      destinationAddress: "TAcquirerWallet",
      settledPaymentIntentIds: [7n],
      settledAt: new Date("2026-01-01T00:00:00Z"),
    }),
  },
];

for (const { name, prefix, call } of calls) {
  for (const error of [
    new ConnectError("t-0 is down", Code.Unavailable),
    new ConnectError("bad field", Code.InvalidArgument),
    new Error("local failure"),
  ]) {
    test(`${name} logs only the message for ${error.message}`, async (t) => {
      const t0 = createClient(IssuerService, createRouterTransport(() => {}));
      // Mock the client method, not the router: the router converts ordinary errors to ConnectError.
      t.mock.method(t0, "paymentReceived", async () => { throw error; });
      t.mock.method(t0, "settlementSent", async () => { throw error; });
      const stderr = t.mock.method(console, "error", () => {});

      if (error instanceof ConnectError) {
        const outcome = await call(t0);
        assert.equal(outcome.kind, error.code === Code.Unavailable ? "unknown" : "rejected");
        assert.equal(outcome.shouldRetry, error.code === Code.Unavailable);
      } else {
        await assert.rejects(call(t0), (thrown) => thrown === error);
      }

      assert.deepEqual(stderr.mock.calls.map(({ arguments: args }) => args), [
        [`${prefix} ${error.message}`],
      ]);
    });
  }
}
