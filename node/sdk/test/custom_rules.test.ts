import assert from "node:assert/strict";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { after, test } from "node:test";
import { create } from "@bufbuild/protobuf";
import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import { secp256k1 } from "@noble/curves/secp256k1.js";
import {
  AcquirerCallbackService,
  Blockchain,
  Code,
  ConnectError,
  computeDigest,
  createClient,
  createHandler,
  createRequestDecoder,
  createServer,
  CreatePaymentInstructionsRequestSchema,
  CreatePaymentInstructionsResponseSchema,
  DecimalSchema,
  IssuerCallbackService,
  payRegistry,
  publicKeyFromPrivateKey,
  validate,
} from "../src/index.js";

// The pay contract's custom rules (valid_address, valid_tx_hash) are proto2 extensions
// in this SDK's own files, so they resolve only through payRegistry. These tests make
// each of them fire through every way a response or request is checked.

// The test plays t-0: it signs with the key the server is told to trust.
const NETWORK_PRIVATE_KEY = "0x" + "11".repeat(32);
const NETWORK_PUBLIC_KEY = publicKeyFromPrivateKey(NETWORK_PRIVATE_KEY);

const BAD_RESPONSE_MESSAGE =
  "response validation failed: success.deposit_options[0].deposit_address: must be 34-42 characters";

const inAnHour = () => timestampFromDate(new Date(Date.now() + 3_600_000));

// Breaks exactly one rule: deposit_address is too short.
const badInstructions = () =>
  create(CreatePaymentInstructionsResponseSchema, {
    result: {
      case: "success",
      value: {
        expiresAt: inAnHour(),
        depositOptions: [
          { chain: Blockchain.ETH, depositAddress: "bad", tokenContract: "0x" + "bb".repeat(20) },
        ],
      },
    },
  });

const validInstructionsRequest = () => ({
  paymentIntentId: 1n,
  acquirerId: 2n,
  amountUsdt: create(DecimalSchema, { unscaled: 1000n, exponent: -2 }),
  expiresAt: inAnHour(),
});

// Breaks exactly one rule: on_chain_tx_hash is not 64 hex characters.
const badAuthorized = () => ({
  paymentIntentId: 1n,
  paymentRef: "order-1",
  paymentMethod: {
    case: "usdtOnChain" as const,
    value: { chain: Blockchain.ETH, onChainTxHash: "0xabc", senderAddress: "0x" + "aa".repeat(20) },
  },
  approvedAt: timestampFromDate(new Date()),
  settlementAmount: create(DecimalSchema, { unscaled: 1000n, exponent: -2 }),
  receivedAt: timestampFromDate(new Date()),
});

const servers: http.Server[] = [];
after(() => {
  for (const s of servers) {
    s.close();
    s.closeAllConnections();
  }
});

async function listen(server: http.Server): Promise<string> {
  servers.push(server);
  if (!server.listening) {
    await new Promise<void>((resolve) => server.listen(0, resolve));
  }
  return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

function isConnectError(code: Code, message: string) {
  return (err: unknown) => {
    assert.ok(err instanceof ConnectError, `expected a ConnectError, got ${String(err)}`);
    assert.equal(err.code, code);
    assert.equal(err.rawMessage, message);
    return true;
  };
}

test("createServer refuses a response that breaks a custom rule", async () => {
  const server = await createServer(0, NETWORK_PUBLIC_KEY, (r) => {
    r.service(IssuerCallbackService, { createPaymentInstructions: () => badInstructions() });
  });
  const t0 = createClient(await listen(server), NETWORK_PRIVATE_KEY, IssuerCallbackService);

  await assert.rejects(
    t0.createPaymentInstructions(validInstructionsRequest()),
    isConnectError(Code.Internal, BAD_RESPONSE_MESSAGE),
  );
});

test("createHandler refuses a response that breaks a custom rule", async () => {
  const handler = createHandler(NETWORK_PUBLIC_KEY, (r) => {
    r.service(IssuerCallbackService, { createPaymentInstructions: () => badInstructions() });
  });
  const t0 = createClient(await listen(http.createServer(handler)), NETWORK_PRIVATE_KEY, IssuerCallbackService);

  await assert.rejects(
    t0.createPaymentInstructions(validInstructionsRequest()),
    isConnectError(Code.Internal, BAD_RESPONSE_MESSAGE),
  );
});

test("createHandler refuses a request that breaks a custom rule, before the handler runs", async () => {
  let calls = 0;
  const handler = createHandler(NETWORK_PUBLIC_KEY, (r) => {
    r.service(AcquirerCallbackService, {
      paymentAuthorized: () => {
        calls++;
        return {};
      },
      settlementInitiated: () => ({}),
      settlementCompleted: () => ({}),
      paymentExpired: () => ({}),
      paymentFailed: () => ({}),
    });
  });
  const t0 = createClient(await listen(http.createServer(handler)), NETWORK_PRIVATE_KEY, AcquirerCallbackService);

  await assert.rejects(
    t0.paymentAuthorized(badAuthorized()),
    isConnectError(
      Code.InvalidArgument,
      "usdt_on_chain.on_chain_tx_hash: must be 64 hex characters, optionally 0x-prefixed [string.valid_tx_hash]",
    ),
  );
  assert.equal(calls, 0);
});

test("the request decoder's encodeResponse refuses a response that breaks a custom rule", () => {
  const decode = createRequestDecoder({ networkPublicKey: NETWORK_PUBLIC_KEY });
  const body = new TextEncoder().encode(
    JSON.stringify({ paymentIntentId: "1", acquirerId: "2", amountUsdt: { unscaled: "1000", exponent: -2 } }),
  );
  const ts = Date.now();
  const privateKey = Buffer.from(NETWORK_PRIVATE_KEY.slice(2), "hex");
  const signature = secp256k1.sign(computeDigest(body, ts), privateKey, { prehash: false });
  const result = decode(CreatePaymentInstructionsRequestSchema, {
    body,
    headers: {
      "content-type": "application/json",
      "x-public-key": NETWORK_PUBLIC_KEY,
      "x-signature": "0x" + Buffer.from(signature).toString("hex"),
      "x-signature-timestamp": String(ts),
    },
  });
  assert.equal(result.ok, true);
  if (!result.ok) return;

  const wire = result.encodeResponse(CreatePaymentInstructionsResponseSchema, badInstructions());
  assert.equal(wire.status, 500);
  assert.deepEqual(JSON.parse(wire.body as string), {
    code: "internal",
    message: BAD_RESPONSE_MESSAGE,
    violations: [
      {
        field: "success.deposit_options[0].deposit_address",
        message: "must be 34-42 characters",
        ruleId: "string.valid_address",
      },
    ],
  });
});

test("validate with payRegistry fires a custom rule", () => {
  assert.throws(
    () => validate(CreatePaymentInstructionsResponseSchema, badInstructions(), { registry: payRegistry }),
    isConnectError(Code.Internal, BAD_RESPONSE_MESSAGE),
  );
});

test("without payRegistry, validate cannot resolve the custom rules even for a valid response", () => {
  const valid = create(CreatePaymentInstructionsResponseSchema, {
    result: {
      case: "success",
      value: {
        expiresAt: inAnHour(),
        depositOptions: [
          { chain: Blockchain.ETH, depositAddress: "0x" + "aa".repeat(20), tokenContract: "0x" + "bb".repeat(20) },
        ],
      },
    },
  });
  assert.doesNotThrow(() => validate(CreatePaymentInstructionsResponseSchema, valid, { registry: payRegistry }));
  assert.throws(
    () => validate(CreatePaymentInstructionsResponseSchema, valid),
    (err: unknown) =>
      err instanceof ConnectError &&
      err.code === Code.Internal &&
      err.rawMessage.startsWith("response validation error: Unknown extension"),
  );
});
