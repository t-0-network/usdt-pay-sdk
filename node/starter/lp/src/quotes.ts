import crypto from "node:crypto";
import type { Client, LpService } from "@t-0/usdt-pay-sdk";
import { decimalFromString } from "./internal/decimals.js";
import { publishQuotes } from "./internal/publish_quote.js";

// TODO: replace with your pricing
const QUOTE_CURRENCY = "COP";
const QUOTE_FX_RATE = decimalFromString("4000.00");
// How long the quote stands. VALIDITY_INVALID from t-0 means this window is too
// short or too long — tune these two numbers first.
const QUOTE_VALIDITY_MS = 5 * 60_000;
const REFRESH_MS = 60_000;

/**
 * Publishes a fixed demo quote on a timer so the sandbox flow runs end to end.
 *
 * Returns a stop function that clears the interval.
 */
export function startQuotePublisher(
  t0: Client<typeof LpService>,
  options?: { refreshMs?: number },
): () => void {
  const refreshMs = options?.refreshMs ?? REFRESH_MS;

  const tick = () => {
    const quoteRef = `${QUOTE_CURRENCY}-${crypto.randomUUID()}`;
    const expiresAt = new Date(Date.now() + QUOTE_VALIDITY_MS);

    publishQuotes(t0, [
      { quoteRef, localCurrency: QUOTE_CURRENCY, fxRate: QUOTE_FX_RATE, expiresAt },
    ]).catch((e: unknown) => {
      console.error("PublishQuote tick failed", e);
    });
  };

  // Fire once immediately (not awaited — an unreachable endpoint does not delay startup).
  tick();

  const interval = setInterval(tick, refreshMs);
  interval.unref();

  return () => {
    clearInterval(interval);
  };
}
