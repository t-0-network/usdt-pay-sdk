import { create } from "@bufbuild/protobuf";
import { timestampDate, timestampFromDate } from "@bufbuild/protobuf/wkt";
import type { ServiceImpl } from "@connectrpc/connect";
import {
  Blockchain,
  CreatePaymentInstructionsResponseSchema,
  type CreatePaymentInstructionsResponse_Success_DepositOption as DepositOption,
  CreatePaymentInstructionsResponse_Success_DepositOptionSchema as DepositOptionSchema,
  type IssuerCallbackService,
} from "@t-0/usdt-pay-sdk";
import { decimalToString } from "./internal/decimals.js";

// TODO: Step 2.3 — replace with addresses from your own pool
const EXAMPLE_ETH_DEPOSIT_ADDRESS = "0x000000000000000000000000000000000000dEaD";
const EXAMPLE_BSC_DEPOSIT_ADDRESS = "0x000000000000000000000000000000000000dEaD";

/**
 * CreatePaymentInstructions — the only callback t-0 pushes to the issuer.
 *
 * Synchronous and on the critical path: t-0 calls it inline while the acquirer waits
 * on CreatePaymentIntent, so answer fast and never block on anything slow.
 *
 * Delivered at least once, dedup key `paymentIntentId`. A repeat for an id you already
 * reserved must return the *same* addresses rather than burning a second set out of
 * the pool — so reserve under the id, and look it up before allocating.
 */
export const issuerCallbackHandler: ServiceImpl<typeof IssuerCallbackService> = {
  async createPaymentInstructions(request) {
    console.log(
      `CreatePaymentInstructions: intent=${request.paymentIntentId} acquirer=${request.acquirerId} ` +
        `amount=${request.amountUsdt ? decimalToString(request.amountUsdt) : "?"} USDt ` +
        `until ${request.expiresAt ? timestampDate(request.expiresAt).toISOString() : "?"}`,
    );

    // TODO: Step 2.1 — look up paymentIntentId first and return the existing
    //       reservation if you have one; only then take fresh addresses from the pool.
    // TODO: Step 2.2 — resolve the settlement wallet for request.acquirerId from your
    //       onboarding mapping. You will need it for SettlementSent, and resolving
    //       it yourself is what makes t-0's on-chain check a real cross-check rather
    //       than an echo of its own input.
    // TODO: Step 2.3 — swap the two example address constants above for addresses
    //       from your own pool, and hold the reservation until request.expiresAt.
    // TODO: Step 2.4 — no free addresses, or the amount is outside your range? Answer
    //       with the failure variant (ADDRESS_POOL_EMPTY / AMOUNT_OUT_OF_RANGE /
    //       ISSUER_UNAVAILABLE) instead of throwing.

    // expires_at is required on the response but the request's gt_now lacks `required`
    const expiresAt = request.expiresAt ?? timestampFromDate(new Date(Date.now() + 2 * 60_000));

    return create(CreatePaymentInstructionsResponseSchema, {
      result: {
        case: "success",
        value: {
          depositOptions: [
            // depositOption(Blockchain.TRON, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", yourTronDepositAddress) — not live yet
            depositOption(Blockchain.ETH, "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                EXAMPLE_ETH_DEPOSIT_ADDRESS),
            depositOption(Blockchain.BSC, "0x55d398326f99059fF775485246999027B3197955",
                EXAMPLE_BSC_DEPOSIT_ADDRESS),
          ],
          expiresAt,
        },
      },
    });
  },
};

/**
 * A deposit option is facts, not a rendering: the chain, the reserved address and the
 * USDT contract on that chain. The POS builds the QR or wallet link from these and the
 * intent's amount, with the token decimals t-0 adds on the way to the acquirer.
 */
function depositOption(chain: Blockchain, usdtContract: string, depositAddress: string): DepositOption {
  return create(DepositOptionSchema, {
    chain,
    depositAddress,
    tokenContract: usdtContract,
  });
}
