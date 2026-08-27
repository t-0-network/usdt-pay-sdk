# Node

TypeScript SDK and starter for the **t-0 QR payment flow**.

## SDK

```bash
npm install @t-0/usdt-pay-sdk
```

Create a client, make a call:

```ts
import { createClient, IssuerService } from "@t-0/usdt-pay-sdk";

const t0 = createClient(endpoint, privateKeyHex, IssuerService);
const response = await t0.paymentReceived(request, { timeoutMs: 10_000 });
```

Serve callbacks:

```ts
import { createServer, IssuerCallbackService } from "@t-0/usdt-pay-sdk";

const server = await createServer(8080, networkPublicKey, (r) => {
  r.service(IssuerCallbackService, issuerCallbackHandler);
});
```

See [sdk/](sdk/) for `createClient`, `createServer` and `createHandler` in full.

## Starter

Node 22 or newer. Scaffold with the [CLI](../cli/):

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh -s -- init --lang=node --role=issuer my-issuer
```

| Role | Template |
|---|---|
| `issuer` | [starter/issuer/](starter/issuer/) |

After scaffolding:

```bash
cd my-issuer
# add NETWORK_PUBLIC_KEY to .env — your t-0 onboarding contact gives you this
npm install && npm run build
npm start
```

Then follow your project's README.
