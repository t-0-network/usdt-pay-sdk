package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.common.Decimal;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.GetPaymentQuoteRequest;
import network.t0.pay.proto.tzero.v1.pay.GetPaymentQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalLong;

/**
 * §3 GetPaymentQuote — prices an upcoming fiat sale against your LP's standing
 * quotes. Fiat-settlement acquirers only; in USDt mode you run your own FX and
 * pass the rate straight to §4.
 *
 * <p>Stateless lookup, no idempotency key: it always returns the current standing
 * quote. The returned quoteId is what §4 references.
 */
public final class GetPaymentQuote {

    private static final Logger log = LoggerFactory.getLogger(GetPaymentQuote.class);

    /**
     * @return t-0's quoteId when a standing quote covers the sale, empty otherwise
     */
    public static OptionalLong fetch(AcquirerServiceGrpc.AcquirerServiceBlockingStub t0) {
        // TODO: Step 2.1 — take currency and amount from the sale on the POS.
        String localCurrency = "COP";
        Decimal localAmount = Decimals.of("100000.00");

        try {
            GetPaymentQuoteResponse response = t0.getPaymentQuote(GetPaymentQuoteRequest.newBuilder()
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
                            success.getExpiresAt());
                    return OptionalLong.of(success.getQuoteId());
                }
                case FAILURE -> {
                    // QUOTE_UNAVAILABLE — your LP is not quoting this currency right now.
                    // AMOUNT_OUT_OF_RANGE — no standing quote's per-sale bounds cover it.
                    // TODO: Step 2.1 — tell the POS the sale cannot be priced.
                    log.warn("No quote for {} {}: {}",
                            Decimals.format(localAmount), localCurrency,
                            response.getFailure().getReason());
                }
                default -> log.warn("GetPaymentQuote returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            log.error("GetPaymentQuote failed: {}", e.getStatus(), e);
        }

        return OptionalLong.empty();
    }

    private GetPaymentQuote() {
    }
}
