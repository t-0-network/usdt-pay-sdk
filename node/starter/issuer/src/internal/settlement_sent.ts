import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  type Blockchain,
  type Client,
  type Decimal,
  type IssuerService,
  type SettlementSentResponse_Accepted,
  SettlementSentResponse_Rejected_Reason,
} from "@t-0/usdt-pay-sdk";
import { decimalToString } from "./decimals.js";
import { accepted, noResultVariant, outcomeFromError, rejected, type Outcome } from "./outcome.js";

/**
 * Longer than the 10s the other two calls use: the transfer is already broadcast, so
 * it is worth waiting rather than turning a settlement that landed into an `unknown`
 * you have to reconcile.
 */
const TIMEOUT_MS = 15_000;

/**
 * §9 SettlementSent — you broadcast a USDt settlement transfer; t-0 verifies it
 * on-chain against the intents it covers.
 *
 * Idempotency key: `settlementRef`, your own id, unique per issuer. It identifies one
 * real transfer: a second broadcast is a new settlement under a new ref, never a
 * correction of an old one.
 *
 * `destinationAddress` is the wallet you resolved from `acquirerId` at §5 — the
 * acquirer's in USDt mode, that acquirer's LP in fiat mode.
 */
export async function reportSettlementSent(
  t0: Client<typeof IssuerService>,
  settlement: {
    settlementRef: string;
    amountUsdt: Decimal;
    chain: Blockchain;
    txHash: string;
    destinationAddress: string;
    settledPaymentIntentIds: bigint[];
    settledAt: Date;
  },
): Promise<Outcome<SettlementSentResponse_Accepted>> {
  try {
    const response = await t0.settlementSent(
      {
        settlementRef: settlement.settlementRef,
        amountUsdt: settlement.amountUsdt,
        settlement: {
          chain: settlement.chain,
          onChainTxHash: settlement.txHash,
          destinationAddress: settlement.destinationAddress,
        },
        settledPaymentIntentIds: settlement.settledPaymentIntentIds,
        settledAt: timestampFromDate(settlement.settledAt),
      },
      { timeoutMs: TIMEOUT_MS },
    );

    switch (response.result.case) {
      case "accepted":
        console.log(
          `§9 accepted: ref=${settlement.settlementRef} ${decimalToString(settlement.amountUsdt)} USDt covering ${settlement.settledPaymentIntentIds}`,
        );
        return accepted(response.result.value);

      case "rejected": {
        // ON_CHAIN_UNCONFIRMED — resend the same ref once the tx confirms.
        // AMOUNT_MISMATCH / WRONG_DESTINATION / INTENT_NOT_SETTLEABLE — fix the fields
        // named by failingIntentIds and resend the same ref.
        const { reason, failingIntentIds } = response.result.value;
        const name = SettlementSentResponse_Rejected_Reason[reason] ?? String(reason);
        console.warn(
          `§9 rejected for ref ${settlement.settlementRef}: ${name} (failing intents ${failingIntentIds})`,
        );
        return rejected(name, failingIntentIds);
      }

      default:
        return noResultVariant();
    }
  } catch (error) {
    // Never broadcast a second transfer to "retry" — resend this same ref.
    console.error(`§9 failed for ref ${settlement.settlementRef}:`, error);
    return outcomeFromError(error);
  }
}
