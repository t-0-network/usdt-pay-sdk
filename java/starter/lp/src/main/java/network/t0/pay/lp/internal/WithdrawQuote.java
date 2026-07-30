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

    public static void withdraw(LpServiceGrpc.LpServiceBlockingStub t0, long quoteId) {
        try {
            WithdrawQuoteResponse response = t0.withdrawQuote(WithdrawQuoteRequest.newBuilder()
                    .setQuoteId(quoteId)
                    .build());

            switch (response.getResultCase()) {
                case SUCCESS -> log.info("§2 withdrew quote {}", quoteId);
                case FAILURE ->
                        // QUOTE_UNKNOWN — unknown id, or a quote that belongs to another LP.
                        log.warn("§2 declined for quote {}: {}",
                                quoteId, response.getFailure().getReason());
                default -> log.warn("WithdrawQuote returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            log.error("§2 failed for quote {}: {}", quoteId, e.getStatus(), e);
        }
    }

    private WithdrawQuote() {
    }
}
