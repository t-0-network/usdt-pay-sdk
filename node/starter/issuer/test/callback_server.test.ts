import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { after, test } from "node:test";
import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  CreatePaymentInstructionsResponse_Failure_Reason,
  createUsdtPayClient,
  createUsdtPayServer,
  IssuerCallbackService,
  publicKeyFromPrivateKey,
} from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { issuerCallbackHandler } from "../src/handler.js";

// In production this is t-0's key and you only hold the public half. Here the test
// plays t-0, so it signs with the key the server is told to trust.
const NETWORK_PRIVATE_KEY = "0x" + "11".repeat(32);

const server = await createUsdtPayServer(0, publicKeyFromPrivateKey(NETWORK_PRIVATE_KEY), (r) => {
  r.service(IssuerCallbackService, issuerCallbackHandler);
});
after(() => {
  server.close();
  server.closeAllConnections();
});

const t0 = createUsdtPayClient(
  `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
  NETWORK_PRIVATE_KEY,
  IssuerCallbackService,
);

const request = {
  paymentIntentId: 1n,
  acquirerId: 2n,
  amountUsdt: decimalFromString("10.00"),
  // The contract requires expires_at in the future, so the request validator on the
  // server rejects a stale one before the handler sees it.
  expiresAt: timestampFromDate(new Date(Date.now() + 120_000)),
};

/**
 * The shipped handler declines, on purpose: whatever addresses it returns are rendered
 * as a payable QR and a customer sends real USDt to them. This test is what keeps that
 * true — if it starts failing because someone wired the success branch up with example
 * addresses, that is the bug it exists to catch.
 */
test("§5 declines until the addresses are yours", async () => {
  const response = await t0.createPaymentInstructions(request);

  assert.equal(response.result.case, "failure");
  assert.equal(
    response.result.case === "failure" ? response.result.value.reason : undefined,
    CreatePaymentInstructionsResponse_Failure_Reason.ISSUER_UNAVAILABLE,
  );
});

test("a call signed by anyone but t-0 never reaches the handler", async () => {
  const impostor = createUsdtPayClient(
    `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
    "0x" + "22".repeat(32),
    IssuerCallbackService,
  );

  await assert.rejects(impostor.createPaymentInstructions(request), /unauthenticated/i);
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
    (await post(`/${IssuerCallbackService.typeName}/CreatePaymentInstructions`)).status,
    400,
  );
});
