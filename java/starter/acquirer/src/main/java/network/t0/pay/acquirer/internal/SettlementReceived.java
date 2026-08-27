package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.acquirer.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.acquirer.SettlementReceivedRequest;
import network.t0.pay.proto.tzero.v1.pay.acquirer.SettlementReceivedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * SettlementReceived — you are the oracle for the bank leg. Fiat mode only.
 *
 * <p>Call this when the LP's transfer actually shows up on your bank statement,
 * never when SettlementInitiated arrives: that is a pre-notice naming the
 * reference to watch for, and the intent only reaches SETTLED on your
 * SettlementReceived call.
 *
 * <p>Idempotency key: the pair (lpId, bankTransferRef) — bankTransferRef is minted
 * by the LP and unique only per LP. On {@link Outcome#shouldRetry()} resend the
 * same pair with identical content.
 */
public final class SettlementReceived {

    private static final Logger log = LoggerFactory.getLogger(SettlementReceived.class);

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
            SettlementReceivedResponse response = t0.settlementReceived(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> {
                    log.info("Settlement {} from LP {} confirmed: {} {}",
                            bankTransferRef, lpId, Decimals.format(amountReceived), localCurrency);
                    // TODO: Step 4.1 — record that this fiat leg is confirmed; the intent is now SETTLED.
                    return new Outcome.Accepted<>(response.getAccepted());
                }
                case REJECTED -> {
                    // AMOUNT_MISMATCH / UNKNOWN_TRANSFER / CURRENCY_MISMATCH.
                    String reason = response.getRejected().getReason().name();
                    log.warn("Settlement {} from LP {} rejected: {}", bankTransferRef, lpId, reason);
                    return new Outcome.Rejected<>(reason);
                }
                default -> {
                    // A result variant this stub does not know — the contract can add one before 1.0.
                    // Unknown, not Rejected: we cannot tell whether t-0 committed, and Rejected's
                    // follow-up is a fresh idempotency key.
                    return new Outcome.Unknown<>("response carried an unrecognised result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            log.error("SettlementReceived failed for {}: {}", bankTransferRef, e.getStatus());
            // TODO: Step 4.2 — retry with backoff, same (lpId, bankTransferRef) pair.
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private SettlementReceived() {
    }
}
