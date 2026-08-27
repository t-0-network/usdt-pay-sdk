import assert from "node:assert/strict";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { after, test } from "node:test";
import { create, fromBinary, fromJson, toBinary, toJson } from "@bufbuild/protobuf";
import { Code, ConnectError } from "@connectrpc/connect";
import {
  createRequestVerifier,
  NetworkHeaders,
  rejectRequest,
  type VerifyRequestFailure,
} from "../src/crypto.js";
import {
  createUsdtPayClient,
  CreatePaymentInstructionsRequestSchema,
  CreatePaymentInstructionsResponse_Failure_Reason,
  CreatePaymentInstructionsResponseSchema,
  IssuerCallbackService,
  publicKeyFromPrivateKey,
} from "../src/index.js";

const NETWORK_PRIVATE_KEY = "0x" + "11".repeat(32);

// The framework-agnostic integration the README documents, verbatim minus the
// framework: a raw http server that verifies the exact wire bytes itself, answers
// failures with rejectRequest, and speaks binary protobuf — what an Effect or
// Hono integrator builds. The signed SDK client is the caller, so this proves the
// verifier and the client agree on the digest (body bytes + timestamp).
const verifyRequest = createRequestVerifier({
  networkPublicKey: publicKeyFromPrivateKey(NETWORK_PRIVATE_KEY),
});

const server = http.createServer(async (req, res) => {
  try {
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(chunk as Buffer);
    const rawBody = new Uint8Array(Buffer.concat(chunks));

    // NetworkHeaders values are title-case; Node lowercases incoming headers.
    const result = verifyRequest({
      body: rawBody,
      signatureHeader: String(req.headers[NetworkHeaders.Signature.toLowerCase()] ?? ""),
      publicKeyHeader: String(req.headers[NetworkHeaders.PublicKey.toLowerCase()] ?? ""),
      timestampHeader: String(req.headers[NetworkHeaders.SignatureTimestamp.toLowerCase()] ?? ""),
    });
    if (!result.valid) {
      const err = rejectRequest(result.reason);
      res.writeHead(err.status, err.headers);
      res.end(err.body);
      return;
    }

    // Connect unary bodies come in the format the Content-Type names — JSON or
    // binary protobuf — and the response must answer in the same one. The bytes
    // were verified above either way; the format only decides how to parse them.
    const isJson = (req.headers["content-type"] ?? "").includes("json");
    const request = isJson
      ? fromJson(CreatePaymentInstructionsRequestSchema, JSON.parse(Buffer.from(rawBody).toString("utf8")))
      : fromBinary(CreatePaymentInstructionsRequestSchema, rawBody);
    // Answer from a real field-read of the verified bytes, so the test proves they
    // deserialized into the message that was sent: intent 42 gets a different
    // decline than anything else.
    const response = create(CreatePaymentInstructionsResponseSchema, {
      result: {
        case: "failure",
        value: {
          reason:
            request.paymentIntentId === 42n
              ? CreatePaymentInstructionsResponse_Failure_Reason.AMOUNT_OUT_OF_RANGE
              : CreatePaymentInstructionsResponse_Failure_Reason.ISSUER_UNAVAILABLE,
        },
      },
    });
    if (isJson) {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify(toJson(CreatePaymentInstructionsResponseSchema, response)));
    } else {
      res.writeHead(200, { "content-type": "application/proto" });
      res.end(Buffer.from(toBinary(CreatePaymentInstructionsResponseSchema, response)));
    }
  } catch (e) {
    // A crash must answer, not hang the caller.
    res.writeHead(500, { "content-type": "application/json" });
    res.end(JSON.stringify({ code: "internal", message: String(e) }));
  }
});
await new Promise<void>((resolve) => server.listen(0, resolve));
after(() => {
  server.close();
  server.closeAllConnections();
});
const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;

test("a signed SDK call passes createRequestVerifier and round-trips the protobuf body", async () => {
  const t0 = createUsdtPayClient(base, NETWORK_PRIVATE_KEY, IssuerCallbackService);
  const response = await t0.createPaymentInstructions({ paymentIntentId: 42n });
  assert.equal(response.result.case, "failure");
  assert.equal(
    response.result.case === "failure" ? response.result.value.reason : undefined,
    CreatePaymentInstructionsResponse_Failure_Reason.AMOUNT_OUT_OF_RANGE,
  );
});

test("a call signed with the wrong key gets rejectRequest's unauthenticated answer", async () => {
  const impostor = createUsdtPayClient(base, "0x" + "22".repeat(32), IssuerCallbackService);
  await assert.rejects(
    impostor.createPaymentInstructions({ paymentIntentId: 42n }),
    (err: unknown) => err instanceof ConnectError && err.code === Code.Unauthenticated,
  );
});

test("rejectRequest maps every documented failure to the transport's status codes", () => {
  const expected: Record<VerifyRequestFailure, number> = {
    invalid_timestamp: 400,
    timestamp_out_of_range: 400,
    invalid_public_key: 400,
    unknown_public_key: 401,
    invalid_signature_format: 400,
    signature_failed: 401,
  };
  for (const [reason, status] of Object.entries(expected)) {
    const err = rejectRequest(reason as VerifyRequestFailure);
    assert.equal(err.status, status, reason);
    assert.equal(err.headers["Content-Type"], "application/json");
    const body = JSON.parse(err.body) as { code: string; message: string };
    assert.equal(body.code, status === 401 ? "unauthenticated" : "invalid_argument");
    assert.ok(body.message.length > 0);
  }
});

test("rejectRequest denies rather than crashes on a reason it has never heard of", () => {
  // VerifyRequestFailure is an open union: a newer provider-sdk may add reasons.
  const err = rejectRequest("some_future_reason" as VerifyRequestFailure);
  assert.equal(err.status, 401);
  assert.equal((JSON.parse(err.body) as { code: string }).code, "unauthenticated");
});
