import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { after, mock, test } from "node:test";
import { create } from "@bufbuild/protobuf";
import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  CreatePaymentInstructionsRequestSchema,
  CreatePaymentInstructionsResponse_Failure_Reason,
  CreatePaymentInstructionsResponseSchema,
  createUsdtPayClient,
  createUsdtPayServer,
  DecimalSchema,
  IssuerCallbackService,
} from "../src/index.js";

/**
 * Runs `vectors/signature-v1.json` over the wire.
 *
 * The Java suite pins the algorithm against the same file with a different curve library.
 * What is left to pin is the wiring — that the SDK signs the body it actually sends, that
 * a golden signature gets through a real server, and that each negative case is refused
 * with the code the fixtures name. So these tests go through HTTP rather than calling the
 * crypto directly: verification here is the shipped interceptor's, not a re-implementation.
 *
 * The vectors carry a fixed timestamp, so every request runs under a fake clock — which is
 * also how the window cases get to be a test rather than a minute of waiting.
 */
const vectors = JSON.parse(
  readFileSync(new URL("../../../vectors/signature-v1.json", import.meta.url), "utf8"),
);

const network = vectors.keys.network;

let handled = 0;
const server = await createUsdtPayServer(0, network.publicKey, (r) => {
  r.service(IssuerCallbackService, {
    async createPaymentInstructions() {
      handled++;
      return create(CreatePaymentInstructionsResponseSchema, {
        result: {
          case: "failure",
          value: { reason: CreatePaymentInstructionsResponse_Failure_Reason.ISSUER_UNAVAILABLE },
        },
      });
    },
  });
});
after(() => {
  server.close();
  server.closeAllConnections();
});

const endpoint =
  `http://127.0.0.1:${(server.address() as AddressInfo).port}` +
  "/tzero.v1.pay.IssuerCallbackService/CreatePaymentInstructions";

const bytes = (hex: string) => Buffer.from(hex.slice(2), "hex");

for (const vector of vectors.verification) {
  test(`${vector.name}: ${vector.note}`, async () => {
    mock.timers.enable({ apis: ["Date"], now: vector.nowMs ?? vector.timestampMs });
    const before = handled;
    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "content-type": "application/proto",
          "x-public-key": vector.publicKey,
          "x-signature": vector.signature,
          "x-signature-timestamp": String(vector.timestampMs),
        },
        body: bytes(vector.payload),
      });

      if (vector.accept) {
        assert.equal(response.status, 200);
        assert.equal(handled, before + 1, "the request has to reach the handler");
      } else {
        assert.equal(handled, before, "a refused request must not reach the handler");
        assert.equal(JSON.parse(await response.text()).code, vector.reason);
      }
    } finally {
      mock.timers.reset();
    }
  });
}

test("the client signs the body it sends", async () => {
  const vector = vectors.signing.find((v: { name: string }) => v.name === "connect-json");
  const fields = vectors.message.fields;

  // Built before the clock is faked: `new Date(...)` is what the fake replaces.
  const request = create(CreatePaymentInstructionsRequestSchema, {
    paymentIntentId: BigInt(fields.paymentIntentId),
    acquirerId: BigInt(fields.acquirerId),
    amountUsdt: create(DecimalSchema, {
      unscaled: BigInt(fields.amountUsdt.unscaled),
      exponent: fields.amountUsdt.exponent,
    }),
    expiresAt: timestampFromDate(new Date(fields.expiresAt)),
  });

  let captured: { body: Buffer; headers: http.IncomingHttpHeaders } | undefined;
  const capture = http.createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on("data", (chunk: Buffer) => chunks.push(chunk));
    req.on("end", () => {
      captured = { body: Buffer.concat(chunks), headers: req.headers };
      res.writeHead(200, { "content-type": "application/json" });
      res.end("{}");
    });
  });
  await new Promise<void>((resolve) => capture.listen(0, resolve));

  try {
    mock.timers.enable({ apis: ["Date"], now: vector.timestampMs });
    const t0 = createUsdtPayClient(
      `http://127.0.0.1:${(capture.address() as AddressInfo).port}`,
      network.privateKey,
      IssuerCallbackService,
    );
    await t0.createPaymentInstructions(request);
  } finally {
    mock.timers.reset();
    capture.close();
  }

  assert.ok(captured);
  assert.equal(
    "0x" + captured.body.toString("hex"),
    vector.payload,
    "the client serialized this request differently than the vectors did — regenerate them",
  );
  // The signature is the vector's without its recovery byte: this client emits 64 bytes.
  assert.equal(captured.headers["x-signature"], vector.signature.slice(0, 130));
  assert.equal(captured.headers["x-public-key"], network.publicKey);
  assert.equal(captured.headers["x-signature-timestamp"], String(vector.timestampMs));
});
