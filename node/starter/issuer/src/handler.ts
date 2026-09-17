import { create } from "@bufbuild/protobuf";
import { timestampDate, timestampFromDate } from "@bufbuild/protobuf/wkt";
import type { ServiceImpl } from "@connectrpc/connect";
import {
  Blockchain,
  CreatePaymentInstructionsResponseSchema,
  type Decimal,
  type IssuerCallbackService,
  type DepositOption,
  DepositOptionSchema,
} from "@t-0/usdt-pay-sdk";
import { decimalToString, decimalToUnits } from "./internal/decimals.js";

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

    const amountUsdt = request.amountUsdt!;
    // expires_at is required on the response but the request's gt_now lacks `required`
    const expiresAt = request.expiresAt ?? timestampFromDate(new Date(Date.now() + 2 * 60_000));

    return create(CreatePaymentInstructionsResponseSchema, {
      result: {
        case: "success",
        value: {
          depositOptions: [
            // tron("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", yourTronDepositAddress, amountUsdt) — not live yet
            evm(Blockchain.ETH, 1, "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                EXAMPLE_ETH_DEPOSIT_ADDRESS, amountUsdt),
            evm(Blockchain.BSC, 56, "0x55d398326f99059fF775485246999027B3197955",
                EXAMPLE_BSC_DEPOSIT_ADDRESS, amountUsdt),
          ],
          expiresAt,
        },
      },
    });
  },
};

/**
 * `paymentUri` is chain-native and the POS encodes it as a QR image without
 * touching it — so it has to be complete and correct here. `tokenContract` is
 * the USDT contract on that chain.
 */
function tron(usdtContract: string, depositAddress: string, amountUsdt: Decimal): DepositOption {
  // TRON wallets read a TIP-681-style URI; amount is in USDt units.
  return create(DepositOptionSchema, {
    chain: Blockchain.TRON,
    depositAddress,
    paymentUri: `tron:${depositAddress}?amount=${decimalToString(amountUsdt)}`,
    tokenContract: usdtContract,
  });
}

function evm(
  chain: Blockchain,
  chainId: number,
  usdtContract: string,
  depositAddress: string,
  amountUsdt: Decimal,
): DepositOption {
  // ERC-681: pay <amount> of the USDt contract to <depositAddress> on <chainId>.
  // USDt is 6 decimals on both Ethereum and BSC-pegged deployments here.
  const units = decimalToUnits(amountUsdt, 6);
  return create(DepositOptionSchema, {
    chain,
    depositAddress,
    paymentUri: `ethereum:${usdtContract}@${chainId}/transfer?address=${depositAddress}&uint256=${units}`,
    tokenContract: usdtContract,
  });
}
