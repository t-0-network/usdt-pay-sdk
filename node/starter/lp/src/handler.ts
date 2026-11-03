import { create } from "@bufbuild/protobuf";
import {
  type LpCallbackService,
  ExecuteQuoteResponseSchema,
  type ServiceImpl,
  UsdtSettlementCompletedResponseSchema,
} from "@t-0/usdt-pay-sdk";
import { decimalToString } from "./internal/decimals.js";

/**
 * The two callbacks t-0 pushes to the LP.
 *
 * ExecuteQuote — synchronous: t-0 calls it when a payment is authorized against one
 * of your standing quotes. Your answer is durable — Accepted creates your firm
 * obligation to pay `localAmount` to the acquirer over bank rails.
 *
 * Delivered at least once, dedup key `executionId`. A repeat for an id you already
 * decided on must return the *same* decision — look it up before deciding.
 *
 * UsdtSettlementCompleted — t-0 verified on-chain that the issuer's USDt reached your
 * wallet, and lists the executions it covers. The transfer itself carries no reference,
 * so this is how you match the credit to your executions. A failed verdict, or no
 * verdict at all, sends nothing: until this arrives, the transfer is unconfirmed.
 *
 * Delivered at least once, dedup key `settlementId`. A repeat must answer success
 * without counting the transfer twice.
 */
export const lpCallbackHandler: ServiceImpl<typeof LpCallbackService> = {
  async executeQuote(request) {
    console.log(
      `ExecuteQuote: execution=${request.executionId} quote=${request.quoteId} ref=${request.quoteRef} ` +
        `acquirer=${request.acquirerId} ` +
        `local=${request.localAmount ? decimalToString(request.localAmount) : "?"} ${request.localCurrency} ` +
        `usdt=${request.amountUsdt ? decimalToString(request.amountUsdt) : "?"} USDt`,
    );

    // TODO: Step 3.1 — look up executionId first and return the decision you
    //       already made for it. Only decide when there is none — a retry must
    //       not flip the result.
    // TODO: Step 3.2 — record the execution durably under executionId before
    //       returning. Accepted is your firm obligation to pay localAmount to
    //       the acquirer; you receive amountUsdt at settlement.
    // TODO: Step 3.3 — to decline, return:
    //       { result: { case: "rejected", value: {
    //           reason: ExecuteQuoteResponse_Rejected_Reason.OTHER,
    //           details: "your reason here"
    //       } } }
    //       details must be non-blank. A Rejected execution routes the payment
    //       to manual handling.

    return create(ExecuteQuoteResponseSchema, {
      result: { case: "accepted", value: {} },
    });
  },

  async usdtSettlementCompleted(request) {
    console.log(
      `UsdtSettlementCompleted: settlement=${request.settlementId} ` +
        `usdt=${request.settlementAmount ? decimalToString(request.settlementAmount) : "?"} USDt ` +
        `executions=${request.settledExecutionIds.join(",")}`,
    );

    // TODO: Step 5.1 — look up settlementId first. On a repeat, answer success
    //       without counting the transfer twice.
    // TODO: Step 5.2 — record which executions this settlement covers, durably
    //       under settlementId, before returning. settledExecutionIds is ascending
    //       and may list an execution you rejected or have not answered or received
    //       yet, so an id you do not recognize is not an error.

    return create(UsdtSettlementCompletedResponseSchema);
  },
};
