import type { Server } from "node:http";
import {
  createClient,
  createServer,
  IssuerCallbackService,
  IssuerService,
} from "@t-0/usdt-pay-sdk";
import { loadConfig, serverSetupError, startupFailureLines } from "./config.js";
import { issuerCallbackHandler } from "./handler.js";

/** How long a shutdown waits for in-flight calls before it drops their connections. */
const DRAIN_LIMIT_MS = 15_000;

/**
 * Issuer starter for the t-0 USDt pay flow.
 *
 * Work through the numbered TODOs in order; the README explains each phase. The
 * issuer API reference documents every field:
 * https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_issuer/
 */
async function main(): Promise<void> {
  const config = loadConfig();

  console.log(`Issuer public key: ${config.publicKey}`);
  // TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

  // Outbound: everything you call on t-0 (PaymentReceived, SettlementSent).
  const t0 = createClient(config.tzeroEndpoint, config.privateKey, IssuerService);

  // Inbound: the one callback t-0 pushes to you (CreatePaymentInstructions).
  // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
  //
  // createServer checks that key before it returns its promise, and throws right here
  // when it is malformed. The promise is awaited outside the try, so a port that cannot
  // be bound is reported as what it is, not as a key error.
  let listening: Promise<Server>;
  try {
    listening = createServer(config.port, config.networkPublicKey, (router) => {
      router.service(IssuerCallbackService, issuerCallbackHandler);
    });
  } catch (error) {
    throw serverSetupError(error);
  }
  const server = await listening;
  console.log(`Callback server listening on port ${config.port}`);

  // Installed before anything below can await, so a signal is handled from here on.
  shutdownOn(server);

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
}

/**
 * SIGINT and SIGTERM stop taking calls, let the in-flight ones finish, and exit 0. A
 * call still running after DRAIN_LIMIT_MS has its connection dropped, and the process
 * exits 0 all the same. A second signal during the drain changes nothing.
 */
function shutdownOn(server: Server): void {
  let shuttingDown = false;
  const shutDown = () => {
    if (shuttingDown) return;
    shuttingDown = true;
    console.log("Shutting down");
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
  for (const line of startupFailureLines("Issuer", error)) {
    console.error(line);
  }
  process.exit(1);
});
