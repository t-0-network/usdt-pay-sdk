# Acquirer starter — Java

You own the merchant relationship. Your POS asks you to price a sale, you open a
payment intent with t-0, you show the customer a QR, and you learn from t-0's
callbacks whether the sale was authorized and when it settled.

Section references (§3, §7, …) point at the QR Payment API spec (`qr_api.md`) —
ask the t-0 team for the current copy. This README says what to build; the spec
says what every field means.

## Prerequisites

- Java 17+
- A secp256k1 private key. Any 32 random bytes will do: `openssl rand -hex 32`.
- The t-0 network public key, from the t-0 team.

## Run it

```bash
cp .env.example .env      # then fill in PRIVATE_KEY and NETWORK_PUBLIC_KEY

# Build from the java/ root — the starter compiles against the local :sdk project.
(cd ../.. && ./gradlew :starter:acquirer:installDist)

# Run from here: .env is read from the working directory.
./build/install/acquirer/bin/acquirer
```

It prints your public key, starts the callback server, and runs one demo sale
through §3 → §4.

## What you implement

| Direction | Endpoint | Where |
|---|---|---|
| you → t-0 | §3 `GetPaymentQuote` | `internal/GetPaymentQuote.java` |
| you → t-0 | §4 `CreatePaymentIntent` | `internal/CreatePaymentIntent.java` |
| you → t-0 | §12 `SettlementReceived` | `internal/SettlementReceived.java` |
| t-0 → you | §7 `PaymentAuthorized` | `handler/AcquirerCallbackHandler.java` |
| t-0 → you | §11 `SettlementInitiated` | `handler/AcquirerCallbackHandler.java` |
| t-0 → you | §13 `SettlementCompleted` | `handler/AcquirerCallbackHandler.java` |
| t-0 → you | §15 `PaymentExpired` | `handler/AcquirerCallbackHandler.java` |

Your settlement mode is fixed at onboarding and decides which half you need.
**Fiat mode** (you have an assigned LP): §3, §11, §12 — and §13 never fires.
**USDt mode**: skip §3, §11, §12; you set your own `fxRate` on §4 and finish on §13.

## Phases

### Phase 1 — keys and server

1. **1.1** Put your private key in `.env`, start the app, see it print your public key.
2. **1.2** Send that public key to the t-0 team. Until they have it, every call you
   make is rejected.
3. **1.3** Confirm the callback server came up on `PORT`.

### Phase 2 — quote → intent

1. **2.1** Replace the demo sale in `Main.java` — currency, amount and `paymentRef`
   are declared there once and handed to both calls, because a quote and an intent
   that disagree price one thing and charge another. In USDt mode drop §3 and go
   straight to §4 with your own rate.
2. **2.2** Mint `paymentRef` and `idempotencyKey` when the sale is created rather
   than at call time, and persist the returned `paymentIntentId` against the sale.
   `paymentRef` is your sale's correlation ref — t-0 echoes it on §7 and §15 and does
   not require it to be unique; `idempotencyKey` is what §4 is keyed on. Render each
   `qrOptions[].renderablePayload` as a QR image **as-is** — it is chain-native, and
   rebuilding it from the address and the amount is how you end up with a QR that
   pays the wrong thing.
3. **2.3** Deploy and give the t-0 team your base URL so Phase 3 can reach you.

### Phase 3 — callbacks

Implement the four methods in `handler/AcquirerCallbackHandler.java`.

1. **3.1** §7 — mark the sale authorized and release the goods. From here the Issuer
   is obligated to settle; settlement lands later.
2. **3.2** §11 — record `(lpId, bankTransferRef)` as a transfer to watch for. Fiat
   mode only, and *not* proof of receipt.
3. **3.3** §13 — close out every intent in `settledPaymentIntentIds`. USDt mode only.
4. **3.4** §15 — cancel the pending sale and take the QR off the POS.

### Phase 4 — confirm the fiat leg

Fiat mode only.

1. **4.1** When the LP's transfer shows up on your bank statement, match it on
   `(lpId, bankTransferRef)` and call `SettlementReceived.confirm(...)` — §12. The
   intent reaches SETTLED on this call and nothing else; you are the oracle for the
   bank leg.
2. **4.2** Retry §12 with backoff until t-0 answers, always with the same
   `(lpId, bankTransferRef)` pair.

## Callbacks are delivered at least once

t-0 retries every callback with backoff until you acknowledge, and **your
acknowledgment means "recorded, stop retrying"**. A handler that returns success
without a durable write tells t-0 to stop and then forgets the event — nothing
redelivers it.

So each handler writes the event under its dedup key first, and returns second. A
repeat under a key you already hold is a no-op you still acknowledge.

| Callback | Dedup key |
|---|---|
| §7 `PaymentAuthorized` | `paymentIntentId` |
| §11 `SettlementInitiated` | `fiatSettlementId` |
| §13 `SettlementCompleted` | `settlementId` |
| §15 `PaymentExpired` | `paymentIntentId` |

Scope the key **per callback**, not globally: §7 and §15 are both keyed on
`paymentIntentId`, so one shared `processed(key)` table collides an intent's expiry
with its authorization and drops one of them.

The same discipline applies to what you send: §4 is keyed on your `idempotencyKey`
and §12 on the pair `(lpId, bankTransferRef)`. Retry with the original key and
identical content — a fresh key on a retry opens a second intent for one sale.
Retrying a *declined* sale is the other case: that takes a fresh `idempotencyKey`
under the same `paymentRef`.

Every call in `internal/` returns an `Outcome`, which is what tells you which of the
three you got:

| Outcome | What it means | What to do |
|---|---|---|
| `Accepted` | t-0 committed it | record it, move on |
| `Rejected` | t-0 refuses this payload | fix the fields, resend the **same** key |
| `Unknown` | no answer; it may or may not have committed | retry the **same** key, unchanged |

`outcome.shouldRetry()` is true only for `Unknown`.

## Layout

```
src/main/java/network/t0/pay/acquirer/
├── Main.java                            # entry point, phases in order
├── Config.java                          # what .env supplies
├── handler/AcquirerCallbackHandler.java # §7, §11, §13, §15 — t-0 calls you
└── internal/
    ├── GetPaymentQuote.java             # §3
    ├── CreatePaymentIntent.java         # §4
    ├── SettlementReceived.java          # §12
    ├── Outcome.java                     # accepted / rejected / unknown
    ├── Decimals.java                    # unscaled × 10^exponent ↔ BigDecimal
    └── Times.java                       # protobuf Timestamp ↔ Instant
```

## Docker

The build context is the repository root — `java/sdk/src/main/proto` is a symlink
into `proto/`, so a narrower context cannot resolve it.

```bash
cd ../../..                 # repository root
docker build -f java/starter/acquirer/Dockerfile -t usdt-pay-acquirer .
docker run -p 8080:8080 --env-file java/starter/acquirer/.env usdt-pay-acquirer
```

The image carries no `.env` on purpose: your private key does not belong in a layer.
