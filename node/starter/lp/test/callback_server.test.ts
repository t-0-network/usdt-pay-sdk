import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { after, test } from "node:test";
import { setTimeout } from "node:timers/promises";
import { createClient as createLocalClient, createRouterTransport } from "@connectrpc/connect";
import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  Code,
  ConnectError,
  LpCallbackService,
  LpService,
  createClient,
  createServer,
  publicKeyFromPrivateKey,
} from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { lpCallbackHandler } from "../src/handler.js";
import { startQuotePublisher } from "../src/quotes.js";

// In production this is t-0's key and you only hold the public half. Here the test
// plays t-0, so it signs with the key the server is told to trust.
const NETWORK_PRIVATE_KEY = "0x" + "11".repeat(32);

const server = await createServer(0, publicKeyFromPrivateKey(NETWORK_PRIVATE_KEY), (r) => {
  r.service(LpCallbackService, lpCallbackHandler);
});
after(() => {
  server.close();
  server.closeAllConnections();
});

const t0 = createClient(
  NETWORK_PRIVATE_KEY,
  `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
  LpCallbackService,
);

const request = {
  executionId: 1n,
  quoteId: 2n,
  quoteRef: "COP-test-ref",
  acquirerId: 3n,
  localAmount: decimalFromString("100000.00"),
  amountUsdt: decimalFromString("25.00"),
  localCurrency: "COP",
  fxRate: decimalFromString("4000.00"),
  executedAt: timestampFromDate(new Date()),
};

test("ExecuteQuote answers accepted", async () => {
  const response = await t0.executeQuote(request);

  assert.equal(response.result.case, "accepted");
});

for (const error of [new ConnectError("t-0 is down", Code.Unavailable), new Error("local failure")]) {
  test(`a quote failure (${error.message}) logs once and leaves the timer and server running`, async (t) => {
    const quotesT0 = createLocalClient(LpService, createRouterTransport(({ service }) => {
      service(LpService, {
        publishQuote: async () => ({ result: { case: "success", value: { quotes: [] } } }),
      });
    }));
    const publishQuote = quotesT0.publishQuote;
    let attempts = 0;
    t.mock.method(quotesT0, "publishQuote", async (...args: Parameters<typeof publishQuote>) => {
      if (++attempts === 1) throw error;
      return publishQuote(...args);
    });
    const stderr = t.mock.method(console, "error", () => {});
    const stop = startQuotePublisher(quotesT0, { refreshMs: 10 });
    t.after(stop);

    await setTimeout(50);
    assert.ok(attempts >= 2, "the next tick must run after a failure");
    const prefix = error instanceof ConnectError ? "PublishQuote failed" : "PublishQuote tick failed";
    assert.deepEqual(stderr.mock.calls.map(({ arguments: args }) => args), [
      [`${prefix}: ${error.message}`],
    ]);
    assert.equal((await t0.executeQuote(request)).result.case, "accepted");

    stop();
    const stoppedAt = attempts;
    await setTimeout(30);
    assert.equal(attempts, stoppedAt);
  });
}

test("a call signed by anyone but t-0 never reaches the handler", async () => {
  const impostor = createClient(
    "0x" + "22".repeat(32),
    `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
    LpCallbackService,
  );

  await assert.rejects(impostor.executeQuote(request), /unauthenticated/i);
});

/**
 * `grpc.health.v1.Health` is on the port alongside the services you registered — the
 * transport mounts it so t-0 can see the endpoint is up.
 *
 * The status codes are the discriminator: an unrouted path is answered 404 by the router
 * before the signature check ever runs, a routed one gets to the signature check and is
 * refused 400. So 400 here means routed.
 */
test("the port carries what you registered plus health", async () => {
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  const post = (path: string) =>
    fetch(base + path, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "{}",
    });

  assert.equal((await post("/grpc.health.v1.Health/Check")).status, 400);
  assert.equal(
    (await post(`/${LpCallbackService.typeName}/ExecuteQuote`)).status,
    400,
  );
});
