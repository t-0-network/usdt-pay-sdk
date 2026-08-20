# Issuer starter — Node

You reserve deposit addresses, watch the chain for the customer's USDt, and settle
on-chain. One inbound endpoint, three outbound calls — all three driven by what you
observe on-chain, none by a timer.

This README says what to build — for what every field and decline code means, see the
[issuer API reference](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_issuer/).

## Prerequisites

- Node 22+.
- A secp256k1 private key. Any 32 random bytes will do: `openssl rand -hex 32`.
- The t-0 network public key — an uncompressed secp256k1 key, `0x04…` and 130 hex
  digits. It comes from your t-0 onboarding contact, along with a `TZERO_ENDPOINT`
  you can reach.

## Run it

```bash
cp .env.example .env      # then fill in PRIVATE_KEY and NETWORK_PUBLIC_KEY

# Install and build from node/ — the starter compiles against the local sdk workspace.
(cd ../.. && npm install && npm run build)

# Run from here: .env is read from the working directory.
npm start
```

It prints your public key and starts the callback server. Nothing else happens until
t-0 calls `CreatePaymentInstructions` — and until you implement that handler it
declines, so nobody can pay against addresses that are not yours.

`npm run dev` runs the same thing under `tsx watch` while you are editing;
`npm test` runs the tests.

## What you implement

| Direction | Endpoint | What it does | Where |
|---|---|---|---|
| t-0 → you | `CreatePaymentInstructions` | Reserve deposit addresses for a sale | `src/handler.ts` |
| you → t-0 | `PaymentReceived` | Transfer is final and KYT-cleared | `src/internal/payment_received.ts` |
| you → t-0 | `SettlementSent` | You broadcast a settlement transfer | `src/internal/settlement_sent.ts` |
| you → t-0 | `PaymentExpired` | Reservation lapsed, addresses released | `src/internal/payment_expired.ts` |

You always settle in USDt. Where that USDt goes depends on the acquirer: its own
wallet in USDt mode, its LP's wallet in fiat mode. You resolve that from the
`acquirerId → settlementWallet` mapping you configure at onboarding — deliberately
without asking t-0, so t-0's on-chain check on `SettlementSent` is a real cross-check rather than
an echo of its own input.

## Phases

### Phase 1 — server

1. **1.1** With `PRIVATE_KEY` set in `.env`, start the app and see it print your public key.
2. **1.2** Send that public key to your t-0 onboarding contact, together with the
   base URL where this service listens. Onboarding runs through the contact you
   already have at t-0 — there is no self-service channel, and the same exchange is
   where `NETWORK_PUBLIC_KEY` comes back to you. `CreatePaymentInstructions` is synchronous and on the
   critical path: if t-0 cannot reach that URL, no intent can be opened, so a laptop
   on `localhost:8080` needs a tunnel or a deployed host before this step means
   anything.

### Phase 2 — the one inbound endpoint

Implement `createPaymentInstructions` in `src/handler.ts`.

1. **2.1** Look up `paymentIntentId` first and return the reservation you already
   made for it. Only allocate fresh addresses when there is none — a retry must not
   burn a second set out of the pool.
2. **2.2** Resolve the settlement wallet for `acquirerId` from your onboarding
   mapping and keep it with the reservation; `SettlementSent` needs it.
3. **2.3** Reserve one address per chain you support, build each
   `renderablePayload` as a chain-native URI (the POS encodes it untouched), and
   hold the reservation until `expiresAt`. t-0 currently asks for a 60–120 second
   window.
4. **2.4** Out of addresses, or the amount is outside your range? Answer with the
   `failure` variant (`ADDRESS_POOL_EMPTY`, `AMOUNT_OUT_OF_RANGE`,
   `ISSUER_UNAVAILABLE`) rather than throwing.

**As shipped, this handler declines every `CreatePaymentInstructions` call with `ISSUER_UNAVAILABLE`.** That is
deliberate. Whatever addresses it returns are rendered by the POS as a payable QR and
a customer sends real USDt to them, so a starter answering with example addresses
would hand customer money to an address you do not own. A decline costs one sale; a
wrong address is irreversible. `test/callback_server.test.ts` holds that line — it
fails the moment the success branch goes live with someone else's addresses.

The response you should return sits directly below the decline, commented out, with
the TRON/Ethereum/BSC options already shaped. Put your own deposit addresses in,
delete the decline, and the QR flow works. The two USDt contract constants in there
are real and stay as they are — it is the deposit addresses that must become yours.

### Phase 3 — report what you see on-chain

Wire your chain watcher to these; nothing here belongs on a timer.

1. **3.1** `reportPaymentReceived` — the transfer is final and KYT-cleared. This
   is what authorizes the sale: t-0 fires `PaymentAuthorized` to the acquirer off
   it, and from that moment you own the on-chain risk and are obligated to settle.
   `amountUsdt` must equal the intent's stored amount exactly, so pass through the
   `Decimal` that `CreatePaymentInstructions` handed you rather than rebuilding it. A payment in the wrong amount,
   or one that arrived after expiry, is yours to refund and never becomes the
   acquirer's problem.
2. **3.2** `reportSettlementSent` — after you broadcast a settlement transfer,
   report it with the transfer's own id as `settlementRef`. On
   `ON_CHAIN_UNCONFIRMED`, resend the same ref once it confirms. Never broadcast a
   second transfer as a "retry": one `settlementRef` is one real transfer.
3. **3.3** `reportPaymentExpired` — when a reservation lapses and you release its
   addresses. Confirmation, not a trigger: t-0 expires the intent on its own clock
   regardless.

## At-least-once, both directions

`CreatePaymentInstructions` is delivered at least once, keyed on `paymentIntentId`. Reserve under that key
and return the same addresses on a repeat — acknowledging without a durable
reservation tells t-0 to stop retrying and leaves the customer paying to an address
you have forgotten.

Your outbound calls work the same way. Retry with the original key and identical
content until t-0 answers:

| Call | Idempotency key | Minted by |
|---|---|---|
| `CreatePaymentInstructions` | `paymentIntentId` | t-0 |
| `PaymentReceived` | `paymentIntentId` | t-0 |
| `SettlementSent` | `settlementRef` | you, unique per issuer |
| `PaymentExpired` | `paymentIntentId` | t-0 |

A rejection is an acknowledgment — stop retrying — but it never consumes the key:
fix the fields and resend the same key.

Every call in `src/internal/` returns an `Outcome` so you can tell the three apart:
`accepted` (record it), `rejected` (fix the fields, resend the same key), `unknown`
(no answer — retry the same key unchanged). `outcome.shouldRetry` is true only for
`unknown`, and the union is discriminated on `outcome.kind`, so a `switch` over it is
exhaustive:

```ts
const outcome = await reportSettlementSent(t0, settlement);
switch (outcome.kind) {
  case "accepted": markSettled(settlement.settlementRef); break;
  case "rejected": alertOps(outcome.reason, outcome.failingIds); break;
  case "unknown": scheduleRetry(settlement); break;
}
```

A refusal t-0 answered with — a request it read and would refuse again, such as
`invalid_argument` — comes back as `rejected` rather than `unknown`, because
resending those same bytes only spins.

## Money is never a `number`

`Decimal` is `unscaled * 10^exponent`, and `unscaled` is a 64-bit integer, so it is a
`bigint` here. `src/internal/decimals.ts` converts through strings and integers only:
`decimalFromString("100000.00")`, `decimalToString(amount)`,
`decimalToUnits(amount, 6)` for the integer an ERC-681 URI carries. A USDt amount
routed through a JS float loses cents at amounts a POS actually rings up.

The cheapest correct thing to do with an amount t-0 sent you is not to convert it at
all — `PaymentReceived` has to report exactly the amount `CreatePaymentInstructions` carried.

## Testing your integration

Both directions stub cleanly, so the half of this that holds your logic needs no
running t-0. Copy the two shapes in `test/`:

- **Inbound** — `test/callback_server.test.ts` boots the real callback server on port
  0 and calls it with a signed client, so signing, verification and validation are all
  in the loop. The test worth writing first is redelivery: call
  `createPaymentInstructions` **twice** with the same `paymentIntentId` and assert
  your own store holds one reservation. That is the at-least-once contract, and it is
  the one that costs money to get wrong.
- **Outbound** — `test/settlement_sent.test.ts` fakes t-0 in memory with
  `createRouterTransport` and hands the helper a real client pointed at it. It covers
  all three outcomes; write the `unknown` one first. That branch is what a happy-path
  test against a sandbox never reaches, and it decides whether you broadcast a second
  transfer by accident.

Point `TZERO_ENDPOINT` at a sandbox only once both sides pass on their own.

## Layout

```
src/
├── index.ts                    # entry point, phases in order
├── config.ts                   # what .env supplies
├── handler.ts                  # CreatePaymentInstructions — t-0 calls you
└── internal/
    ├── payment_received.ts     # PaymentReceived
    ├── settlement_sent.ts      # SettlementSent
    ├── payment_expired.ts      # PaymentExpired
    ├── outcome.ts              # accepted / rejected / unknown
    └── decimals.ts             # unscaled × 10^exponent ↔ decimal string
test/
├── callback_server.test.ts     # CreatePaymentInstructions declines; a call t-0 did not sign never lands
├── decimals.test.ts
├── outcome.test.ts
└── settlement_sent.test.ts     # all three outcomes against a fake t-0
```

## Docker

The build context is `node/`, because the starter compiles against the SDK next to
it.

```bash
cd ../..                    # node/
docker build -f starter/issuer/Dockerfile -t usdt-pay-issuer .
docker run -p 8080:8080 --env-file starter/issuer/.env usdt-pay-issuer
```

The image carries no `.env` on purpose: your private key does not belong in a layer.
