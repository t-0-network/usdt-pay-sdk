import { timestampFromDate } from "@bufbuild/protobuf/wkt";
import {
  type Client,
  type IssuerPaymentExpiredResponse_Accepted,
  IssuerPaymentExpiredResponse_Rejected_Reason,
  type IssuerService,
} from "@t-0/usdt-pay-sdk";
import { accepted, noResultVariant, outcomeFromError, rejected, type Outcome } from "./outcome.js";

const TIMEOUT_MS = 10_000;

/**
 * §14 PaymentExpired — the reservation closed with no valid payment and you have
 * released the deposit addresses back to the pool.
 *
 * Confirmation, not a trigger: t-0 expires the intent on its own clock at `expiresAt`
 * and tells the acquirer via §15 regardless of this call.
 *
 * Idempotency key: `paymentIntentId`.
 */
export async function reportPaymentExpired(
  t0: Client<typeof IssuerService>,
  expiry: { paymentIntentId: bigint; expiredAt: Date },
): Promise<Outcome<IssuerPaymentExpiredResponse_Accepted>> {
  try {
    const response = await t0.paymentExpired(
      {
        paymentIntentId: expiry.paymentIntentId,
        expiredAt: timestampFromDate(expiry.expiredAt),
      },
      { timeoutMs: TIMEOUT_MS },
    );

    switch (response.result.case) {
      case "accepted":
        console.log(`§14 accepted: intent=${expiry.paymentIntentId} released`);
        return accepted(response.result.value);

      case "rejected": {
        // UNKNOWN_INTENT — t-0 never opened this intent. Look at where the id came
        // from rather than resending.
        const { reason } = response.result.value;
        const name = IssuerPaymentExpiredResponse_Rejected_Reason[reason] ?? String(reason);
        console.warn(`§14 rejected for intent ${expiry.paymentIntentId}: ${name}`);
        return rejected(name);
      }

      default:
        return noResultVariant();
    }
  } catch (error) {
    console.error(`§14 failed for intent ${expiry.paymentIntentId}:`, error);
    return outcomeFromError(error);
  }
}
