import assert from "node:assert/strict";
import { setTimeout } from "node:timers/promises";
import { after, test } from "node:test";
import { timestampDate } from "@bufbuild/protobuf/wkt";
import {
  createClient,
  createRouterTransport,
  type ServiceImpl,
} from "@connectrpc/connect";
import { LpService, type PublishQuoteRequest } from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { startQuotePublisher } from "../src/quotes.js";

function fakeT0(publishQuote: ServiceImpl<typeof LpService>["publishQuote"]) {
  return createClient(
    LpService,
    createRouterTransport(({ service }) => {
      service(LpService, { publishQuote } as ServiceImpl<typeof LpService>);
    }),
  );
}

test("publishes one demo quote on the first tick", async () => {
  let callCount = 0;
  let captured: PublishQuoteRequest | undefined;

  let resolve: () => void;
  const received = new Promise<void>((r) => {
    resolve = r;
  });

  const t0 = fakeT0(async (request) => {
    callCount++;
    captured = request;
    resolve();
    return {
      result: {
        case: "success",
        value: {
          quotes: request.quotes.map((q) => ({
            quoteRef: q.quoteRef,
            quoteId: 1n,
          })),
        },
      },
    };
  });

  const stop = startQuotePublisher(t0, { refreshMs: 60_000_000 });
  after(stop);

  await received;

  assert.equal(callCount, 1);
  assert.ok(captured);
  assert.equal(captured.quotes.length, 1);

  const q = captured.quotes[0];
  assert.equal(q.localCurrency, "COP");
  assert.deepEqual(q.fxRate, decimalFromString("4000.00"));
  assert.ok(q.quoteRef.length <= 64);
  assert.ok(timestampDate(q.expiresAt!) > new Date());

  stop();
  await setTimeout(50);
  assert.equal(callCount, 1);
});
