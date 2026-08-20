# Node

TypeScript SDK and starter for the **t-0 QR payment flow**.

## SDK

```bash
npm install @t-0/usdt-pay-sdk
```

Create a client, make a call:

```ts
import { createUsdtPayClient, IssuerService } from "@t-0/usdt-pay-sdk";

const t0 = createUsdtPayClient(endpoint, privateKeyHex, IssuerService);
const response = await t0.paymentReceived(request, { timeoutMs: 10_000 });
```

Serve callbacks:

```ts
import { createUsdtPayServer, IssuerCallbackService } from "@t-0/usdt-pay-sdk";

const server = await createUsdtPayServer(8080, networkPublicKey, (r) => {
  r.service(IssuerCallbackService, issuerCallbackHandler);
});
```

See [sdk/](sdk/) for `createUsdtPayClient` and `createUsdtPayServer` in full.

## Starter

Node 22 or newer.

```bash
npx @t-0/usdt-pay-starter-ts my-issuer issuer
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
See [cli/](cli/) for scaffolder details.
