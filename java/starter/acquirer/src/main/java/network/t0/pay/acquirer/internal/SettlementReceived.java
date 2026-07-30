package network.t0.pay.acquirer.internal;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.common.Decimal;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.SettlementReceivedRequest;
import network.t0.pay.proto.tzero.v1.pay.SettlementReceivedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * §12 SettlementReceived — you are the oracle for the bank leg. Fiat mode only.
 *
 * <p>Call this when the LP's transfer actually shows up on your bank statement,
 * never when §11 SettlementInitiated arrives: §11 is a pre-notice naming the
 * reference to watch for, and the intent only reaches SETTLED on your §12.
 *
 * <p>Idempotency key: the pair (lpId, bankTransferRef) — bankTransferRef is minted
 * by the LP and unique only per LP. Retry with the same pair until t-0 answers.
 */
public final class SettlementReceived {

    private static final Logger log = LoggerFactory.getLogger(SettlementReceived.class);

    public static void confirm(
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
                .setReceivedAt(Timestamp.newBuilder()
                        .setSeconds(receivedAt.getEpochSecond())
                        .setNanos(receivedAt.getNano())
                        .build())
                .build();

        try {
            SettlementReceivedResponse response = t0.settlementReceived(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> log.info("Settlement {} from LP {} confirmed: {} {}",
                        bankTransferRef, lpId, Decimals.format(amountReceived), localCurrency);
                case REJECTED ->
                        // AMOUNT_MISMATCH / UNKNOWN_TRANSFER / CURRENCY_MISMATCH.
                        // A rejection is an acknowledgment — stop retrying. It does not consume
                        // the key: correct the fields and resend the same (lpId, ref) pair.
                        log.warn("Settlement {} from LP {} rejected: {}",
                                bankTransferRef, lpId, response.getRejected().getReason());
                default -> log.warn("SettlementReceived returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            // TODO: Step 4.2 — no acknowledgment means retry with backoff, same key,
            //       identical content. Drive it off your durable record, not this catch block.
            log.error("SettlementReceived failed for {}: {}", bankTransferRef, e.getStatus(), e);
        }
    }

    private SettlementReceived() {
    }
}
