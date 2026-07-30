package network.t0.pay.issuer.internal;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.common.Decimal;
import network.t0.pay.proto.tzero.v1.pay.Blockchain;
import network.t0.pay.proto.tzero.v1.pay.IssuerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.OnChainSettlementDetails;
import network.t0.pay.proto.tzero.v1.pay.SettlementSentRequest;
import network.t0.pay.proto.tzero.v1.pay.SettlementSentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

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

    public static void report(
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
                .setSettledAt(Timestamp.newBuilder()
                        .setSeconds(settledAt.getEpochSecond())
                        .setNanos(settledAt.getNano())
                        .build())
                .build();

        try {
            SettlementSentResponse response = t0.settlementSent(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> log.info("§9 accepted: ref={} {} USDt covering {}",
                        settlementRef, Decimals.format(amountUsdt), settledPaymentIntentIds);
                case REJECTED ->
                        // ON_CHAIN_UNCONFIRMED — resend the same ref once the tx confirms.
                        // AMOUNT_MISMATCH / WRONG_DESTINATION / INTENT_NOT_SETTLEABLE — fix
                        // the fields (failingIntentIds names the offenders) and resend the
                        // same ref. A rejection never consumes the key.
                        log.warn("§9 rejected for ref {}: {} (failing intents {})",
                                settlementRef,
                                response.getRejected().getReason(),
                                response.getRejected().getFailingIntentIdsList());
                default -> log.warn("SettlementSent returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            // TODO: Step 3.2 — no acknowledgment means retry with backoff, same ref,
            //       identical content. Never broadcast a second transfer to "retry".
            log.error("§9 failed for ref {}: {}", settlementRef, e.getStatus(), e);
        }
    }

    private SettlementSent() {
    }
}
