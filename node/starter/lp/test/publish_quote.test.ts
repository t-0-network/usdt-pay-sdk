import assert from "node:assert/strict";
import { test } from "node:test";
import {
  Code,
  ConnectError,
  createClient,
  createRouterTransport,
  type ServiceImpl,
} from "@connectrpc/connect";
import {
  LpService,
  PublishQuoteResponse_Failure_Reason,
} from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { publishQuotes } from "../src/internal/publish_quote.js";

function fakeT0(publishQuote: ServiceImpl<typeof LpService>["publishQuote"]) {
  return createClient(
    LpService,
    createRouterTransport(({ service }) => {
      service(LpService, { publishQuote } as ServiceImpl<typeof LpService>);
    }),
  );
}

const quotes = [
  {
    quoteRef: "quote-ref-1",
    localCurrency: "COP",
    fxRate: decimalFromString("4000.00"),
    expiresAt: new Date(Date.now() + 300_000),
  },
];

test("accepted when t-0 publishes the quote", async () => {
  let seen: { quoteRef: string; fxRate: { unscaled: bigint } } | undefined;
  const t0 = fakeT0(async (request) => {
    const q = request.quotes[0];
    seen = { quoteRef: q.quoteRef, fxRate: { unscaled: q.fxRate!.unscaled } };
    return {
      result: {
        case: "success",
        value: { quotes: [{ quoteRef: "quote-ref-1", quoteId: 42n }] },
      },
    };
  });

  const outcome = await publishQuotes(t0, quotes);

  assert.equal(outcome.kind, "accepted");
  assert.equal(outcome.shouldRetry, false);
  assert.equal(seen?.quoteRef, "quote-ref-1");
  assert.equal(seen?.fxRate.unscaled, 400000n);
  assert.equal(
    outcome.kind === "accepted" && outcome.value.quotes[0].quoteId,
    42n,
  );
});

test("VALIDITY_INVALID is a rejection", async () => {
  const t0 = fakeT0(async () => ({
    result: {
      case: "failure",
      value: { reason: PublishQuoteResponse_Failure_Reason.VALIDITY_INVALID },
    },
  }));

  const outcome = await publishQuotes(t0, quotes);

  assert.equal(outcome.kind, "rejected");
  assert.equal(outcome.shouldRetry, false);
  assert.equal(outcome.kind === "rejected" && outcome.reason, "VALIDITY_INVALID");
});

test("no answer becomes unknown, which must be retried", async () => {
  const t0 = fakeT0(async () => {
    throw new ConnectError("t-0 is down", Code.Unavailable);
  });

  const outcome = await publishQuotes(t0, quotes);

  assert.equal(outcome.kind, "unknown");
  assert.equal(outcome.shouldRetry, true);
});

test("a request t-0 refuses outright is rejected, not retried", async () => {
  const t0 = fakeT0(async () => {
    throw new ConnectError("quotes must not be empty", Code.InvalidArgument);
  });

  const outcome = await publishQuotes(t0, quotes);

  assert.equal(outcome.kind, "rejected");
  assert.equal(outcome.shouldRetry, false);
});
