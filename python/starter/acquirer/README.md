# Acquirer — t-0 USDt pay flow

This project integrates you as the **acquirer** in the t-0 USDt pay flow: you
price the sale, open the payment intent, show the QR, and learn when it settles.

## Prerequisites

- Python 3.13 or later
- [uv](https://docs.astral.sh/uv/) (package manager)

## Run it

```bash
uv sync
uv run python -m acquirer.main
```

It prints your public key, starts the callback server, and once the server is
listening runs one demo sale through `get_payment_quote` →
`create_payment_intent`. That demo is a fiat-mode sale for 100 000 COP —
replace it in `main.py` once the round trip works.

The callback server is uvicorn, on `PORT` on all interfaces (IPv4, and IPv6
where the host has it). It speaks HTTP/1.1 and serves the Connect and gRPC-Web
protocols. It does not serve gRPC, which needs HTTP/2.

SIGINT (Ctrl+C) or SIGTERM shuts it down: the server stops accepting calls,
waits up to 15 s for the calls in flight, cancels the demo sale if it is still
waiting on t-0, and exits with status 0.

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `PROVIDER_PRIVATE_KEY` | Yes | Generated into `.env` by the CLI | Your secp256k1 private key (hex). Every request you send to t-0 is signed with it. |
| `NETWORK_PUBLIC_KEY` | Yes | The sandbox key, in `.env` | t-0's public key. Every callback must verify against it. |
| `TZERO_ENDPOINT` | No | `https://usdt-pay-api-sandbox.t-0.network` | The t-0 pay API. |
| `PORT` | No | `8080` | The callback server's port, 1 to 65535. |

Surrounding whitespace is trimmed from the two keys and from `PORT`. An empty
`TZERO_ENDPOINT` or `PORT` counts as unset.

The starter reads `.env` from the working directory when it exists. Without
it, the starter says so on stderr and takes the values from the environment
(for example `docker run --env-file .env`). A variable set in the environment
wins over the same one in `.env`.

The libraries the starter runs on read a few more:

| Variable | Read by | Effect |
|---|---|---|
| `PYTHON_DOTENV_DISABLED` | python-dotenv | `1`, `true`, `t`, `yes` or `y` (in any case) skips `.env`. |
| `FORWARDED_ALLOW_IPS` | uvicorn | The proxy addresses trusted to set `X-Forwarded-For` and `X-Forwarded-Proto`. Default `127.0.0.1,::1`. |
| `WEB_CONCURRENCY` | uvicorn | Its worker count. The starter runs one server in its own process, so the count has no effect, but a value that is not an integer makes the start fail. |
| `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`, `NO_PROXY`, and the lowercase forms | pyqwest, the HTTP client that sends the calls to t-0 | Send the calls to t-0 through a proxy. |

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

The CLI generated a keypair, recorded both halves in `.env`, and pre-filled
`NETWORK_PUBLIC_KEY` with the sandbox key. Send the public key and your callback
base URL to the t-0 onboarding contact. They give you back the production
`NETWORK_PUBLIC_KEY`; every inbound callback is verified against it.

Start the server. Health is mounted and signature-verified, so only t-0 can
reach it.

## Phase 2 — open a payment

Replace the demo sale in `main.py` with a real one from your POS. When the
merchant rings up a sale:

1. **(fiat only)** `get_payment_quote` — indicative `settlement_amount`,
   `fx_rate`, `quote_id`, `expires_at`. The quote stands until it expires; any
   number of intents may reference it.
2. `create_payment_intent` — `payment_intent_id`, `expires_at`,
   `settlement_amount`, deposit options (one per chain), settlement mode. The POS
   builds a QR per option from `chain`, `deposit_address`, `token_contract`,
   `token_decimals` and `settlement_amount` and shows it until `expires_at`. A wallet
   URI carries `settlement_amount × 10^token_decimals` base units, scaled by that
   option's own `token_decimals` (USDt is 6 on some chains, 18 on others); on EVM:
   `ethereum:<token_contract>@<chainId>/transfer?address=<deposit_address>&uint256=<base units>`
   (`chainId` 1 for ETH, 56 for BSC).
3. The customer pays from their wallet. You wait for the callbacks.

## Phase 3 — the callbacks

t-0 pushes five callbacks, each delivered at least once:

- **PaymentAuthorized** — the payment is approved; from here the issuer is
  obligated to settle. Notify the merchant.
- **SettlementInitiated** (fiat) — the LP sent a fiat transfer, naming the
  bank reference to match on your statement. Does not settle the intents.
- **SettlementCompleted** (USDt) — on-chain settlement verified. The intents
  it names are terminal.
- **PaymentExpired** — the QR window lapsed. Clear the pending order.
- **PaymentFailed** — a deposit that will not settle. `reason` tells the customer
  why (`AMOUNT_MISMATCH`, or `ISSUER_DECLINED` with no further detail);
  `disposition` tells them what to expect.

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

`EXPIRED` and `FAILED` branch from `OPEN` — `EXPIRED` fires when the QR window
has elapsed, `FAILED` fires while it is still open. After `PaymentAuthorized`
the issuer is obligated to settle — there is never a reversal for you.

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

A call that fails is classified by its code. `INVALID_ARGUMENT`,
`UNAUTHENTICATED`, `PERMISSION_DENIED`, `UNIMPLEMENTED` and
`FAILED_PRECONDITION` mean t-0 read the request and refused it, so they are
`Rejected`, with `reason` set to `<CODE>: <message>`. Every other code, and a call
that failed in transport, is `Unknown`. `should_retry` is true only for `Unknown`.

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

The tests spin up the real ASGI app on a free port with signed requests.

## Layout

```
src/acquirer/
├── main.py            entry point (uvicorn)
├── config.py          .env → Config
├── handler.py         callbacks
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
