import type { Server } from "node:http";
import {
  createClient,
  createServer,
  IssuerCallbackService,
  IssuerService,
} from "@t-0/usdt-pay-sdk";
import { ConfigurationError, loadConfig } from "./config.js";
import { issuerCallbackHandler } from "./handler.js";

/**
 * Issuer starter for the t-0 QR payment flow.
 *
 * Work through the numbered TODOs in order; the README explains each phase. Numbers
 * such as §6 are shorthand for the endpoints — the README maps them to RPC names, and
 * the issuer API reference documents every field:
 * https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_issuer/
 */
async function main(): Promise<void> {
  const config = loadConfig();

  console.log(`Issuer public key: ${config.publicKey}`);
  // TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

  // Outbound: everything you call on t-0 (§6, §9, §14). Each internal/ helper sets its
  // own timeout — a Connect deadline is per call, so there is nothing to install here.
  const t0 = createClient(config.tzeroEndpoint, config.privateKey, IssuerService);

  // Inbound: the one callback t-0 pushes to you (§5).
  // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
  const server = await createServer(config.port, config.networkPublicKey, (router) => {
    router.service(IssuerCallbackService, issuerCallbackHandler);
  });
  console.log(`Callback server listening on port ${config.port}`);

  // ──────────────────────────────────────────────────────────────────
  // Phase 3 — report what you see on-chain.
  //
  // Nothing runs at startup: every outbound call is driven by your chain watcher,
  // not by a timer.
  //
  // TODO: Step 3.1 — when a deposit lands and passes your screening, call
  //       reportPaymentReceived(t0, ...) with outcome `authorized`.
  // TODO: Step 3.2 — after you broadcast a settlement transfer, call
  //       reportSettlementSent(t0, ...).
  //
  // t0 is not passed into the handler on purpose: CreatePaymentInstructions must
  // answer inline with reserved addresses and nothing else. Hand `t0` to your
  // chain watcher.
  // ──────────────────────────────────────────────────────────────────
  void t0;

  shutdownOn(server);
}

function shutdownOn(server: Server): void {
  for (const signal of ["SIGINT", "SIGTERM"] as const) {
    process.once(signal, () => {
      console.log("Shutting down");
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
    console.error("Issuer failed to start", error);
  }
  process.exit(1);
});
