import type { Server } from "node:http";
import {
  createClient,
  createServer,
  LpCallbackService,
  LpService,
} from "@t-0/usdt-pay-sdk";
import { loadConfig, serverSetupError, startupFailureLines } from "./config.js";
import { lpCallbackHandler } from "./handler.js";
import { startQuotePublisher } from "./quotes.js";

/** How long a shutdown waits for in-flight calls before it drops their connections. */
const DRAIN_LIMIT_MS = 15_000;

/**
 * LP starter for the t-0 USDt pay flow.
 *
 * Work through the numbered TODOs in order; the README explains each phase. The
 * LP API reference documents every field:
 * https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_lp/
 */
async function main(): Promise<void> {
  const config = loadConfig();

  console.log(`LP public key: ${config.publicKey}`);
  // TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

  // Outbound: everything you call on t-0 (PublishQuote, FiatSettlementSent).
  const t0 = createClient(config.privateKey, config.tzeroEndpoint, LpService);

  // Inbound: the one callback t-0 pushes to you (ExecuteQuote).
  // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
  //
  // createServer checks that key before it returns its promise, and throws right here
  // when it is malformed. The promise is awaited outside the try, so a port that cannot
  // be bound is reported as what it is, not as a key error.
  let listening: Promise<Server>;
  try {
    listening = createServer(config.port, config.networkPublicKey, (router) => {
      router.service(LpCallbackService, lpCallbackHandler);
    });
  } catch (error) {
    throw serverSetupError(error);
  }
  const server = await listening;
  console.log(`Callback server listening on port ${config.port}`);

  // ──────────────────────────────────────────────────────────────────
  // Phase 2 — publish standing quotes.
  //
  // The demo loop publishes a fixed COP quote every minute so you can
  // see the round trip. Replace the constants with your pricing.
  // ──────────────────────────────────────────────────────────────────
  const stopQuotes = startQuotePublisher(t0);

  // Installed before anything below can await, so a signal is handled from here on.
  shutdownOn(server, stopQuotes);

  // ──────────────────────────────────────────────────────────────────
  // Phase 3 — ExecuteQuote (handler).
  //
  // t-0 calls ExecuteQuote when a payment is authorized against one
  // of your standing quotes. The shipped handler accepts unconditionally;
  // in production, record the execution under executionId and answer
  // a redelivery with the same decision.
  // ──────────────────────────────────────────────────────────────────

  // ──────────────────────────────────────────────────────────────────
  // Phase 4 — FiatSettlementSent (driven by your bank rails).
  //
  // TODO: Step 4.1 — after you wire fiat to the acquirer, call
  //       reportFiatSettlementSent(t0, ...) with the bank transfer ref.
  //       One transfer covers accepted executions of one acquirer in
  //       one currency; settlementAmount = the sum of their localAmounts.
  // ──────────────────────────────────────────────────────────────────
}

/**
 * SIGINT and SIGTERM stop the quote timer and taking calls, let the in-flight calls
 * finish, and exit 0. A call still running after DRAIN_LIMIT_MS has its connection
 * dropped, and the process exits 0 all the same. A second signal during the drain
 * changes nothing.
 */
function shutdownOn(server: Server, stopQuotes: () => void): void {
  let shuttingDown = false;
  const shutDown = () => {
    if (shuttingDown) return;
    shuttingDown = true;
    console.log("Shutting down");
    stopQuotes();
    server.close(() => process.exit(0));
    // close() waits on open connections, and t-0 holds its callback connection
    // alive between calls: drop the idle ones so in-flight requests are all it waits for.
    server.closeIdleConnections();
    setTimeout(() => {
      server.closeAllConnections();
      process.exit(0);
    }, DRAIN_LIMIT_MS).unref();
  };
  process.on("SIGINT", shutDown);
  process.on("SIGTERM", shutDown);
}

main().catch((error: unknown) => {
  for (const line of startupFailureLines("LP", error)) {
    console.error(line);
  }
  process.exit(1);
});
