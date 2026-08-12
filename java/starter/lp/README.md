# Liquidity Provider starter — Java

You price USDt↔local fiat for one acquirer, get bound to that price when a sale is
authorized, and settle the fiat over bank rails on your own initiative.

Fiat settlement mode only. If your acquirer settles in USDt there is no LP in the
flow at all.

Section references (§1, §8, …) point at the QR Payment API spec (`qr_api.md`) —
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
(cd ../.. && ./gradlew :starter:lp:installDist)

# Run from here: .env is read from the working directory.
./build/install/lp/bin/lp
```

It prints your public key, starts the callback server, and begins refreshing a
standing quote every 30 seconds.

## What you implement

| Direction | Endpoint | Where |
|---|---|---|
| you → t-0 | §1 `PublishQuote` | `internal/PublishQuote.java` |
| you → t-0 | §2 `WithdrawQuote` | `internal/WithdrawQuote.java` |
| you → t-0 | §10 `FiatSettlementSent` | `internal/FiatSettlementSent.java` |
| t-0 → you | §8 `ExecuteQuote` | `handler/LpCallbackHandler.java` |

You are the sole source of your acquirer's quotes. A currency you are not quoting
right now is a currency it cannot sell in — t-0 declines §3 with
`QUOTE_UNAVAILABLE`. There is no fallback LP.

## Phases

### Phase 1 — server

1. **1.1** Put your private key in `.env`, start the app, see it print your public key.
2. **1.2** Send that public key to the t-0 team, and give them the base URL where
   this service listens. §8 is how you find out you owe money.

### Phase 2 — keep a quote standing

The starter publishes a fresh quote every 30 seconds, withdraws the one it
replaces, and withdraws the last one on shutdown.

1. **2.1** Replace the hardcoded `COP` rate in `PublishQuote.java` with your own
   pricing. Mint `quoteRef` from your pricing run and persist it, so a retry after a
   lost response reuses it instead of publishing a duplicate.
2. **2.2** Keep the shutdown withdrawal. A standing quote left behind keeps t-0
   pricing sales you are no longer around to execute.

A standing quote is **immutable and multi-consumable**: any number of sales can
execute against it while it stands. It is not a per-sale offer — price it as a rate
you are happy to be held to repeatedly. You change a price by publishing a new quote
and withdrawing the old one, never by amending one.

`PublishQuote` takes a batch — at most one quote per currency per call, each under
its own `quoteRef`, and the batch is atomic: one invalid quote declines the whole
call and consumes no `quoteRef`. The starter publishes a batch of one.

### Phase 3 — take the obligation

Implement `executeQuote` in `handler/LpCallbackHandler.java`.

1. **3.1** Write `executionId` down with `acquirerId`, `localAmount` and
   `amountUsdt` **before** you return.
2. **3.2** Resolve the acquirer's registered bank destination from `acquirerId`;
   §10 needs it.

§8 is not a request you can decline. t-0 binds you at the moment it *transmits* the
message — your acknowledgment does not create the obligation, it only stops the
retries. `quoteRef` comes back as a correlation echo, not the key: it lets you
attribute an execution that lands before you have recorded t-0's `quoteId` for that
publish.

Withdrawing a quote does not cancel executions already accepted against it.

### Phase 4 — settle the fiat

1. **4.1** When you release the bank transfer, call
   `FiatSettlementSent.report(...)` — §10 — with the transfer's own reference as
   `bankTransferRef` and the `executionId`s it covers. `settlementAmount` must equal
   the sum of those executions' local amounts, and one transfer credits one acquirer.
2. **4.2** Retry with the same `bankTransferRef` until t-0 answers. The money
   already moved; a rejection means fix what you *reported*, not send another
   transfer.

Nothing from t-0 tells you when to pay — batching and timing are yours. t-0 relays
your §10 to the acquirer as §11 so it knows which reference to watch for, and the
intent settles when the acquirer confirms receipt on §12.

## At-least-once, both directions

§8 is delivered at least once, keyed on `executionId`. Acknowledging before that id
is durably written stops the retries and leaves nothing on your side knowing you
owe the money.

Your outbound calls carry keys too — retry with the original key and identical
content:

| Call | Idempotency key | Minted by |
|---|---|---|
| §1 `PublishQuote` | `quoteRef` | you, unique per LP |
| §2 `WithdrawQuote` | `quoteId` | t-0 |
| §8 `ExecuteQuote` | `executionId` | t-0 |
| §10 `FiatSettlementSent` | `bankTransferRef` | you, unique per LP |

A rejection is an acknowledgment — stop retrying — but it never consumes the key:
fix the fields and resend the same key.

Every call in `internal/` returns an `Outcome` so you can tell the three apart:
`Accepted` (record it), `Rejected` (fix the fields, resend the same key), `Unknown`
(no answer — retry the same key unchanged). `outcome.shouldRetry()` is true only for
`Unknown` — that is what the quote-refresh loop in `Main.java` keys off when a
withdrawal does not complete.

## Layout

```
src/main/java/network/t0/pay/lp/
├── Main.java                      # entry point, quote loop, shutdown withdrawal
├── Config.java                    # what .env supplies
├── handler/LpCallbackHandler.java # §8 — t-0 calls you
└── internal/
    ├── PublishQuote.java          # §1
    ├── WithdrawQuote.java         # §2
    ├── FiatSettlementSent.java    # §10
    ├── Outcome.java               # accepted / rejected / unknown
    ├── Decimals.java              # unscaled × 10^exponent ↔ BigDecimal
    └── Times.java                 # protobuf Timestamp ↔ Instant
```

## Docker

The build context is the repository root — `java/sdk/src/main/proto` is a symlink
into `proto/`, so a narrower context cannot resolve it.

```bash
cd ../../..                 # repository root
docker build -f java/starter/lp/Dockerfile -t usdt-pay-lp .
docker run -p 8080:8080 --env-file java/starter/lp/.env usdt-pay-lp
```

The image carries no `.env` on purpose: your private key does not belong in a layer.
