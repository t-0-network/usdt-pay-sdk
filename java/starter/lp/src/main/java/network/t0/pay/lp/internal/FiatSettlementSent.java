package network.t0.pay.lp.internal;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.common.Decimal;
import network.t0.pay.proto.tzero.v1.pay.FiatSettlementSentRequest;
import network.t0.pay.proto.tzero.v1.pay.FiatSettlementSentResponse;
import network.t0.pay.proto.tzero.v1.pay.LpServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * §10 FiatSettlementSent — you released a bank transfer to the acquirer covering a
 * batch of your executions.
 *
 * <p>Self-initiated: nothing from t-0 tells you to pay. You decide when to batch and
 * send, then report it here. t-0 relays it to the acquirer as §11 so it knows which
 * reference to watch for, and the intent settles on the acquirer's §12.
 *
 * <p>Idempotency key: {@code bankTransferRef}, your reference on the transfer,
 * unique per LP. One ref is one real transfer — a second transfer is a new ref.
 *
 * <p>{@code settlementAmount} must equal the sum of the covered executions' local
 * amounts, and one transfer credits one acquirer.
 */
public final class FiatSettlementSent {

    private static final Logger log = LoggerFactory.getLogger(FiatSettlementSent.class);

    public static void report(
            LpServiceGrpc.LpServiceBlockingStub t0,
            String bankTransferRef,
            List<Long> settledExecutionIds,
            String localCurrency,
            Decimal settlementAmount,
            String destinationAccount,
            Instant settledAt) {

        FiatSettlementSentRequest request = FiatSettlementSentRequest.newBuilder()
                .setBankTransferRef(bankTransferRef)
                .addAllSettledExecutionIds(settledExecutionIds)
                .setLocalCurrency(localCurrency)
                .setSettlementAmount(settlementAmount)
                .setDestinationAccount(destinationAccount)
                .setSettledAt(Timestamp.newBuilder()
                        .setSeconds(settledAt.getEpochSecond())
                        .setNanos(settledAt.getNano())
                        .build())
                .build();

        try {
            FiatSettlementSentResponse response = t0.fiatSettlementSent(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> log.info("§10 accepted: ref={} {} {} covering {}",
                        bankTransferRef, Decimals.format(settlementAmount), localCurrency,
                        settledExecutionIds);
                case REJECTED ->
                        // EXECUTION_UNKNOWN / EXECUTION_ALREADY_COVERED / CURRENCY_MISMATCH /
                        // AMOUNT_MISMATCH / DESTINATION_MISMATCH / ACQUIRER_MIXED.
                        // A rejection is an acknowledgment — stop retrying. The key is not
                        // consumed: correct the fields (failingExecutionIds names the
                        // offenders) and resend the same ref. The money already moved, so
                        // the fix is in what you reported, not another transfer.
                        log.warn("§10 rejected for ref {}: {} (failing executions {})",
                                bankTransferRef,
                                response.getRejected().getReason(),
                                response.getRejected().getFailingExecutionIdsList());
                default -> log.warn("FiatSettlementSent returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            // TODO: Step 4.1 — no acknowledgment means retry with backoff, same ref,
            //       identical content. Drive it off your durable record, not this catch block.
            log.error("§10 failed for ref {}: {}", bankTransferRef, e.getStatus(), e);
        }
    }

    private FiatSettlementSent() {
    }
}
