package network.t0.pay.issuer.internal;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.IssuerPaymentExpiredRequest;
import network.t0.pay.proto.tzero.v1.pay.IssuerPaymentExpiredResponse;
import network.t0.pay.proto.tzero.v1.pay.IssuerServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * §14 PaymentExpired — the reservation closed with no valid payment and you have
 * released the deposit addresses back to the pool.
 *
 * <p>Confirmation, not a trigger: t-0 expires the intent on its own clock at
 * {@code expiresAt} and tells the acquirer via §15 regardless of this call.
 *
 * <p>Idempotency key: {@code paymentIntentId}.
 */
public final class PaymentExpired {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpired.class);

    public static void report(
            IssuerServiceGrpc.IssuerServiceBlockingStub t0,
            long paymentIntentId,
            Instant expiredAt) {

        IssuerPaymentExpiredRequest request = IssuerPaymentExpiredRequest.newBuilder()
                .setPaymentIntentId(paymentIntentId)
                .setExpiredAt(Timestamp.newBuilder()
                        .setSeconds(expiredAt.getEpochSecond())
                        .setNanos(expiredAt.getNano())
                        .build())
                .build();

        try {
            IssuerPaymentExpiredResponse response = t0.paymentExpired(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> log.info("§14 accepted: intent={} released", paymentIntentId);
                case REJECTED ->
                        // UNKNOWN_INTENT — t-0 never opened this intent. Stop retrying and
                        // look at where the id came from.
                        log.warn("§14 rejected for intent {}: {}",
                                paymentIntentId, response.getRejected().getReason());
                default -> log.warn("PaymentExpired returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            // TODO: Step 3.3 — no acknowledgment means retry with backoff, same key.
            log.error("§14 failed for intent {}: {}", paymentIntentId, e.getStatus(), e);
        }
    }

    private PaymentExpired() {
    }
}
