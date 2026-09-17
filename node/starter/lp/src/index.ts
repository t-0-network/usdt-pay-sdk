import type { Server } from "node:http";
import {
  createClient,
  createServer,
  LpCallbackService,
  LpService,
} from "@t-0/usdt-pay-sdk";
import { ConfigurationError, loadConfig } from "./config.js";
import { lpCallbackHandler } from "./handler.js";
import { startQuotePublisher } from "./quotes.js";

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

  // Outbound: everything you call on t-0 (PublishQuote, FiatSettlementSent). Each internal/ helper sets its
  // own timeout — a Connect deadline is per call, so there is nothing to install here.
  const t0 = createClient(config.tzeroEndpoint, config.privateKey, LpService);

  // Inbound: the one callback t-0 pushes to you (ExecuteQuote).
  // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
  const server = await createServer(config.port, config.networkPublicKey, (router) => {
    router.service(LpCallbackService, lpCallbackHandler);
  });
  console.log(`Callback server listening on port ${config.port}`);

  // ──────────────────────────────────────────────────────────────────
  // Phase 2 — publish standing quotes.
  //
  // The demo loop publishes a fixed COP quote every minute so you can
  // see the round trip. Replace the constants with your pricing.
  // ──────────────────────────────────────────────────────────────────
  const stop = startQuotePublisher(t0);

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

  shutdownOn(server, stop);
}

function shutdownOn(server: Server, stopQuotes: () => void): void {
  for (const signal of ["SIGINT", "SIGTERM"] as const) {
    process.once(signal, () => {
      console.log("Shutting down");
      stopQuotes();
      server.close(() => process.exit(0));
      // close() waits on open connections, and t-0 holds its callback connection
      // alive between calls. Drop the idle ones, give in-flight requests 10s, leave.
      server.closeIdleConnections();
      setTimeout(() => process.exit(0), 10_000).unref();
    });
  }
}

main().catch((error: unknown) => {
  if (error instanceof ConfigurationError) {
    console.error(error.message);
    console.error(error.help);
  } else {
    console.error("LP failed to start", error);
  }
  process.exit(1);
});
