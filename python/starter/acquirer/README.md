# Acquirer — t-0 QR payment flow

This project integrates you as the **acquirer** in the t-0 QR payment flow: you
price the sale, open the payment intent, show the QR, and learn when it settles.

## Prerequisites

- Python 3.13 or later
- [uv](https://docs.astral.sh/uv/) (package manager)

## Run it

```bash
uv sync
uv run python -m acquirer.main
```

Sync mode (gunicorn or waitress):

```bash
uv sync
uv run gunicorn acquirer.wsgi:app --bind 0.0.0.0:8080
```

## What you implement

| Direction | Endpoint | What | Where |
|---|---|---|---|
| you → t-0 | `GetPaymentQuote` | Price a fiat payment | `internal/get_payment_quote.py` |
| you → t-0 | `CreatePaymentIntent` | Open a payment | `internal/create_payment_intent.py` |
| you → t-0 | `SettlementReceived` | Confirm fiat receipt | `internal/settlement_received.py` |
| t-0 → you | `PaymentAuthorized` | Payment approved | `handler.py` |
| t-0 → you | `SettlementInitiated` | Fiat transfer en route | `handler.py` |
| t-0 → you | `SettlementCompleted` | USDt settlement on-chain | `handler.py` |
| t-0 → you | `PaymentExpired` | QR window lapsed | `handler.py` |
| t-0 → you | `PaymentFailed` | Deposit will not settle | `handler.py` |

## Your settlement mode

Your settlement mode is fixed at onboarding — it determines which endpoints
apply to you:

**USDt settlement** — the issuer sends USDt directly to your wallet. You skip
`GetPaymentQuote`, `SettlementInitiated` and `SettlementReceived` entirely, and
your terminal event is `SettlementCompleted`.

**Fiat settlement** — an LP converts USDt to local fiat and sends it over bank
rails. You price with `GetPaymentQuote`, receive `SettlementInitiated` when the
LP sends the transfer, confirm receipt with `SettlementReceived` (the terminal
event), and never see `SettlementCompleted`.

## Phase 1 — keys and server

The CLI generated a keypair and recorded both halves in `.env`. Send the public
key and your callback base URL to the t-0 onboarding contact. They give you
back:

- `NETWORK_PUBLIC_KEY` — put it in `.env`. Every inbound callback is verified
  against it; without it the server rejects everything.

Start the server. Health is mounted and signature-verified, so only t-0 can
reach it.

## Phase 2 — open a payment

When the merchant rings up a sale:

1. **(fiat only)** `get_payment_quote` — indicative `settlement_amount`,
   `fx_rate`, `quote_id`, `expires_at`. The quote stands until it expires; any
   number of intents may reference it.
2. `create_payment_intent` — `payment_intent_id`, `expires_at`,
   `settlement_amount`, deposit options (one per chain), settlement mode. Hand
   each `payment_uri` to the POS unchanged and show it until `expires_at`.
3. The customer pays from their wallet. You wait for the callbacks.

Nothing outbound runs on a timer: the sale drives it.

## Phase 3 — the callbacks

t-0 pushes five callbacks, each delivered at least once:

- **PaymentAuthorized** — the payment is approved; from here the issuer is
  obligated to settle. Notify the merchant.
- **SettlementInitiated** (fiat) — the LP sent a fiat transfer, naming the
  bank reference to match on your statement. Does not settle the intents.
- **SettlementCompleted** (USDt) — on-chain settlement verified. The intents
  it names are terminal.
- **PaymentExpired** — the QR window lapsed. Clear the pending order.
- **PaymentFailed** — a deposit that will not settle. `disposition` tells the
  customer what to expect.

## Phase 4 — fiat mode: confirm receipt

When the bank transfer from `SettlementInitiated` lands on your statement, call
`settlement_received` with the `lp_id` and `bank_transfer_ref` from that
callback. On `Accepted`, the covered intents are settled (terminal in fiat mode).

Nothing runs on a timer: the bank statement drives it.

## The intent as you see it

```
OPEN → AUTHORIZED → SETTLED
                  ↘ EXPIRED
                  ↘ FAILED
```

After `PaymentAuthorized` the issuer is obligated to settle — there is never a
reversal for you. The issuer→LP leg is not yours to see.

## At-least-once, both directions

| Call | Key | Who owns it |
|---|---|---|
| `CreatePaymentIntent` | `idempotency_key` | you |
| `SettlementReceived` | `(lp_id, bank_transfer_ref)` | LP |
| `PaymentAuthorized` | `payment_intent_id` | t-0 |
| `SettlementInitiated` | `fiat_settlement_id` | t-0 |
| `SettlementCompleted` | `settlement_id` | t-0 |
| `PaymentExpired` | `payment_intent_id` | t-0 |
| `PaymentFailed` | `payment_intent_id` | t-0 |

**Outbound:** retry with the *original* key and identical content. A rejection
never consumes the key: correct the fields and resend the same key. Exception:
a **declined** `CreatePaymentIntent` is retried under a **fresh**
`idempotency_key` with the same `payment_ref`. An `Unknown` outcome retries the
same key unchanged.

**Inbound:** write first, ack second. A return without a durable write throws
the event away, and nothing redelivers it.

## Money is never a float

`Decimal` is `unscaled * 10^exponent` — 123.45 is `unscaled=12345,
exponent=-2`. `unscaled` is a 64-bit integer. Use `decimal_from_string` /
`decimal_to_string` for conversions; both do integer arithmetic only.

## Idempotency is yours to implement

Each callback's TODO tells you the dedup key. Write durably under it before
returning — a return without a durable write throws the event away, and nothing
redelivers it.

## Testing your integration

```bash
uv run pytest
```

The tests spin up the real ASGI/WSGI app on a free port with signed requests.

## Sync or async

The starter ships both:

- **Async** — `main.py`, uvicorn, `AcquirerCallbacks`, `create_payment_intent`
- **Sync** — `wsgi.py`, gunicorn/waitress, `AcquirerCallbacksSync`,
  `create_payment_intent_sync`

Both share the same outbound helpers (`build_request` / `outcome_from_response`
are pure, tested once).

## Layout

```
src/acquirer/
├── main.py            async entry (uvicorn)
├── wsgi.py            sync entry (gunicorn/waitress)
├── config.py          .env → Config
├── handler.py         async callbacks
├── handler_sync.py    sync callbacks
└── internal/
    ├── outcome.py             Accepted / Rejected / Unknown
    ├── decimals.py            decimal_from_string / decimal_to_string
    ├── get_payment_quote.py   fiat pricing
    ├── create_payment_intent.py  open a payment
    └── settlement_received.py    confirm fiat receipt
```

## Docker

```bash
docker build -t my-acquirer .
docker run --env-file .env -p 8080:8080 my-acquirer
```

The image runs as a non-root user. `.env` is excluded by `.dockerignore` — pass
it at runtime.
