package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.SettlementReceivedRequest;
import network.t0.pay.proto.tzero.v1.pay.SettlementReceivedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * §12 SettlementReceived — you are the oracle for the bank leg. Fiat mode only.
 *
 * <p>Call this when the LP's transfer actually shows up on your bank statement,
 * never when §11 SettlementInitiated arrives: §11 is a pre-notice naming the
 * reference to watch for, and the intent only reaches SETTLED on your §12.
 *
 * <p>Idempotency key: the pair (lpId, bankTransferRef) — bankTransferRef is minted
 * by the LP and unique only per LP. On {@link Outcome#shouldRetry()} resend the
 * same pair with identical content.
 */
public final class SettlementReceived {

    private static final Logger log = LoggerFactory.getLogger(SettlementReceived.class);

    private static final int TIMEOUT_SECONDS = 10;

    public static Outcome<SettlementReceivedResponse.Accepted> confirm(
            AcquirerServiceGrpc.AcquirerServiceBlockingStub t0,
            long lpId,
            String bankTransferRef,
            String localCurrency,
            Decimal amountReceived,
            Instant receivedAt) {

        SettlementReceivedRequest request = SettlementReceivedRequest.newBuilder()
                .setLpId(lpId)
                .setBankTransferRef(bankTransferRef)
                .setLocalCurrency(localCurrency)
                .setAmountReceived(amountReceived)
                .setReceivedAt(Times.from(receivedAt))
                .build();

        try {
            SettlementReceivedResponse response =
                    t0.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS).settlementReceived(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> {
                    log.info("Settlement {} from LP {} confirmed: {} {}",
                            bankTransferRef, lpId, Decimals.format(amountReceived), localCurrency);
                    return new Outcome.Accepted<>(response.getAccepted());
                }
                case REJECTED -> {
                    // AMOUNT_MISMATCH / UNKNOWN_TRANSFER / CURRENCY_MISMATCH.
                    String reason = response.getRejected().getReason().name();
                    log.warn("Settlement {} from LP {} rejected: {}", bankTransferRef, lpId, reason);
                    return new Outcome.Rejected<>(reason);
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            log.error("SettlementReceived failed for {}: {}", bankTransferRef, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private SettlementReceived() {
    }
}
