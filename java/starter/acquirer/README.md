# Acquirer starter — Java

You own the merchant relationship. Your POS asks you to price a sale, you open a
payment intent with t-0, you show the customer a QR, and you learn from t-0's
callbacks whether the sale was authorized and when it settled.

The RPC names below (`GetPaymentQuote`, `PaymentAuthorized`, …) match the methods
in the proto and in the code. This README says what to build — for what every field
and decline code means, see the
[acquirer API reference](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_acquirer/).

## Prerequisites

- Java 21+. If your JDK is older the Gradle build still works — it provisions a 21
  toolchain on its own — but the binary it produces needs a 21 runtime.
- A secp256k1 private key. Any 32 random bytes will do: `openssl rand -hex 32`.
- The t-0 network public key — an uncompressed secp256k1 key, `0x04…` and 130 hex
  digits. It comes from your t-0 onboarding contact, along with a `TZERO_ENDPOINT`
  you can reach.

## Run it

If you used the scaffolder, skip the `cp` — your generated `.env` already holds
your key, and copying the example over it destroys it.

```bash
cp .env.example .env      # then fill in PROVIDER_PRIVATE_KEY and NETWORK_PUBLIC_KEY

# Build from the java/ root — the starter compiles against the local :sdk project.
(cd ../.. && ./gradlew :starter:acquirer:installDist)

# Run from here: .env is read from the working directory.
./build/install/acquirer/bin/acquirer
```

It prints your public key, starts the callback server, and runs one demo sale
through `GetPaymentQuote` → `CreatePaymentIntent`. That demo is a **fiat-mode**
sale: if you settle in USDt you skip `GetPaymentQuote` entirely and set your own
rate on `CreatePaymentIntent`, so do not read your own first call off it —
[What you implement](#what-you-implement) says which half is yours.

To run the starter's own tests:

```bash
# From a clone of the repository:
(cd ../.. && ./gradlew :starter:acquirer:test)

# From a scaffolded standalone project:
./gradlew test
```

## What you implement

| Direction | Endpoint | What it does | Where |
|---|---|---|---|
| you → t-0 | `GetPaymentQuote` | Prices a fiat sale (fiat only) | `internal/GetPaymentQuote.java` |
| you → t-0 | `CreatePaymentIntent` | Opens an intent, returns QR options | `internal/CreatePaymentIntent.java` |
| you → t-0 | `SettlementReceived` | You confirm the fiat landed (fiat only) | `internal/SettlementReceived.java` |
| t-0 → you | `PaymentAuthorized` | Sale approved, release goods | `handler/AcquirerCallbackHandler.java` |
| t-0 → you | `SettlementInitiated` | LP sent a bank transfer, pre-notice (fiat only) | `handler/AcquirerCallbackHandler.java` |
| t-0 → you | `SettlementCompleted` | On-chain settlement verified (USDt only) | `handler/AcquirerCallbackHandler.java` |
| t-0 → you | `PaymentExpired` | QR expired, cancel the sale | `handler/AcquirerCallbackHandler.java` |

Your settlement mode decides which endpoints apply. The table above marks each
endpoint's mode. Fiat mode: `SettlementCompleted` never fires. USDt mode: skip
`GetPaymentQuote`, `SettlementInitiated`, `SettlementReceived`.

## Phases

### Phase 1 — keys and server

1. **1.1** With `PROVIDER_PRIVATE_KEY` set in `.env`, start the app and see it print your public key.
2. **1.2** Send that public key to your t-0 onboarding contact. Until they have it,
   every call you make is rejected. Onboarding runs through the contact you already
   have at t-0 — there is no self-service channel, and the same exchange is where
   `NETWORK_PUBLIC_KEY` comes back to you.
3. **1.3** Confirm the callback server came up on `PORT`.

### Phase 2 — quote → intent

1. **2.1** Replace the demo sale in `Main.java` — currency, amount and `paymentRef`
   are declared there once and handed to both calls, because a quote and an intent
   that disagree price one thing and charge another. In USDt mode drop
   `GetPaymentQuote` and go straight to `CreatePaymentIntent` with your own rate.
2. **2.2** Mint `paymentRef` and `idempotencyKey` when the sale is created rather
   than at call time, and persist the returned `paymentIntentId` against the sale.
   `paymentRef` is your sale's correlation ref — t-0 echoes it on `PaymentAuthorized`
   and `PaymentExpired`, and it is explicitly **not** an idempotency key and not
   required to be unique.
   `idempotencyKey` is the only thing `CreatePaymentIntent` is keyed on: at most one
   intent is ever created under one key, repeating a key returns that intent unchanged,
   and retrying a *declined* sale takes a fresh key under the same `paymentRef`.
   Keying `CreatePaymentIntent` on `paymentRef` opens a second intent on every retry.
   Render each `qrOptions[].renderablePayload` as a QR image **as-is** — it is
   chain-native, and rebuilding it from the address and the amount is how you end up
   with a QR that pays the wrong thing.
3. **2.3** Deploy and give your t-0 onboarding contact the base URL Phase 3's
   callbacks should reach. It has to be openable from outside, so a laptop on
   `localhost:8080` needs a tunnel or a deployed host first.

### Phase 3 — callbacks

Implement the callbacks in `handler/AcquirerCallbackHandler.java`.

1. **3.1** `PaymentAuthorized` — mark the sale authorized and release the goods.
   From here the Issuer is obligated to settle; settlement lands later.
2. **3.2** `SettlementInitiated` — record `(lpId, bankTransferRef)` as a transfer to
   watch for. Fiat mode only, and *not* proof of receipt.
3. **3.3** `SettlementCompleted` — close out every intent in
   `settledPaymentIntentIds`. USDt mode only; leave as a no-op in fiat mode.
4. **3.4** `PaymentExpired` — cancel the pending sale and take the QR off the POS.

### Phase 4 — confirm the fiat leg

Fiat mode only.

1. **4.1** When the LP's transfer shows up on your bank statement, match it on
   `(lpId, bankTransferRef)` and call `SettlementReceived.confirm(...)`. The intent
   reaches SETTLED on this call and nothing else; you are the oracle for the bank leg.
2. **4.2** Retry `SettlementReceived` with backoff until t-0 answers, always with the
   same `(lpId, bankTransferRef)` pair.

## Callbacks are delivered at least once

t-0 retries every callback with backoff until you acknowledge, and **your
acknowledgment means "recorded, stop retrying"**. A handler that returns success
without a durable write tells t-0 to stop and then forgets the event — nothing
redelivers it.

So each handler writes the event under its dedup key first, and returns second. A
repeat under a key you already hold is a no-op you still acknowledge.

| Callback | Dedup key |
|---|---|
| `PaymentAuthorized` | `paymentIntentId` |
| `SettlementInitiated` | `fiatSettlementId` |
| `SettlementCompleted` | `settlementId` |
| `PaymentExpired` | `paymentIntentId` |

Scope the key **per callback**, not globally: `PaymentAuthorized` and
`PaymentExpired` are both keyed on `paymentIntentId`, so one shared
`processed(key)` table collides an intent's expiry with its authorization and drops
one of them.

The same discipline applies to what you send: `CreatePaymentIntent` is keyed on
your `idempotencyKey` and `SettlementReceived` on the pair
`(lpId, bankTransferRef)`. Retry with the original key and identical content — a
fresh key on a retry opens a second intent for one sale. Retrying a *declined* sale
is the other case: that takes a fresh `idempotencyKey` under the same `paymentRef`.

Every call in `internal/` returns an `Outcome`, which is what tells you which of the
three you got:

| Outcome | What it means | What to do |
|---|---|---|
| `Accepted` | t-0 committed it | record it, move on |
| `Rejected` | t-0 refuses this payload | fix the fields, resend the **same** key |
| `Unknown` | no answer; it may or may not have committed | retry the **same** key, unchanged |

`outcome.shouldRetry()` is true only for `Unknown`.

## Testing your integration

You do not need t-0 to reach you, or a running server, to test the half of this that
holds your logic. Both directions stub cleanly.

The inbound test worth writing first is redelivery: call your handler **twice** with
the same dedup key and assert your own store holds one row. That is the at-least-once
contract, and it is the one that costs money to get wrong.

**Inbound — your callback handler is a plain object.** It extends a generated
`*ImplBase`, so a test constructs it and calls the method directly, asserting on what
it hands the `StreamObserver`. No server, no signing, no network:

```java
var responses = new ArrayList<PaymentAuthorizedResponse>();
new AcquirerCallbackHandler().paymentAuthorized(
        PaymentAuthorizedRequest.newBuilder().setPaymentIntentId(1).setPaymentRef("sale-1").build(),
        new StreamObserver<>() {
            public void onNext(PaymentAuthorizedResponse r) { responses.add(r); }
            public void onError(Throwable t) { throw new AssertionError(t); }
            public void onCompleted() { }
        });

assertEquals(1, responses.size());
```

**Outbound — fake t-0, not the stub.** The generated stubs are `final` with private
constructors, so they cannot be subclassed. Stand a fake service up in
memory and hand the helper a real stub pointed at it. One test dependency,
`io.grpc:grpc-inprocess`:

```java
String name = InProcessServerBuilder.generateName();
Server server = InProcessServerBuilder.forName(name).directExecutor()
        .addService(new AcquirerServiceGrpc.AcquirerServiceImplBase() {
            @Override public void createPaymentIntent(
                    CreatePaymentIntentRequest request,
                    StreamObserver<CreatePaymentIntentResponse> observer) {
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        })
        .build().start();

var t0 = AcquirerServiceGrpc.newBlockingStub(
        InProcessChannelBuilder.forName(name).directExecutor().build());

assertTrue(CreatePaymentIntent.create(
        t0, "sale-1", idempotencyKey, Decimals.of("100000.00"), quoteId).shouldRetry());
```

`src/test/java/network/t0/pay/acquirer/internal/CreatePaymentIntentTest.java`
works this out for all three outcomes — copy its shape. Write the `Unknown` one first:
it is the branch a happy-path test against a sandbox never reaches, and it decides
whether you open a second intent for one sale by accident.

Point `TZERO_ENDPOINT` at a sandbox only once both sides pass on their own.

## Layout

```
src/main/java/network/t0/pay/acquirer/
├── Main.java                            # entry point, phases in order
├── Config.java                          # what .env supplies
├── handler/AcquirerCallbackHandler.java # PaymentAuthorized, SettlementInitiated,
│                                        # SettlementCompleted, PaymentExpired
└── internal/
    ├── GetPaymentQuote.java             # prices a fiat sale
    ├── CreatePaymentIntent.java         # opens an intent, returns QR options
    ├── SettlementReceived.java          # you confirm the fiat landed
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
