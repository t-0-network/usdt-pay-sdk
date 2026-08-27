# usdt-pay-sdk

SDKs and starter projects for the **t-0 QR payment flow** — the protocol under
`proto/tzero/v1/pay/`.

## Which role are you?

Three companies meet in this flow, and each integrates its own edge of it.
Pick yours and go straight to its SDK.

| You are | You do | SDK |
|---|---|---|
| **Acquirer** | Own the merchant. Price the sale, open the intent, show the QR, learn when it settles. | [Java](java/) |
| **Issuer** | Reserve deposit addresses, watch the chain for the customer's USDt, settle on-chain. | [Node](node/) |
| **Liquidity Provider** | Price USDt↔local fiat, take the per-sale obligation, settle fiat over bank rails. | Not yet published |

Not sure which you are? The acquirer talks to the POS, the issuer talks to the
blockchain, the LP talks to a bank. t-0 sits in the middle and no two of you ever
talk directly.

The LP only exists in **fiat settlement mode**. An acquirer settled in USDt has no
LP, gets its USDt straight from the issuer, and skips `GetPaymentQuote`,
`SettlementInitiated` and `SettlementReceived` entirely. Your mode is fixed at
onboarding.

## Quick start

Scaffold a project with one command:

**Java (acquirer):**

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh -s -- init --lang=java --role=acquirer my-acquirer
```

**Node (issuer):**

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh -s -- init --lang=node --role=issuer my-issuer
```

Then follow your project's README.
See [java/](java/) or [node/](node/) for SDK-only usage without the scaffolder.

## What is in here

```
proto/tzero/v1/pay/      protocol definitions, snapshot-synced from the t-0 backend
java/                    Java SDK + acquirer starter
node/                    Node SDK + issuer starter
cli/                     unified scaffolder (Go) — `usdt-pay init`
```

Each directory has its own README:

- [`java/`](java/) — SDK coordinates, starter
- [`java/sdk/`](java/sdk/) — client patterns: blocking, non-blocking, V2 stubs
- [`java/starter/acquirer/`](java/starter/acquirer/) — acquirer integration guide
- [`node/`](node/) — SDK install, starter
- [`node/sdk/`](node/sdk/) — `createClient`, `createServer`,
  `createHandler` for mounting into an existing server, and
  `@t-0/usdt-pay-sdk/crypto` for verifying requests in any HTTP stack
- [`node/starter/issuer/`](node/starter/issuer/) — issuer integration guide

## Before you write code

Two things bite every integration, in both directions:

**Callbacks are delivered at least once.** t-0 retries until you acknowledge, and
your acknowledgment means *"recorded, stop retrying"*. A handler that returns
success without a durable write throws the event away — nothing redelivers it.
Write first under the callback's dedup key, return second.

**Every state-changing call has an idempotency key.** Retry with the *original* key
and identical content. A rejection is itself an acknowledgment — stop retrying —
but it never consumes the key: correct the fields and resend the same key. A fresh
key on a retry is a second sale, a second settlement, a second obligation.

Each starter README lists the keys for its role, and
[Idempotency & reliability](https://usdt-pay-docs.t-0.network/docs/integration-guidance/idempotency/)
states the rules in both directions. The full contract — every field, every decline
code — is the API reference:
[acquirer](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_acquirer/) ·
[issuer](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_issuer/) ·
[LP](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_lp/) ·
[shared types](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_common/).

## Documentation

- Per-role integration guides: the README in each starter directory.
- Full documentation: <https://usdt-pay-docs.t-0.network/docs/introduction/> — including
  the field-level [API reference](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/),
  [request authentication](https://usdt-pay-docs.t-0.network/docs/integration-guidance/protocol/authentication/)
  and [idempotency](https://usdt-pay-docs.t-0.network/docs/integration-guidance/idempotency/).

## License

MIT — see [LICENSE](LICENSE).
