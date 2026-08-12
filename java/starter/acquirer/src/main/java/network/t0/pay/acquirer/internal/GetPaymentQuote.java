package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.GetPaymentQuoteRequest;
import network.t0.pay.proto.tzero.v1.pay.GetPaymentQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * §3 GetPaymentQuote — prices an upcoming fiat sale against your LP's standing
 * quotes. Fiat-settlement acquirers only; in USDt mode you run your own FX and
 * pass the rate straight to §4.
 *
 * <p>Stateless lookup, no idempotency key: it always returns the current standing
 * quote, so an {@link Outcome.Unknown} here is safe to retry as often as you like.
 * The returned quoteId is what §4 references.
 */
public final class GetPaymentQuote {

    private static final Logger log = LoggerFactory.getLogger(GetPaymentQuote.class);

    /** A quote is only useful while the customer is still standing there. */
    private static final int TIMEOUT_SECONDS = 5;

    public static Outcome<GetPaymentQuoteResponse.Success> fetch(
            AcquirerServiceGrpc.AcquirerServiceBlockingStub t0,
            String localCurrency,
            Decimal localAmount) {

        try {
            // Deadline on every call: the stub handed out by BlockingNetworkClient
            // carries none, so a stalled t-0 blocks this thread indefinitely.
            GetPaymentQuoteResponse response = t0.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getPaymentQuote(GetPaymentQuoteRequest.newBuilder()
                            .setLocalCurrency(localCurrency)
                            .setLocalAmount(localAmount)
                            .build());

            switch (response.getResultCase()) {
                case SUCCESS -> {
                    GetPaymentQuoteResponse.Success success = response.getSuccess();
                    log.info("Quote {}: {} {} costs {} USDt at rate {}, expires at {}",
                            success.getQuoteId(),
                            Decimals.format(localAmount),
                            localCurrency,
                            Decimals.format(success.getAmountUsdt()),
                            Decimals.format(success.getFxRate()),
                            Times.format(success.getExpiresAt()));
                    return new Outcome.Accepted<>(success);
                }
                case FAILURE -> {
                    // QUOTE_UNAVAILABLE — your LP is not quoting this currency right now.
                    // TODO: Step 2.1 — tell the POS the sale cannot be priced.
                    String reason = response.getFailure().getReason().name();
                    log.warn("No quote for {} {}: {}",
                            Decimals.format(localAmount), localCurrency, reason);
                    return new Outcome.Rejected<>(reason);
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            log.error("GetPaymentQuote failed: {}", e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private GetPaymentQuote() {
    }
}
