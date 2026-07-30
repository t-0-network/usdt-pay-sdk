package network.t0.pay.lp.internal;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.LpServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.PublishQuoteRequest;
import network.t0.pay.proto.tzero.v1.pay.PublishQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * §1 PublishQuote — pushes one immutable standing quote into t-0's Order Book.
 *
 * <p>Multi-consumable while it stands: any number of sales can execute against it
 * between min and max. It is not a per-sale offer, so price it as a rate you are
 * happy to be held to repeatedly until it expires or you withdraw it (§2).
 *
 * <p>Idempotency key: {@code quoteRef}, your own id, unique per LP. Retrying with
 * the same ref returns the original quoteId; a new ref publishes a second quote.
 */
public final class PublishQuote {

    private static final Logger log = LoggerFactory.getLogger(PublishQuote.class);

    /**
     * @return t-0's quoteId when the quote was accepted, empty otherwise
     */
    public static OptionalLong publish(LpServiceGrpc.LpServiceBlockingStub t0, Duration validity) {
        // TODO: Step 2.1 — mint quoteRef from your own pricing run and persist it with
        //       the quote, so a retry after a lost response reuses it instead of
        //       publishing a duplicate.
        String quoteRef = UUID.randomUUID().toString();

        Instant expiresAt = Instant.now().plus(validity);

        PublishQuoteRequest request = PublishQuoteRequest.newBuilder()
                .setQuoteRef(quoteRef)
                // TODO: Step 2.1 — your currency, your rate, your per-sale bounds.
                .setLocalCurrency("COP")
                // Rate is local currency per 1 USDt.
                .setFxRate(Decimals.of("4100.00"))
                .setMinAmountUsdt(Decimals.of("1.00"))
                .setMaxAmountUsdt(Decimals.of("5000.00"))
                .setExpiresAt(Timestamp.newBuilder()
                        .setSeconds(expiresAt.getEpochSecond())
                        .setNanos(expiresAt.getNano())
                        .build())
                .build();

        try {
            PublishQuoteResponse response = t0.publishQuote(request);

            switch (response.getResultCase()) {
                case SUCCESS -> {
                    long quoteId = response.getSuccess().getQuoteId();
                    log.info("§1 published quote {} (ref {}), standing until {}",
                            quoteId, quoteRef, expiresAt);
                    return OptionalLong.of(quoteId);
                }
                case FAILURE ->
                        // CURRENCY_UNSUPPORTED / LIMITS_INVALID / VALIDITY_INVALID.
                        log.warn("§1 declined for ref {}: {}",
                                quoteRef, response.getFailure().getReason());
                default -> log.warn("PublishQuote returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            log.error("§1 failed for ref {}: {}", quoteRef, e.getStatus(), e);
        }

        return OptionalLong.empty();
    }

    private PublishQuote() {
    }
}
