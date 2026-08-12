# Issuer starter — Java

You reserve deposit addresses, watch the chain for the customer's USDt, and settle
on-chain. One inbound endpoint, three outbound calls — all three driven by what you
observe on-chain, none by a timer.

The §-numbers (§5, §6, …) are shorthand for the endpoints; the table below maps each
to its RPC name. This README says what to build — for what every field and decline
code means, see the
[issuer API reference](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_issuer/).

## Prerequisites

- Java 21+. If your JDK is older the Gradle build still works — it provisions a 21
  toolchain on its own — but the binary it produces needs a 21 runtime.
- A secp256k1 private key. Any 32 random bytes will do: `openssl rand -hex 32`.
- The t-0 network public key, from the t-0 team.

## Run it

```bash
cp .env.example .env      # then fill in PRIVATE_KEY and NETWORK_PUBLIC_KEY

# Build from the java/ root — the starter compiles against the local :sdk project.
(cd ../.. && ./gradlew :starter:issuer:installDist)

# Run from here: .env is read from the working directory.
./build/install/issuer/bin/issuer
```

It prints your public key and starts the callback server. Nothing else happens
until t-0 calls §5.

## What you implement

| Direction | Endpoint | Where |
|---|---|---|
| t-0 → you | §5 `CreatePaymentInstructions` | `handler/IssuerCallbackHandler.java` |
| you → t-0 | §6 `PaymentReceived` | `internal/PaymentReceived.java` |
| you → t-0 | §9 `SettlementSent` | `internal/SettlementSent.java` |
| you → t-0 | §14 `PaymentExpired` | `internal/PaymentExpired.java` |

You always settle in USDt. Where that USDt goes depends on the acquirer: its own
wallet in USDt mode, its LP's wallet in fiat mode. You resolve that from the
`acquirerId → settlementWallet` mapping you configure at onboarding — deliberately
without asking t-0, so t-0's on-chain check on §9 is a real cross-check rather than
an echo of its own input.

## Phases

### Phase 1 — server

1. **1.1** Put your private key in `.env`, start the app, see it print your public key.
2. **1.2** Send that public key to the t-0 team, and give them the base URL where
   this service listens. §5 is synchronous and on the critical path — if t-0 cannot
   reach you, no intent can be opened.

### Phase 2 — the one inbound endpoint

Implement `createPaymentInstructions` in `handler/IssuerCallbackHandler.java`.

1. **2.1** Look up `paymentIntentId` first and return the reservation you already
   made for it. Only allocate fresh addresses when there is none — a retry must not
   burn a second set out of the pool.
2. **2.2** Resolve the settlement wallet for `acquirerId` from your onboarding
   mapping and keep it with the reservation; §9 needs it.
3. **2.3** Reserve one address per chain you support, build each
   `renderablePayload` as a chain-native URI (the POS encodes it untouched), and
   hold the reservation until `expiresAt`. t-0 currently asks for a 60–120 second
   window.
4. **2.4** Out of addresses, or the amount is outside your range? Answer with the
   `Failure` variant (`ADDRESS_POOL_EMPTY`, `AMOUNT_OUT_OF_RANGE`,
   `ISSUER_UNAVAILABLE`) rather than an error status.

The starter ships placeholder addresses on TRON, Ethereum and BSC so you can see
the shape of the response. Replace them before you talk to anything real.

### Phase 3 — report what you see on-chain

Wire your chain watcher to these; nothing here belongs on a timer.

1. **3.1** §6 `PaymentReceived` — the transfer is final and KYT-cleared. This is
   what authorizes the sale: t-0 fires §7 to the acquirer off it, and from that
   moment you own the on-chain risk and are obligated to settle. `amountUsdt` must
   equal the intent's stored amount exactly; a payment in the wrong amount, or one
   that arrived after expiry, is yours to refund and never becomes the acquirer's
   problem.
2. **3.2** §9 `SettlementSent` — after you broadcast a settlement transfer, report
   it with the transfer's own id as `settlementRef`. On `ON_CHAIN_UNCONFIRMED`,
   resend the same ref once it confirms. Never broadcast a second transfer as a
   "retry": one `settlementRef` is one real transfer.
3. **3.3** §14 `PaymentExpired` — when a reservation lapses and you release its
   addresses. Confirmation, not a trigger: t-0 expires the intent on its own clock
   regardless.

## At-least-once, both directions

§5 is delivered at least once, keyed on `paymentIntentId`. Reserve under that key
and return the same addresses on a repeat — acknowledging without a durable
reservation tells t-0 to stop retrying and leaves the customer paying to an address
you have forgotten.

Your outbound calls work the same way. Retry with the original key and identical
content until t-0 answers:

| Call | Idempotency key | Minted by |
|---|---|---|
| §5 `CreatePaymentInstructions` | `paymentIntentId` | t-0 |
| §6 `PaymentReceived` | `paymentIntentId` | t-0 |
| §9 `SettlementSent` | `settlementRef` | you, unique per issuer |
| §14 `PaymentExpired` | `paymentIntentId` | t-0 |

A rejection is an acknowledgment — stop retrying — but it never consumes the key:
fix the fields and resend the same key.

Every call in `internal/` returns an `Outcome` so you can tell the three apart:
`Accepted` (record it), `Rejected` (fix the fields, resend the same key), `Unknown`
(no answer — retry the same key unchanged). `outcome.shouldRetry()` is true only for
`Unknown`.

## Layout

```
src/main/java/network/t0/pay/issuer/
├── Main.java                          # entry point, phases in order
├── Config.java                        # what .env supplies
├── handler/IssuerCallbackHandler.java # §5 — t-0 calls you
└── internal/
    ├── PaymentReceived.java           # §6
    ├── SettlementSent.java            # §9
    ├── PaymentExpired.java            # §14
    ├── Outcome.java                   # accepted / rejected / unknown
    ├── Decimals.java                  # unscaled × 10^exponent ↔ BigDecimal
    └── Times.java                     # protobuf Timestamp ↔ Instant
```

## Docker

The build context is the repository root — `java/sdk/src/main/proto` is a symlink
into `proto/`, so a narrower context cannot resolve it.

```bash
cd ../../..                 # repository root
docker build -f java/starter/issuer/Dockerfile -t usdt-pay-issuer .
docker run -p 8080:8080 --env-file java/starter/issuer/.env usdt-pay-issuer
```

The image carries no `.env` on purpose: your private key does not belong in a layer.
