# LP starter — Node

You price USDt↔local fiat, accept the per-payment obligation when t-0 executes
against your standing quote, and settle fiat to the acquirer over bank rails.
One inbound endpoint, two outbound calls.

This README says what to build — for what every field and decline code means, see the
[LP API reference](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_lp/).

## Prerequisites

- Node 22+.
- The t-0 network public key — an uncompressed secp256k1 key, `0x04…` and 130 hex
  digits. It comes from your t-0 onboarding contact, along with a `TZERO_ENDPOINT`
  you can reach.

## Run it

`usdt-pay init` ([usdt-pay-sdk](https://github.com/t-0-network/usdt-pay-sdk))
created this project and wrote `.env` with a fresh `PROVIDER_PRIVATE_KEY`; the
matching public key is on the comment line under it. Fill in `NETWORK_PUBLIC_KEY`
with the key your t-0 onboarding contact gives you, then install and run:

```bash
npm install && npm run dev
```

It prints your public key, starts the callback server and starts publishing the
demo COP quote every minute under `tsx watch`, restarting as you edit. Until the
key is registered with t-0, each tick logs `Unauthenticated` — that is expected
and tells you the key exchange is still pending.

`npm run build && npm start` runs the compiled build; `npm test` runs the tests.

## What you implement

| Direction | Endpoint | What it does | Where |
|---|---|---|---|
| t-0 → you | `ExecuteQuote` | Accept or reject a per-payment obligation | `src/handler.ts` |
| you → t-0 | `PublishQuote` | Push standing quotes into t-0's order book | `src/quotes.ts` + `src/internal/publish_quote.ts` |
| you → t-0 | `FiatSettlementSent` | Report a fiat bank transfer to the acquirer | `src/internal/fiat_settlement_sent.ts` |

## Phases

### Phase 1 — server

1. **1.1** With `PROVIDER_PRIVATE_KEY` set in `.env`, start the app and see it print your public key
   (it is also recorded as a comment in `.env`, right under the private key).
2. **1.2** Send that public key to your t-0 onboarding contact, together with the
   base URL where this service listens. Onboarding runs through your t-0 contact,
   and the same exchange is where `NETWORK_PUBLIC_KEY` comes back to you.
   `ExecuteQuote` is synchronous and on the critical path: if t-0
   cannot reach that URL, no execution can be delivered.

### Phase 2 — publish standing quotes

Replace the demo constants in `src/quotes.ts` with your pricing — one quote per
currency per call, each under its own `quoteRef`. The demo publishes a fixed
COP 4000.00 rate every minute; in production you price from your FX feed and
publish as rates change.

1. **2.1** Replace `QUOTE_CURRENCY`, `QUOTE_FX_RATE` and the validity/refresh
   intervals with your own values.
2. **2.2** If a tick logs `VALIDITY_INVALID`, the quote's `expiresAt` window is too
   short or too long for t-0's configured bounds — adjust `QUOTE_VALIDITY_MS` and
   `REFRESH_MS` first.

### Phase 3 — ExecuteQuote

Implement `executeQuote` in `src/handler.ts`.

1. **3.1** Look up `executionId` first and return the decision you already made for
   it. Only decide when there is none — a retry must not flip the result.
2. **3.2** Record the execution durably under `executionId` before returning.
   `Accepted` is your firm obligation to pay `localAmount` to the acquirer; you
   receive `amountUsdt` at settlement.
3. **3.3** To decline, return the `rejected` variant with
   `ExecuteQuoteResponse_Rejected_Reason.OTHER` and a non-blank `details` string.
   A Rejected execution routes the payment to manual handling.

### Phase 4 — settle fiat

After you wire fiat to the acquirer over bank rails, call `reportFiatSettlementSent`
with:

- `bankTransferRef` — your reference for this wire, unique per LP. One real bank
  transfer, one ref; never "retry" by wiring twice.
- `settledExecutionIds` — the accepted executions this transfer covers, all for one
  acquirer in one currency.
- `settlementAmount` — the sum of the covered executions' `localAmount`s.

`FAILED_PRECONDITION` means an execution has no durable result yet — retry the same
request under the same `bankTransferRef`.

Nothing here runs on a timer: the bank statement drives it.

## At-least-once, both directions

`ExecuteQuote` is delivered at least once, keyed on `executionId`. Record your
decision durably under that key and return the same answer on a repeat —
acknowledging without a durable write throws the event away.

Your outbound calls work the same way. Retry with the original key and identical
content until t-0 answers:

| Call | Idempotency key | Minted by |
|---|---|---|
| `PublishQuote` | `quoteRef` | you, unique per LP |
| `ExecuteQuote` | `executionId` | t-0 |
| `FiatSettlementSent` | `bankTransferRef` | you, unique per LP |

A rejection is an acknowledgment — stop retrying — but it never consumes the key:
fix the fields and resend the same key.

Every call in `src/internal/` returns an `Outcome` so you can tell the three apart:
`accepted` (record it), `rejected` (fix the fields, resend the same key), `unknown`
(no answer — retry the same key unchanged). `outcome.shouldRetry` is true only for
`unknown`, and the union is discriminated on `outcome.kind`, so a `switch` over it is
exhaustive:

```ts
const outcome = await reportFiatSettlementSent(t0, settlement);
switch (outcome.kind) {
  case "accepted": markSettled(settlement.bankTransferRef); break;
  case "rejected": alertOps(outcome.reason); break;
  case "unknown": scheduleRetry(settlement); break;
}
```

A refusal t-0 answered with — a request it read and would refuse again, such as
`invalid_argument` — comes back as `rejected` rather than `unknown`, because
resending those same bytes only spins.

`FAILED_PRECONDITION` on `FiatSettlementSent` is the exception: an execution's
durable result is still pending, and the contract says retry the same request. The
helper classifies it as `unknown` so the caller retries.

## Money is never a `number`

`Decimal` is `unscaled * 10^exponent`, and `unscaled` is a 64-bit integer, so it is a
`bigint` here. `src/internal/decimals.ts` converts through strings and integers only:
`decimalFromString("100000.00")`, `decimalToString(amount)`,
`decimalToUnits(amount, 6)` for the integer an ERC-681 URI carries. A USDt amount
routed through a JS float loses cents at amounts a POS actually rings up.

## Testing your integration

Both directions stub cleanly, so the half of this that holds your logic needs no
running t-0. Copy the two shapes in `test/`:

- **Inbound** — `test/callback_server.test.ts` boots the real callback server on port
  0 and calls it with a signed client, so signing, verification and validation are all
  in the loop. The test worth writing first is redelivery: call
  `executeQuote` **twice** with the same `executionId` and assert
  your own store holds one decision. That is the at-least-once contract, and it is
  the one that costs money to get wrong.
- **Outbound** — `test/publish_quote.test.ts` and `test/fiat_settlement_sent.test.ts`
  fake t-0 in memory with `createRouterTransport` and hand the helper a real client
  pointed at it. They cover all three outcomes; write the `unknown` one first.

Point `TZERO_ENDPOINT` at a sandbox only once both sides pass on their own.

## Layout

```
src/
├── index.ts                    # entry point, phases in order
├── config.ts                   # what .env supplies
├── handler.ts                  # ExecuteQuote — t-0 calls you
├── quotes.ts                   # demo quote publisher loop
└── internal/
    ├── publish_quote.ts        # PublishQuote
    ├── fiat_settlement_sent.ts # FiatSettlementSent
    ├── outcome.ts              # accepted / rejected / unknown
    └── decimals.ts             # unscaled × 10^exponent ↔ decimal string
test/
├── callback_server.test.ts     # ExecuteQuote answers accepted; a call t-0 did not sign never lands
├── decimals.test.ts
├── outcome.test.ts
├── publish_quote.test.ts       # all three outcomes against a fake t-0
├── fiat_settlement_sent.test.ts # all three outcomes against a fake t-0
└── quotes.test.ts              # demo publisher fires and stops
```

## Docker

```bash
npm install                 # writes package-lock.json; the image installs from it
docker build -t usdt-pay-lp .
docker run -p 8080:8080 --env-file .env usdt-pay-lp
```

The image carries no `.env` on purpose: your private key does not belong in a layer.
