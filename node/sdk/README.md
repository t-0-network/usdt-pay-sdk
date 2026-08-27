# @t-0/usdt-pay-sdk

TypeScript SDK for the **t-0 QR payment flow** — the contract under
`proto/tzero/v1/pay/`. Generated Connect clients and servers for all three roles,
over a transport that signs what you send and verifies what arrives.

Start from a role's starter rather than from here:
[`node/starter/issuer`](https://github.com/t-0-network/usdt-pay-sdk/tree/master/node/starter/issuer).

## Install

```bash
npm install @t-0/usdt-pay-sdk
```

Scaffolding a project with
[`@t-0/usdt-pay-starter-ts`](https://www.npmjs.com/package/@t-0/usdt-pay-starter-ts)
adds this dependency for you. Working inside a clone of the repo instead? The starters
resolve the SDK through the `node/` npm workspace, so no install is needed.

## Serving the callbacks t-0 pushes to you

```ts
import { createServer, IssuerCallbackService } from "@t-0/usdt-pay-sdk";

const server = await createServer(8080, process.env.NETWORK_PUBLIC_KEY!, (r) => {
  r.service(IssuerCallbackService, issuerCallbackHandler);
});
```

Every inbound request is verified against t-0's public key before it reaches your
handler, and every response is validated against the contract's `buf.validate`
constraints on the way out. Verification runs over the bytes that arrived — protobuf
encoding is not canonical, so a re-serialized message is a different message to
secp256k1, and `createServer` wires the raw-body hasher in for you.

The returned value is a listening `http.Server`: `close()` it to shut down, and read
`address()` when you passed port 0.

Mount one service per role edge you implement — `IssuerCallbackService`,
`AcquirerCallbackService`, `LpCallbackService`.

## Mounting into a server you already run

`createHandler` is the same thing as `createServer` minus the
`http.Server`: a plain `(req, res)` handler you mount wherever you want —
Express, Fastify (via its middleware bridge), or a raw `http` server you
multiplex yourself.

```ts
import express from "express";
import { createHandler, IssuerCallbackService } from "@t-0/usdt-pay-sdk";

const app = express();

// Mount BEFORE any body parser — the handler must see the raw bytes.
app.use(
  "/sda/payments/t0",
  createHandler(process.env.NETWORK_PUBLIC_KEY!, (r) => {
    r.service(IssuerCallbackService, issuerCallbackHandler);
  }),
);

app.use(express.json()); // parsers for the rest of the app go after
app.listen(3000);
```

Two constraints, both about bytes:

- **Mount it before anything that touches the body.** The signature is verified
  against the raw bytes as they stream in; `express.json()` and friends consume
  the stream, and anything that decompresses changes the bytes. Order the
  middleware so the handler is first.
- **Prefix mounting works because Express strips the prefix** from `req.url`
  before the handler routes on the Connect path
  (`/<package>.<Service>/<Method>`). A framework that passes the full URL
  through needs the handler at the root, or the prefix removed first.

The health service t-0 probes (`grpc.health.v1.Health/Check`) is inside the
handler, behind the same signature check — mounting the handler mounts it.

## Verifying requests without the SDK's server

For a stack that cannot hand Node's `(req, res)` pair to a handler at all —
Effect, Hono, Koa, anything that owns its own HTTP layer —
`@t-0/usdt-pay-sdk/crypto` exports the verification the handler uses, for
wiring up yourself: `createRequestVerifier` (setup-time config → per-request
verifier), `rejectRequest` (failure → the HTTP error to send), the
`NetworkHeaders` enum, and the primitives under them (`verifySignature`,
`computeDigest`, `keccak256`, `parsePublicKey`, `publicKeysEqual`).

Two imports: verification from `./crypto`, proto message schemas from the
package root:

```ts
import { fromBinary, fromJson } from "@bufbuild/protobuf";
import {
  createRequestVerifier,
  NetworkHeaders,
  rejectRequest,
} from "@t-0/usdt-pay-sdk/crypto";
import {
  CreatePaymentInstructionsRequestSchema,
  CreatePaymentInstructionsResponseSchema,
} from "@t-0/usdt-pay-sdk";

const verifyRequest = createRequestVerifier({
  networkPublicKey: T0_NETWORK_PUBLIC_KEY, // "0x04..." uncompressed secp256k1
});

// In your framework's handler:
const rawBody = new Uint8Array(await request.arrayBuffer()); // the exact wire bytes

const result = verifyRequest({
  body: rawBody, // Uint8Array — not the bare ArrayBuffer
  signatureHeader: headers[NetworkHeaders.Signature.toLowerCase()],
  publicKeyHeader: headers[NetworkHeaders.PublicKey.toLowerCase()],
  timestampHeader: headers[NetworkHeaders.SignatureTimestamp.toLowerCase()],
});

if (!result.valid) {
  const err = rejectRequest(result.reason);
  return respond(err.body, err.status, err.headers);
}
```

**Verify the exact bytes that arrived.** No body parsers, no auto-decompression,
never re-serialized protobuf — protobuf encoding is not canonical, so a
re-encoded message is a different message to secp256k1. This is the rule the
rest of the SDK exists to enforce for you; here it is yours to keep.

What the working parts around the verifier look like:

- **Headers**: `NetworkHeaders` values are title-case (`X-Signature`); Node and
  most frameworks hand you incoming headers lowercased. Look them up by
  `NetworkHeaders.Signature.toLowerCase()`, as above.
- **Body format follows `Content-Type`.** A Connect unary body is JSON
  (`application/json` → `fromJson`) or binary protobuf (`application/proto` →
  `fromBinary`), and the response answers in the same format with the same
  `Content-Type`. Handle both — this SDK's own client sends JSON, and other
  callers may send proto.
- **Errors go through `rejectRequest(result.reason)`.** It returns the status,
  headers and serialized body of the Connect error the caller expects — the
  same statuses the SDK's transport answers for the same failures. Send it
  verbatim. `VerifyRequestFailure` is an open union: reasons this SDK version
  has never heard of map to 401, so an unknown failure still denies.
- **Route the health check too.** t-0 probes `/grpc.health.v1.Health/Check` as
  part of the signed contract; a standalone integration must answer it. The
  wire contract is in
  [HEALTH_SERVICE.md](https://github.com/t-0-network/provider-sdk/blob/master/docs/HEALTH_SERVICE.md).

`node/sdk/test/crypto.test.ts` is this section as a running program — a raw
`http` server verifying, parsing and answering a signed SDK client, with
nothing from `createServer` in it.

## Calling t-0

```ts
import { createClient, IssuerService } from "@t-0/usdt-pay-sdk";

const t0 = createClient(process.env.TZERO_ENDPOINT!, privateKeyHex, IssuerService);
const response = await t0.paymentReceived(request, { timeoutMs: 10_000 });
```

All 15 endpoints are unary request/response — nothing in this contract streams.

`endpoint` is required: the underlying provider client defaults to a different t-0
API, and a pay participant that omitted it would sign perfectly valid requests and
send them to the wrong host.

Deadlines are per call, as `{ timeoutMs }`. A Connect timeout is a duration evaluated
when the call is made, so each call site picks its own — the starters give a
settlement report more room than the rest, because the transfer is already broadcast
by then and an answer is worth waiting for.

`signer` takes a hex private key, or a `SignerFunction` when the key lives in an HSM
or KMS and never reaches this process.

## The public key t-0 knows you by

```ts
import { publicKeyFromPrivateKey } from "@t-0/usdt-pay-sdk";

console.log(publicKeyFromPrivateKey(process.env.PRIVATE_KEY!));
```

Send it to the t-0 team — that is step 1 of every role's integration. Calling it at
startup also fails a malformed key there rather than on the first request.

## Generated code

`src/gen/` is checked in, so neither the starters nor a consumer needs `buf`
installed. Regenerate it after a proto sync:

```bash
npm run buf:generate
```

`buf.gen.yaml` pins the `protoc-gen-es` version; bump it and the `@bufbuild/protobuf`
range in `package.json` together, so generated code and runtime stay on one major.

## Build

```bash
npm install     # from node/
npm run build   # ESM to lib/esm, CommonJS to lib/cjs
```

The package exports both, so it works from an `import` and from a `require`.
