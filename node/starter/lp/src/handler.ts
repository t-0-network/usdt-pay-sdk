import { create } from "@bufbuild/protobuf";
import type { ServiceImpl } from "@connectrpc/connect";
import {
  type LpCallbackService,
  ExecuteQuoteResponseSchema,
} from "@t-0/usdt-pay-sdk";
import { decimalToString } from "./internal/decimals.js";

/**
 * ExecuteQuote — the only callback t-0 pushes to the LP.
 *
 * Synchronous: t-0 calls it when a payment is authorized against one of your
 * standing quotes. Your answer is durable — Accepted creates your firm obligation
 * to pay `localAmount` to the acquirer over bank rails.
 *
 * Delivered at least once, dedup key `executionId`. A repeat for an id you already
 * decided on must return the *same* decision — look it up before deciding.
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
};
