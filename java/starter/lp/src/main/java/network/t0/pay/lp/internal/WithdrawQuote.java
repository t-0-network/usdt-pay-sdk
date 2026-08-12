package network.t0.pay.lp.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.LpServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.WithdrawQuoteRequest;
import network.t0.pay.proto.tzero.v1.pay.WithdrawQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * §2 WithdrawQuote — takes one standing quote out of the Order Book before it
 * expires.
 *
 * <p>Executions already accepted against it are unaffected: withdrawing stops new
 * sales from pricing off it, it does not cancel obligations you already have.
 *
 * <p>Idempotency key: {@code quoteId}.
 */
public final class WithdrawQuote {

    private static final Logger log = LoggerFactory.getLogger(WithdrawQuote.class);

    public static Outcome<WithdrawQuoteResponse.Success> withdraw(
            LpServiceGrpc.LpServiceBlockingStub t0, long quoteId) {
        try {
            WithdrawQuoteResponse response = t0.withdrawQuote(WithdrawQuoteRequest.newBuilder()
                    .setQuoteId(quoteId)
                    .build());

            switch (response.getResultCase()) {
                case SUCCESS -> {
                    log.info("§2 withdrew quote {}", quoteId);
                    return new Outcome.Accepted<>(response.getSuccess());
                }
                case FAILURE -> {
                    // QUOTE_UNKNOWN — unknown id, or a quote that belongs to another LP.
                    // Either way it is not standing, so there is nothing left to withdraw.
                    String reason = response.getFailure().getReason().name();
                    log.warn("§2 declined for quote {}: {}", quoteId, reason);
                    return new Outcome.Rejected<>(reason);
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            // The quote is still standing as far as you know — keep the id and retry.
            log.error("§2 failed for quote {}: {}", quoteId, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private WithdrawQuote() {
    }
}
