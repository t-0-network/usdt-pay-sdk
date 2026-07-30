package network.t0.pay.issuer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.IssuerPaymentExpiredRequest;
import network.t0.pay.proto.tzero.v1.pay.IssuerPaymentExpiredResponse;
import network.t0.pay.proto.tzero.v1.pay.IssuerServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

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

    private static final int TIMEOUT_SECONDS = 10;

    public static Outcome<IssuerPaymentExpiredResponse.Accepted> report(
            IssuerServiceGrpc.IssuerServiceBlockingStub t0,
            long paymentIntentId,
            Instant expiredAt) {

        IssuerPaymentExpiredRequest request = IssuerPaymentExpiredRequest.newBuilder()
                .setPaymentIntentId(paymentIntentId)
                .setExpiredAt(Times.from(expiredAt))
                .build();

        try {
            IssuerPaymentExpiredResponse response =
                    t0.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS).paymentExpired(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> {
                    log.info("§14 accepted: intent={} released", paymentIntentId);
                    return new Outcome.Accepted<>(response.getAccepted());
                }
                case REJECTED -> {
                    // UNKNOWN_INTENT — t-0 never opened this intent. Look at where the id
                    // came from rather than resending.
                    String reason = response.getRejected().getReason().name();
                    log.warn("§14 rejected for intent {}: {}", paymentIntentId, reason);
                    return new Outcome.Rejected<>(reason);
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            log.error("§14 failed for intent {}: {}", paymentIntentId, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private PaymentExpired() {
    }
}
