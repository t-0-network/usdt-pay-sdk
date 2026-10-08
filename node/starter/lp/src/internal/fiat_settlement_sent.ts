import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  type Client,
  Code,
  ConnectError,
  type Decimal,
  type LpService,
  type FiatSettlementSentResponse_Accepted,
  FiatSettlementSentResponse_Rejected_Reason,
} from "@t-0/usdt-pay-sdk";
import { decimalToString } from "./decimals.js";
import {
  accepted,
  noResultVariant,
  outcomeFromError,
  rejected,
  unknown,
  type Outcome,
} from "./outcome.js";

/**
 * FiatSettlementSent — you wired local fiat to the acquirer over bank rails;
 * t-0 verifies the covered executions.
 *
 * Idempotency key: `bankTransferRef`, your own id, unique per LP. It identifies one
 * real bank transfer: a second wire is a new settlement under a new ref, never a
 * correction of an old one.
 *
 * `FAILED_PRECONDITION` means an execution in the batch has no durable result yet —
 * retry the same request. The contract says the same `bankTransferRef` with the same
 * content is safe to resend.
 */
export async function reportFiatSettlementSent(
  t0: Client<typeof LpService>,
  settlement: {
    bankTransferRef: string;
    settledExecutionIds: bigint[];
    localCurrency: string;
    settlementAmount: Decimal;
    settledAt: Date;
  },
): Promise<Outcome<FiatSettlementSentResponse_Accepted>> {
  try {
    const response = await t0.fiatSettlementSent({
      bankTransferRef: settlement.bankTransferRef,
      settledExecutionIds: settlement.settledExecutionIds,
      localCurrency: settlement.localCurrency,
      settlementAmount: settlement.settlementAmount,
      settledAt: timestampFromDate(settlement.settledAt),
    });

    switch (response.result.case) {
      case "accepted":
        console.log(
          `FiatSettlementSent accepted: ref=${settlement.bankTransferRef} ${decimalToString(settlement.settlementAmount)} ${settlement.localCurrency} covering ${settlement.settledExecutionIds}`,
        );
        return accepted(response.result.value);

      case "rejected": {
        const { reason } = response.result.value;
        const name = FiatSettlementSentResponse_Rejected_Reason[reason] ?? String(reason);
        console.warn(
          `FiatSettlementSent rejected for ref ${settlement.bankTransferRef}: ${name}`,
        );
        return rejected(name);
      }

      default:
        return noResultVariant();
    }
  } catch (error) {
    // Never wire a second transfer to "retry" — resend this same ref.
    console.error(`FiatSettlementSent failed for ref ${settlement.bankTransferRef}:`, error);
    // Everywhere else FAILED_PRECONDITION is a refusal, but not here. The contract says
    // so on this RPC (tzero/v1/pay/lp/lp.proto:25): "An execution still awaiting its
    // durable result returns FAILED_PRECONDITION; retry the same request."
    if (error instanceof ConnectError && error.code === Code.FailedPrecondition) {
      return unknown(error.message);
    }
    return outcomeFromError(error);
  }
}
