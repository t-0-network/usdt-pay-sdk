# usdt-pay-sdk

SDKs and starter projects for the **t-0 QR payment flow** — the protocol under
`proto/tzero/v1/pay/`.

> **0.x — the contract still moves.** Fields and endpoints can change without a
> deprecation window until 1.0. Pin what you build against and read the release
> notes before upgrading.

## Which role are you?

Three companies meet in this flow, and each integrates a different half of it.
Pick yours and go straight to its starter.

| You are | You do | Start here |
|---|---|---|
| **Acquirer** | Own the merchant. Price the sale, open the intent, show the QR, learn when it settles. | [`java/starter/acquirer`](java/starter/acquirer) |
| **Issuer** | Reserve deposit addresses, watch the chain for the customer's USDt, settle on-chain. | [`java/starter/issuer`](java/starter/issuer) |
| **Liquidity Provider** | Price USDt↔local fiat, take the per-sale obligation, settle fiat over bank rails. | [`java/starter/lp`](java/starter/lp) |

Not sure which you are? The acquirer talks to the POS, the issuer talks to the
blockchain, the LP talks to a bank. t-0 sits in the middle and no two of you ever
talk directly.

The LP only exists in **fiat settlement mode**. An acquirer settled in USDt has no
LP, gets its USDt straight from the issuer, and skips §3, §11 and §12 entirely.
Your mode is fixed at onboarding.

## Quick start

```bash
git clone git@github.com:t-0-network/usdt-pay-sdk.git
cd usdt-pay-sdk/java
./gradlew build                                  # SDK + all three starters

./gradlew :starter:acquirer:installDist

cd starter/acquirer
cp .env.example .env
# fill in PRIVATE_KEY (openssl rand -hex 32) and NETWORK_PUBLIC_KEY (from the t-0 team)

# Run from this directory — .env is read from the working directory.
./build/install/acquirer/bin/acquirer
```

Then work through that starter's README — it is a numbered path from "prints my
public key" to "settled a real sale".

## What is in here

```
proto/tzero/v1/          protocol definitions, snapshot-synced from the t-0 backend
└── pay/                 acquirer.proto, issuer.proto, lp.proto, types.proto
                         (self-contained: the pay contract shares no types with
                          tzero.v1.common, so your generated code carries exactly
                          one Decimal and one Blockchain)

java/                    Java 17 SDK + one starter project per role
├── sdk/                 network.t-0:usdt-pay-sdk-java — generated stubs for all three roles
└── starter/{acquirer,issuer,lp}
```

The Java stubs are **generated at build time** by [buf](https://buf.build) and are
not committed. `./gradlew build` regenerates them; nothing under `gen/` or `build/`
belongs in git.

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

Each starter README lists the keys for its role. The full contract — every field,
every decline code — is the QR Payment API spec (`qr_api.md`); ask the t-0 team for
the current copy.

## Documentation

- Per-role integration guides: the README in each starter directory.
- Field-level contract: `qr_api.md` (from the t-0 team). Endpoints are numbered §1–§15
  there, and the code and READMEs here reference those numbers rather than restating
  the semantics.

## Roadmap

Go and Node SDKs land as sibling directories — `go/` and `node/` — served by the
same `proto/` at the root. Names and versioning are fixed now so you can plan
against them:

| Ecosystem | Artifact | Versioning | Transport |
|---|---|---|---|
| Java | `network.t-0:usdt-pay-sdk-java` | Maven, `X.Y.Z` | grpc-java |
| Go | `github.com/t-0-network/usdt-pay-sdk/go` | Git tags `go/vX.Y.Z` | Connect |
| Node | `@t-0/usdt-pay-sdk` | npm, `X.Y.Z` | Connect |

Neither the Go nor the Node code exists yet. Until 1.0 the Java artifact is not
published either — clone this repo and let the starters build against the local
`:sdk` project.

## License

MIT — see [LICENSE](LICENSE).
