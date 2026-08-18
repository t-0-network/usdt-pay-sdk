# @t-0/usdt-pay-sdk

TypeScript SDK for the **t-0 QR payment flow** — the contract under
`proto/tzero/v1/pay/`. Generated Connect clients and servers for all three roles,
over a transport that signs what you send and verifies what arrives.

Start from a role's starter rather than from here: [`node/starter/issuer`](../starter/issuer).

## Install

```bash
npm install @t-0/usdt-pay-sdk
```

Before 1.0 the package is not published. Clone this repository and let the starters
resolve it through the `node/` npm workspace, which is what they are set up to do.

## Serving the callbacks t-0 pushes to you

```ts
import { createUsdtPayServer, IssuerCallbackService } from "@t-0/usdt-pay-sdk";

const server = await createUsdtPayServer(8080, process.env.NETWORK_PUBLIC_KEY!, (r) => {
  r.service(IssuerCallbackService, issuerCallbackHandler);
});
```

Every inbound request is verified against t-0's public key before it reaches your
handler, and every response is validated against the contract's `buf.validate`
constraints on the way out. Verification runs over the bytes that arrived — protobuf
encoding is not canonical, so a re-serialized message is a different message to
secp256k1, and `createUsdtPayServer` wires the raw-body hasher in for you.

The returned value is a listening `http.Server`: `close()` it to shut down, and read
`address()` when you passed port 0.

Mount one service per role edge you implement — `IssuerCallbackService`,
`AcquirerCallbackService`, `LpCallbackService`.

## Calling t-0

```ts
import { createUsdtPayClient, IssuerService } from "@t-0/usdt-pay-sdk";

const t0 = createUsdtPayClient(process.env.TZERO_ENDPOINT!, privateKeyHex, IssuerService);
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
