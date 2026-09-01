import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { after, describe, test } from "node:test";
import { create, fromBinary, fromJson, toBinary, toJson, toJsonString } from "@bufbuild/protobuf";
import { secp256k1 } from "@noble/curves/secp256k1.js";
import { Code, ConnectError } from "@connectrpc/connect";
import {
  computeDigest,
  createRequestDecoder,
  createRequestVerifier,
  NetworkHeaders,
  rejectRequest,
  type VerifyRequestFailure,
} from "../src/crypto.js";
import {
  Blockchain,
  createClient,
  CreatePaymentInstructionsRequestSchema,
  CreatePaymentInstructionsResponse_Failure_Reason,
  CreatePaymentInstructionsResponseSchema,
  CreatePaymentInstructionsResponse_SuccessSchema,
  DecimalSchema,
  IssuerCallbackService,
  payRegistry,
  publicKeyFromPrivateKey,
  QrOptionSchema,
  UsdtOnChainPaymentSchema,
} from "../src/index.js";
import { TimestampSchema } from "@bufbuild/protobuf/wkt";

const NETWORK_PRIVATE_KEY = "0x" + "11".repeat(32);

// Lower-level createRequestVerifier path: a raw http server that verifies the
// exact wire bytes itself, answers failures with rejectRequest, and handles
// content-type dispatch manually. The signed SDK client is the caller, so this
// proves the verifier and the client agree on the digest (body bytes + timestamp).
// For the one-call createRequestDecoder path, see the describe block below.
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
  const t0 = createClient(base, NETWORK_PRIVATE_KEY, IssuerCallbackService);
  const response = await t0.createPaymentInstructions({ paymentIntentId: 42n });
  assert.equal(response.result.case, "failure");
  assert.equal(
    response.result.case === "failure" ? response.result.value.reason : undefined,
    CreatePaymentInstructionsResponse_Failure_Reason.AMOUNT_OUT_OF_RANGE,
  );
});

test("a call signed with the wrong key gets rejectRequest's unauthenticated answer", async () => {
  const impostor = createClient(base, "0x" + "22".repeat(32), IssuerCallbackService);
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

// ---------------------------------------------------------------------------
// createRequestDecoder — one-call decode with payRegistry baked in
// ---------------------------------------------------------------------------

function newKeypair() {
  const priv = Uint8Array.from(randomBytes(32));
  const pub = secp256k1.getPublicKey(priv, false);
  return { priv, publicKeyHex: "0x" + Buffer.from(pub).toString("hex") };
}

function sign(body: Uint8Array, priv: Uint8Array) {
  const ts = Date.now();
  const digest = computeDigest(body, ts);
  const sig = secp256k1.sign(digest, priv, { prehash: false });
  return {
    "x-signature": "0x" + Buffer.from(sig).toString("hex"),
    "x-public-key": "0x" + Buffer.from(secp256k1.getPublicKey(priv, false)).toString("hex"),
    "x-signature-timestamp": String(ts),
  };
}

describe("createRequestDecoder", () => {
  const futureTimestamp = create(TimestampSchema, {
    seconds: BigInt(Math.floor(Date.now() / 1000) + 3600),
  });

  const validRequest = {
    paymentIntentId: 42n,
    acquirerId: 1n,
    amountUsdt: create(DecimalSchema, { unscaled: 1000n, exponent: -2 }),
    expiresAt: futureTimestamp,
  };

  test("JSON decode + encode round-trip", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(CreatePaymentInstructionsRequestSchema, validRequest);
    const jsonBody = new TextEncoder().encode(
      toJsonString(CreatePaymentInstructionsRequestSchema, msg, { registry: payRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), "content-type": "application/json" };

    const result = decode(CreatePaymentInstructionsRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.format, "json");
    assert.equal(result.request.paymentIntentId, 42n);

    const resp = create(CreatePaymentInstructionsResponseSchema, {
      result: {
        case: "failure",
        value: { reason: CreatePaymentInstructionsResponse_Failure_Reason.ISSUER_UNAVAILABLE },
      },
    });
    const wire = result.encodeResponse(CreatePaymentInstructionsResponseSchema, resp);
    assert.equal(wire.status, 200);
    assert.equal(wire.headers["Content-Type"], "application/json");
    assert.equal(typeof wire.body, "string");
  });

  test("binary proto decode + encode round-trip", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(CreatePaymentInstructionsRequestSchema, { ...validRequest, paymentIntentId: 99n });
    const protoBody = toBinary(CreatePaymentInstructionsRequestSchema, msg);
    const headers = { ...sign(protoBody, priv), "content-type": "application/proto" };

    const result = decode(CreatePaymentInstructionsRequestSchema, { body: protoBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;
    assert.equal(result.format, "proto");
    assert.equal(result.request.paymentIntentId, 99n);

    const resp = create(CreatePaymentInstructionsResponseSchema, {
      result: {
        case: "failure",
        value: { reason: CreatePaymentInstructionsResponse_Failure_Reason.AMOUNT_OUT_OF_RANGE },
      },
    });
    const wire = result.encodeResponse(CreatePaymentInstructionsResponseSchema, resp);
    assert.equal(wire.status, 200);
    assert.equal(wire.headers["Content-Type"], "application/proto");
    assert.ok(wire.body instanceof Uint8Array);
  });

  test("cross-schema encodeResponse", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(CreatePaymentInstructionsRequestSchema, validRequest);
    const jsonBody = new TextEncoder().encode(
      toJsonString(CreatePaymentInstructionsRequestSchema, msg, { registry: payRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), "content-type": "application/json" };

    const result = decode(CreatePaymentInstructionsRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;

    const decimal = create(DecimalSchema, { unscaled: 12345n, exponent: -2 });
    const wire = result.encodeResponse(DecimalSchema, decimal);
    assert.equal(wire.status, 200);
    assert.equal(wire.headers["Content-Type"], "application/json");
    const parsed = JSON.parse(wire.body as string);
    assert.equal(parsed.unscaled, "12345");
  });

  test("validation violations for invalid Decimal", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(DecimalSchema, { unscaled: 100n, exponent: 99 });
    const jsonBody = new TextEncoder().encode(
      toJsonString(DecimalSchema, msg, { registry: payRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), "content-type": "application/json" };

    const result = decode(DecimalSchema, { body: jsonBody, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 400);
    const parsed = JSON.parse(result.error.body as string);
    assert.equal(parsed.code, "invalid_argument");
    assert.ok(Array.isArray(parsed.violations));
    assert.ok(parsed.violations.length > 0);
  });

  test("custom predefined rules resolve through payRegistry (valid_tx_hash / valid_address)", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(UsdtOnChainPaymentSchema, {
      chain: Blockchain.TRON,
      onChainTxHash: "bad",
      senderAddress: "bad",
    });
    const jsonBody = new TextEncoder().encode(
      toJsonString(UsdtOnChainPaymentSchema, msg, { registry: payRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), "content-type": "application/json" };

    const result = decode(UsdtOnChainPaymentSchema, { body: jsonBody, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 400);
    const parsed = JSON.parse(result.error.body as string);
    assert.ok(parsed.violations.length >= 2);
    const fields = parsed.violations.map((v: { field: string }) => v.field);
    assert.ok(fields.some((f: string) => f.includes("on_chain_tx_hash")), "expected valid_tx_hash violation");
    assert.ok(fields.some((f: string) => f.includes("sender_address")), "expected valid_address violation");
  });

  test("bad signature → 401", () => {
    const { publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const body = new Uint8Array([1, 2, 3]);
    const headers = {
      "x-signature": "0x" + "00".repeat(64),
      "x-public-key": publicKeyHex,
      "x-signature-timestamp": String(Date.now()),
      "content-type": "application/json",
    };

    const result = decode(CreatePaymentInstructionsRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 401);
  });

  test("unsupported Content-Type → 415", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const body = new TextEncoder().encode("hello");
    const headers = { ...sign(body, priv), "content-type": "text/plain" };

    const result = decode(CreatePaymentInstructionsRequestSchema, { body, headers });
    assert.equal(result.ok, false);
    if (result.ok) return;
    assert.equal(result.error.status, 415);
  });

  test("encodeResponse with valid Success + QrOption through payRegistry → 200", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(CreatePaymentInstructionsRequestSchema, validRequest);
    const jsonBody = new TextEncoder().encode(
      toJsonString(CreatePaymentInstructionsRequestSchema, msg, { registry: payRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), "content-type": "application/json" };

    const result = decode(CreatePaymentInstructionsRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;

    const resp = create(CreatePaymentInstructionsResponseSchema, {
      result: {
        case: "success",
        value: create(CreatePaymentInstructionsResponse_SuccessSchema, {
          qrOptions: [
            create(QrOptionSchema, {
              chain: Blockchain.TRON,
              depositAddress: "TN2x2mHMRe8ufaM75sMnZGBfPGv7gM4jnk",
              renderablePayload: "usdt-tron:TN2x2mHMRe8ufaM75sMnZGBfPGv7gM4jnk?amount=10.00",
            }),
          ],
          expiresAt: futureTimestamp,
        }),
      },
    });
    const wire = result.encodeResponse(CreatePaymentInstructionsResponseSchema, resp);
    assert.equal(wire.status, 200, "valid Success with QrOption should encode as 200");
  });

  test("encodeResponse with invalid response (empty oneof) → 500", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(CreatePaymentInstructionsRequestSchema, validRequest);
    const jsonBody = new TextEncoder().encode(
      toJsonString(CreatePaymentInstructionsRequestSchema, msg, { registry: payRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), "content-type": "application/json" };

    const result = decode(CreatePaymentInstructionsRequestSchema, { body: jsonBody, headers });
    assert.equal(result.ok, true);
    if (!result.ok) return;

    const badResp = create(CreatePaymentInstructionsResponseSchema, {});
    const wire = result.encodeResponse(CreatePaymentInstructionsResponseSchema, badResp);
    assert.equal(wire.status, 500, "invalid response should produce 500");
    const parsed = JSON.parse(wire.body as string);
    assert.equal(parsed.code, "internal");
  });

  test("toleranceMs passthrough", async () => {
    const { priv, publicKeyHex } = newKeypair();

    const msg = create(CreatePaymentInstructionsRequestSchema, validRequest);
    const jsonBody = new TextEncoder().encode(
      toJsonString(CreatePaymentInstructionsRequestSchema, msg, { registry: payRegistry }),
    );
    const headers = { ...sign(jsonBody, priv), "content-type": "application/json" };

    // Wait for the signature to become stale relative to a 1ms tolerance
    await new Promise((r) => setTimeout(r, 50));

    const strictDecode = createRequestDecoder({ networkPublicKey: publicKeyHex, toleranceMs: 1 });
    const strictResult = strictDecode(CreatePaymentInstructionsRequestSchema, { body: jsonBody, headers });
    assert.equal(strictResult.ok, false, "1ms tolerance should reject a 50ms-old signature");

    const relaxedDecode = createRequestDecoder({ networkPublicKey: publicKeyHex, toleranceMs: 600_000 });
    const relaxedResult = relaxedDecode(CreatePaymentInstructionsRequestSchema, { body: jsonBody, headers });
    assert.equal(relaxedResult.ok, true, "600s tolerance should accept the same signature");
  });

  test("accepts fetch Headers object", () => {
    const { priv, publicKeyHex } = newKeypair();
    const decode = createRequestDecoder({ networkPublicKey: publicKeyHex });

    const msg = create(CreatePaymentInstructionsRequestSchema, validRequest);
    const jsonBody = new TextEncoder().encode(
      toJsonString(CreatePaymentInstructionsRequestSchema, msg, { registry: payRegistry }),
    );
    const rawHeaders = sign(jsonBody, priv);
    const fetchHeaders = new Headers({
      ...rawHeaders,
      "content-type": "application/json",
    });

    const result = decode(CreatePaymentInstructionsRequestSchema, { body: jsonBody, headers: fetchHeaders });
    assert.equal(result.ok, true, "fetch Headers should be accepted");
    if (!result.ok) return;
    assert.equal(result.request.paymentIntentId, 42n);
  });
});
