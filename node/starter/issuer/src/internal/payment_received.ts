import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  type Blockchain,
  type Client,
  type Decimal,
  type IssuerService,
  type PaymentReceivedResponse_Accepted,
  PaymentReceivedResponse_Rejected_Reason,
} from "@t-0/usdt-pay-sdk";
import { decimalToString } from "./decimals.js";
import { accepted, noResultVariant, outcomeFromError, rejected, type Outcome } from "./outcome.js";

const TIMEOUT_MS = 10_000;

/**
 * §6 PaymentReceived — the customer's transfer is final on-chain and KYT-cleared.
 * This call is what authorizes the sale: t-0 fires §7 to the acquirer off it, and from
 * here you own the on-chain risk and are obligated to settle.
 *
 * Idempotency key: `paymentIntentId`. Retry with the same id and identical content
 * until t-0 answers; it is delivered at least once by design.
 *
 * `amountUsdt` must equal the intent's stored amount *exactly* — t-0 rejects anything
 * else with AMOUNT_MISMATCH. Pass through the `Decimal` §5 handed you rather than
 * rebuilding it from your own records.
 */
export async function reportPaymentReceived(
  t0: Client<typeof IssuerService>,
  payment: {
    paymentIntentId: bigint;
    amountUsdt: Decimal;
    chain: Blockchain;
    txHash: string;
    senderAddress: string;
    receivedAt: Date;
  },
): Promise<Outcome<PaymentReceivedResponse_Accepted>> {
  try {
    const response = await t0.paymentReceived(
      {
        paymentIntentId: payment.paymentIntentId,
        amountUsdt: payment.amountUsdt,
        paymentMethod: {
          case: "usdtOnChain",
          value: {
            chain: payment.chain,
            onChainTxHash: payment.txHash,
            senderAddress: payment.senderAddress,
          },
        },
        receivedAt: timestampFromDate(payment.receivedAt),
        outcome: { case: "authorized", value: {} },
      },
      { timeoutMs: TIMEOUT_MS },
    );

    switch (response.result.case) {
      case "accepted":
        console.log(
          `§6 accepted: intent=${payment.paymentIntentId} ${decimalToString(payment.amountUsdt)} USDt via ${payment.txHash}`,
        );
        return accepted(response.result.value);

      case "rejected": {
        // INTENT_EXPIRED / UNKNOWN_INTENT / AMOUNT_MISMATCH.
        // A payment that arrived late or in the wrong amount is yours to refund; it
        // does not become the acquirer's problem.
        const { reason } = response.result.value;
        // Numeric on the wire; name it so a log line is greppable against the docs.
        const name = PaymentReceivedResponse_Rejected_Reason[reason] ?? String(reason);
        console.warn(`§6 rejected for intent ${payment.paymentIntentId}: ${name}`);
        return rejected(name);
      }

      default:
        return noResultVariant();
    }
  } catch (error) {
    console.error(`§6 failed for intent ${payment.paymentIntentId}:`, error);
    return outcomeFromError(error);
  }
}
