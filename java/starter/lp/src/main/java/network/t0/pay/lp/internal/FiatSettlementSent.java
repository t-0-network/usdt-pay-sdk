package network.t0.pay.lp.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.FiatSettlementSentRequest;
import network.t0.pay.proto.tzero.v1.pay.FiatSettlementSentResponse;
import network.t0.pay.proto.tzero.v1.pay.LpServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    /**
     * Longer than the 10s default Main installs: the money already left your bank, so
     * it is worth waiting rather than turning a transfer that landed into an
     * {@link Outcome.Unknown} you have to reconcile.
     */
    private static final int TIMEOUT_SECONDS = 15;

    public static Outcome<FiatSettlementSentResponse.Accepted> report(
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
                .setSettledAt(Times.from(settledAt))
                .build();

        try {
            FiatSettlementSentResponse response =
                    t0.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS).fiatSettlementSent(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> {
                    log.info("§10 accepted: ref={} {} {} covering {}",
                            bankTransferRef, Decimals.format(settlementAmount), localCurrency,
                            settledExecutionIds);
                    return new Outcome.Accepted<>(response.getAccepted());
                }
                case REJECTED -> {
                    // EXECUTION_UNKNOWN / EXECUTION_ALREADY_COVERED / CURRENCY_MISMATCH /
                    // AMOUNT_MISMATCH / DESTINATION_MISMATCH / ACQUIRER_MIXED.
                    // The money already moved: the fix is in what you reported, not another
                    // transfer. Correct the fields and resend the same ref.
                    FiatSettlementSentResponse.Rejected rejected = response.getRejected();
                    log.warn("§10 rejected for ref {}: {} (failing executions {})",
                            bankTransferRef, rejected.getReason(), rejected.getFailingExecutionIdsList());
                    return new Outcome.Rejected<>(
                            rejected.getReason().name(), rejected.getFailingExecutionIdsList());
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            log.error("§10 failed for ref {}: {}", bankTransferRef, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private FiatSettlementSent() {
    }
}
