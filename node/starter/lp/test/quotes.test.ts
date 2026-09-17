import assert from "node:assert/strict";
import { setTimeout } from "node:timers/promises";
import { after, test } from "node:test";
import { timestampDate } from "@bufbuild/protobuf/wkt";
import {
  createClient,
  createRouterTransport,
  type ServiceImpl,
} from "@connectrpc/connect";
import { LpService, type PublishQuoteRequest, type PublishQuoteRequest_Quote } from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "../src/internal/decimals.js";
import { startQuotePublisher } from "../src/quotes.js";

test("publishes one demo quote on the first tick", async () => {
  let callCount = 0;
  let captured: PublishQuoteRequest | undefined;

  // A deferred promise the test awaits — resolved when the fake runs.
  let resolve: () => void;
  const received = new Promise<void>((r) => {
    resolve = r;
  });

  const t0 = createClient(
    LpService,
    createRouterTransport(({ service }) => {
      service(LpService, {
        publishQuote(request: PublishQuoteRequest) {
          callCount++;
          captured = request;
          resolve();
          return {
            result: {
              case: "success" as const,
              value: {
                quotes: request.quotes.map((q: PublishQuoteRequest_Quote) => ({
                  quoteRef: q.quoteRef,
                  quoteId: 1n,
                })),
              },
            },
          };
        },
      } as unknown as ServiceImpl<typeof LpService>);
    }),
  );

  // A huge refresh interval so only the first immediate tick fires.
  const stop = startQuotePublisher(t0, { refreshMs: 60_000_000 });
  after(stop);

  // The first tick is fired-not-awaited, so wait for the fake to run.
  await received;

  assert.equal(callCount, 1);
  assert.ok(captured);
  assert.equal(captured.quotes.length, 1);

  const q = captured.quotes[0];
  assert.equal(q.localCurrency, "COP");
  assert.deepEqual(q.fxRate, decimalFromString("4000.00"));
  assert.ok(q.quoteRef.length <= 64);
  assert.ok(timestampDate(q.expiresAt!) > new Date());

  // stop() clears the interval — the call count must not increase.
  stop();
  await setTimeout(50);
  assert.equal(callCount, 1);
});
