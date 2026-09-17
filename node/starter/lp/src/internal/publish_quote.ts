import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  type Client,
  type Decimal,
  type LpService,
  type PublishQuoteResponse_Success,
  PublishQuoteResponse_Failure_Reason,
} from "@t-0/usdt-pay-sdk";
import { accepted, noResultVariant, outcomeFromError, rejected, type Outcome } from "./outcome.js";

const TIMEOUT_MS = 10_000;

/**
 * PublishQuote — push standing quotes into t-0's order book.
 *
 * Each quote is immutable and multi-consumable while it stands. Publish one per
 * currency per call, each under its own `quoteRef` (idempotency key, unique per LP,
 * ≤64 chars).
 */
export async function publishQuotes(
  t0: Client<typeof LpService>,
  quotes: {
    quoteRef: string;
    localCurrency: string;
    fxRate: Decimal;
    expiresAt: Date;
  }[],
): Promise<Outcome<PublishQuoteResponse_Success>> {
  try {
    const response = await t0.publishQuote(
      {
        quotes: quotes.map((q) => ({
          quoteRef: q.quoteRef,
          localCurrency: q.localCurrency,
          fxRate: q.fxRate,
          expiresAt: timestampFromDate(q.expiresAt),
        })),
      },
      { timeoutMs: TIMEOUT_MS },
    );

    switch (response.result.case) {
      case "success":
        for (const q of response.result.value.quotes) {
          console.log(
            `PublishQuote accepted: ref=${q.quoteRef} → quoteId=${q.quoteId}`,
          );
        }
        return accepted(response.result.value);

      case "failure": {
        const { reason } = response.result.value;
        const name = PublishQuoteResponse_Failure_Reason[reason] ?? String(reason);
        console.warn(`PublishQuote rejected: ${name}`);
        return rejected(name);
      }

      default:
        return noResultVariant();
    }
  } catch (error) {
    console.error("PublishQuote failed:", error);
    return outcomeFromError(error);
  }
}
