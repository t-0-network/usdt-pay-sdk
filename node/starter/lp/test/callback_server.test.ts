import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { after, test } from "node:test";
import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  LpCallbackService,
  createClient,
  createServer,
  publicKeyFromPrivateKey,
} from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { lpCallbackHandler } from "../src/handler.js";

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
  `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
  NETWORK_PRIVATE_KEY,
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

test("a call signed by anyone but t-0 never reaches the handler", async () => {
  const impostor = createClient(
    `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
    "0x" + "22".repeat(32),
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
