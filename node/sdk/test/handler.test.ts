import assert from "node:assert/strict";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { after, test } from "node:test";
import { create } from "@bufbuild/protobuf";
import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import { ConnectError, Code } from "@connectrpc/connect";
import {
  createUsdtPayClient,
  createUsdtPayHandler,
  CreatePaymentInstructionsResponse_Failure_Reason,
  CreatePaymentInstructionsResponseSchema,
  DecimalSchema,
  IssuerCallbackService,
  publicKeyFromPrivateKey,
} from "../src/index.js";

// The test plays t-0: it signs with the key the handler is told to trust.
const NETWORK_PRIVATE_KEY = "0x" + "11".repeat(32);

const decline = create(CreatePaymentInstructionsResponseSchema, {
  result: {
    case: "failure",
    value: { reason: CreatePaymentInstructionsResponse_Failure_Reason.ISSUER_UNAVAILABLE },
  },
});

const handler = createUsdtPayHandler(publicKeyFromPrivateKey(NETWORK_PRIVATE_KEY), (r) => {
  r.service(IssuerCallbackService, {
    createPaymentInstructions: () => decline,
  });
});

// Mounted the way Express mounts a sub-app: the prefix is stripped from req.url
// before the handler routes on the Connect path. This is the documented pattern
// (`app.use("/sda/payments/t0", handler)`) without depending on Express.
const PREFIX = "/sda/payments/t0";
const server = http.createServer((req, res) => {
  if (req.url?.startsWith(PREFIX + "/")) {
    req.url = req.url.slice(PREFIX.length);
    handler(req, res);
  } else {
    res.writeHead(404);
    res.end();
  }
});
await new Promise<void>((resolve) => server.listen(0, resolve));
after(() => {
  server.close();
  server.closeAllConnections();
});
const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

// The request has to satisfy the contract's validation rules — the handler
// validates before the implementation ever runs.
const request = {
  paymentIntentId: 1n,
  acquirerId: 2n,
  amountUsdt: create(DecimalSchema, { unscaled: 1000n, exponent: -2 }),
  expiresAt: timestampFromDate(new Date(Date.now() + 120_000)),
};

test("the handler serves a signed call under an Express-style mount prefix", async () => {
  const t0 = createUsdtPayClient(`${base}${PREFIX}`, NETWORK_PRIVATE_KEY, IssuerCallbackService);
  const response = await t0.createPaymentInstructions(request);
  assert.equal(response.result.case, "failure");
});

test("a call signed by anyone but t-0 is refused before the handler runs", async () => {
  const impostor = createUsdtPayClient(
    `${base}${PREFIX}`,
    "0x" + "22".repeat(32),
    IssuerCallbackService,
  );
  await assert.rejects(
    impostor.createPaymentInstructions(request),
    (err: unknown) => err instanceof ConnectError && err.code === Code.Unauthenticated,
  );
});

test("the health service is mounted and behind the same signature check", async () => {
  // An unsigned probe: Connect path for grpc.health.v1.Health/Check with an empty
  // request. It must NOT answer healthy — health is part of the signed contract.
  const probe = await fetch(`${base}${PREFIX}/grpc.health.v1.Health/Check`, {
    method: "POST",
    headers: { "content-type": "application/proto" },
    body: new Uint8Array(0),
  });
  assert.notEqual(probe.status, 200);
});
