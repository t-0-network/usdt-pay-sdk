import { create } from "@bufbuild/protobuf";
import { timestampDate } from "@bufbuild/protobuf/wkt";
import type { ServiceImpl } from "@connectrpc/connect";
import {
  Blockchain,
  CreatePaymentInstructionsResponse_Failure_Reason,
  CreatePaymentInstructionsResponseSchema,
  type Decimal,
  type IssuerCallbackService,
  type QrOption,
  QrOptionSchema,
} from "@t-0/usdt-pay-sdk";
import { decimalToString, decimalToUnits } from "./internal/decimals.js";

/**
 * §5 CreatePaymentInstructions — the only callback t-0 pushes to the issuer.
 *
 * Synchronous and on the critical path: t-0 calls it inline while the acquirer waits
 * on §4, so answer fast and never block on anything slow.
 *
 * Delivered at least once, dedup key `paymentIntentId`. A repeat for an id you already
 * reserved must return the *same* addresses rather than burning a second set out of
 * the pool — so reserve under the id, and look it up before allocating.
 */
export const issuerCallbackHandler: ServiceImpl<typeof IssuerCallbackService> = {
  async createPaymentInstructions(request) {
    console.log(
      `§5 reserve: intent=${request.paymentIntentId} acquirer=${request.acquirerId} ` +
        `amount=${request.amountUsdt ? decimalToString(request.amountUsdt) : "?"} USDt ` +
        `until ${request.expiresAt ? timestampDate(request.expiresAt).toISOString() : "?"}`,
    );

    // TODO: Step 2.1 — look up paymentIntentId first and return the existing
    //       reservation if you have one; only then take fresh addresses from the pool.
    // TODO: Step 2.2 — resolve the settlement wallet for request.acquirerId from your
    //       onboarding mapping. You will need it for §9 SettlementSent, and resolving
    //       it yourself is what makes t-0's on-chain check a real cross-check rather
    //       than an echo of its own input.
    // TODO: Step 2.3 — hold the reservation until request.expiresAt, then release the
    //       addresses.
    // TODO: Step 2.4 — no free addresses, or the amount is outside your range? Answer
    //       with the failure variant (ADDRESS_POOL_EMPTY / AMOUNT_OUT_OF_RANGE /
    //       ISSUER_UNAVAILABLE) instead of throwing — which is what the unimplemented
    //       default below already does.

    // Declines until you implement the steps above. Deliberate: whatever addresses
    // this returns are rendered by the POS as a payable QR and the customer sends real
    // USDt to them. A decline costs one sale; an address you do not control costs the
    // customer their money, irreversibly.
    return create(CreatePaymentInstructionsResponseSchema, {
      result: {
        case: "failure",
        value: { reason: CreatePaymentInstructionsResponse_Failure_Reason.ISSUER_UNAVAILABLE },
      },
    });

    // TODO: Step 2.3 — delete the decline above and return this instead, once the
    //       addresses come from your own pool. One option per chain you support for
    //       this intent; the customer picks one. The two hex constants are the real
    //       USDt contracts on each chain and stay as they are — it is the deposit
    //       addresses that must become yours.
    //
    // const amountUsdt = request.amountUsdt!;
    // return create(CreatePaymentInstructionsResponseSchema, {
    //   result: {
    //     case: "success",
    //     value: {
    //       qrOptions: [
    //         tron(yourTronDepositAddress, amountUsdt),
    //         evm(Blockchain.ETH, 1, "0xdAC17F958D2ee523a2206206994597C13D831ec7",
    //             yourEthDepositAddress, amountUsdt),
    //         evm(Blockchain.BSC, 56, "0x55d398326f99059fF775485246999027B3197955",
    //             yourBscDepositAddress, amountUsdt),
    //       ],
    //       expiresAt: request.expiresAt,
    //     },
    //   },
    // });
  },
};

/**
 * renderablePayload is chain-native and the POS encodes it as a QR image without
 * touching it — so it has to be complete and correct here.
 */
function tron(depositAddress: string, amountUsdt: Decimal): QrOption {
  // TRON wallets read a TIP-681-style URI; amount is in USDt units.
  return create(QrOptionSchema, {
    chain: Blockchain.TRON,
    depositAddress,
    renderablePayload: `tron:${depositAddress}?amount=${decimalToString(amountUsdt)}`,
  });
}

function evm(
  chain: Blockchain,
  chainId: number,
  usdtContract: string,
  depositAddress: string,
  amountUsdt: Decimal,
): QrOption {
  // ERC-681: pay <amount> of the USDt contract to <depositAddress> on <chainId>.
  // USDt is 6 decimals on both Ethereum and BSC-pegged deployments here.
  const units = decimalToUnits(amountUsdt, 6);
  return create(QrOptionSchema, {
    chain,
    depositAddress,
    renderablePayload: `ethereum:${usdtContract}@${chainId}/transfer?address=${depositAddress}&uint256=${units}`,
  });
}
