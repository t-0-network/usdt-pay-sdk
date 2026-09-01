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

Scaffolding a project with the CLI (`usdt-pay init`) adds this dependency for you. Working inside a clone of the repo instead? The starters resolve the SDK through
the `node/` npm workspace, so no install is needed.

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

### Standalone Request Decoding

For frameworks that don't use Node's `http.createServer` (Hono, Effect, Koa, Fastify, etc.), use `createRequestDecoder` for one-call signature verification + Content-Type-aware decoding + protovalidation. It returns an either-type result: success with the decoded message and a response encoder, or failure with a ready-to-send HTTP error.

```ts
import { createRequestDecoder } from "@t-0/usdt-pay-sdk/crypto";
import { CreatePaymentInstructionsRequestSchema, CreatePaymentInstructionsResponseSchema } from "@t-0/usdt-pay-sdk";

const decode = createRequestDecoder({
  networkPublicKey: process.env.NETWORK_PUBLIC_KEY!,
});

// Hono / fetch-shaped framework — route by Connect procedure path:
app.post("/tzero.v1.pay.issuer.IssuerCallback/:method", async (c) => {
  const body = new Uint8Array(await c.req.arrayBuffer());
  const result = decode(CreatePaymentInstructionsRequestSchema, { body, headers: c.req.raw.headers });

  if (!result.ok) {
    return new Response(result.error.body, {
      status: result.error.status,
      headers: result.error.headers,
    });
  }

  const response = await handleRequest(result.request);

  // encodeResponse validates + encodes in the matching wire format (JSON or proto)
  const wire = result.encodeResponse(CreatePaymentInstructionsResponseSchema, response);
  return new Response(wire.body, { status: wire.status, headers: wire.headers });
});
```

```ts
// Raw Node http example:
import http from "node:http";
import { createRequestDecoder } from "@t-0/usdt-pay-sdk/crypto";
import { CreatePaymentInstructionsRequestSchema, CreatePaymentInstructionsResponseSchema } from "@t-0/usdt-pay-sdk";

const decode = createRequestDecoder({
  networkPublicKey: process.env.NETWORK_PUBLIC_KEY!,
});

http.createServer((req, res) => {
  const chunks: Buffer[] = [];
  req.on("data", (c) => chunks.push(c));
  req.on("end", async () => {
    const body = Buffer.concat(chunks);
    const result = decode(CreatePaymentInstructionsRequestSchema, { body, headers: req.headers });

    if (!result.ok) {
      res.writeHead(result.error.status, result.error.headers);
      res.end(result.error.body);
      return;
    }

    const response = await handleRequest(result.request);
    const wire = result.encodeResponse(CreatePaymentInstructionsResponseSchema, response);
    res.writeHead(wire.status, wire.headers);
    res.end(wire.body);
  });
}).listen(3000);
```

The decoder accepts both fetch `Headers` and Node's `Record<string, string | string[] | undefined>`. It normalizes header case internally, detects Content-Type (`application/json` or `application/proto`), and the returned `encodeResponse` closure responds in the matching format.

**Important constraints for standalone integrations:**

- **Raw body bytes only.** Pass the exact wire bytes — no body parsers, no auto-decompression, never re-serialized protobuf. Protobuf encoding is not canonical; re-encoding produces different bytes and breaks verification.
- **Health endpoint.** t-0 probes `/grpc.health.v1.Health/Check` on every endpoint. The probe is signed. Standalone integrations must route this path and return a valid health response. See [HEALTH_SERVICE.md](https://github.com/t-0-network/provider-sdk/blob/master/docs/HEALTH_SERVICE.md) for the wire contract.
- **`DecodeRequestFailure` is an open union.** New error shapes may be added without a major version bump. Handle unknown failures as generic errors.

<details>
<summary>Lower-level primitives</summary>

The individual building blocks are also exported: `createRequestVerifier`, `rejectRequest`, `verifySignature`, `computeDigest`, `keccak256`, `parsePublicKey`, `publicKeysEqual`. You can import them from the `./crypto` subpath: `import { createRequestVerifier } from "@t-0/usdt-pay-sdk/crypto"`.
</details>

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

console.log(publicKeyFromPrivateKey(process.env.PROVIDER_PRIVATE_KEY!));
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
