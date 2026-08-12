package network.t0.pay.issuer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.Blockchain;
import network.t0.pay.proto.tzero.v1.pay.IssuerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.OnChainSettlementDetails;
import network.t0.pay.proto.tzero.v1.pay.SettlementSentRequest;
import network.t0.pay.proto.tzero.v1.pay.SettlementSentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * §9 SettlementSent — you broadcast a USDt settlement transfer; t-0 verifies it
 * on-chain against the intents it covers.
 *
 * <p>Idempotency key: {@code settlementRef}, your own id, unique per issuer. It
 * identifies one real transfer: a second broadcast is a new settlement under a new
 * ref, never a correction of an old one.
 *
 * <p>{@code destinationAddress} is the wallet you resolved from acquirerId at §5 —
 * the acquirer's in USDt mode, that acquirer's LP in fiat mode.
 */
public final class SettlementSent {

    private static final Logger log = LoggerFactory.getLogger(SettlementSent.class);

    /**
     * Longer than the 10s default Main installs: the transfer is already broadcast, so
     * it is worth waiting rather than turning a settlement that landed into an
     * {@link Outcome.Unknown} you have to reconcile.
     */
    private static final int TIMEOUT_SECONDS = 15;

    public static Outcome<SettlementSentResponse.Accepted> report(
            IssuerServiceGrpc.IssuerServiceBlockingStub t0,
            String settlementRef,
            Decimal amountUsdt,
            Blockchain chain,
            String txHash,
            String destinationAddress,
            List<Long> settledPaymentIntentIds,
            Instant settledAt) {

        SettlementSentRequest request = SettlementSentRequest.newBuilder()
                .setSettlementRef(settlementRef)
                .setAmountUsdt(amountUsdt)
                .setSettlement(OnChainSettlementDetails.newBuilder()
                        .setChain(chain)
                        .setOnChainTxHash(txHash)
                        .setDestinationAddress(destinationAddress)
                        .build())
                .addAllSettledPaymentIntentIds(settledPaymentIntentIds)
                .setSettledAt(Times.from(settledAt))
                .build();

        try {
            SettlementSentResponse response =
                    t0.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS).settlementSent(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> {
                    log.info("§9 accepted: ref={} {} USDt covering {}",
                            settlementRef, Decimals.format(amountUsdt), settledPaymentIntentIds);
                    return new Outcome.Accepted<>(response.getAccepted());
                }
                case REJECTED -> {
                    // ON_CHAIN_UNCONFIRMED — resend the same ref once the tx confirms.
                    // AMOUNT_MISMATCH / WRONG_DESTINATION / INTENT_NOT_SETTLEABLE — fix the
                    // fields named by failingIntentIds and resend the same ref.
                    SettlementSentResponse.Rejected rejected = response.getRejected();
                    log.warn("§9 rejected for ref {}: {} (failing intents {})",
                            settlementRef, rejected.getReason(), rejected.getFailingIntentIdsList());
                    return new Outcome.Rejected<>(
                            rejected.getReason().name(), rejected.getFailingIntentIdsList());
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            // Never broadcast a second transfer to "retry" — resend this same ref.
            log.error("§9 failed for ref {}: {}", settlementRef, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private SettlementSent() {
    }
}
