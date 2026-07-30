package network.t0.pay.issuer.internal;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.common.Decimal;
import network.t0.pay.proto.tzero.v1.pay.Blockchain;
import network.t0.pay.proto.tzero.v1.pay.IssuerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.PaymentReceivedRequest;
import network.t0.pay.proto.tzero.v1.pay.PaymentReceivedResponse;
import network.t0.pay.proto.tzero.v1.pay.UsdtOnChainPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * §6 PaymentReceived — the customer's transfer is final on-chain and KYT-cleared.
 * This call is what authorizes the sale: t-0 fires §7 to the acquirer off it, and
 * from here you own the on-chain risk and are obligated to settle.
 *
 * <p>Idempotency key: {@code paymentIntentId}. Retry with the same id and identical
 * content until t-0 answers; it is delivered at least once by design.
 *
 * <p>{@code amountUsdt} must equal the intent's stored amount <em>exactly</em> —
 * t-0 rejects anything else with AMOUNT_MISMATCH.
 */
public final class PaymentReceived {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceived.class);

    public static void report(
            IssuerServiceGrpc.IssuerServiceBlockingStub t0,
            long paymentIntentId,
            Decimal amountUsdt,
            Blockchain chain,
            String txHash,
            String senderAddress,
            Instant receivedAt) {

        PaymentReceivedRequest request = PaymentReceivedRequest.newBuilder()
                .setPaymentIntentId(paymentIntentId)
                .setAmountUsdt(amountUsdt)
                .setUsdtOnChain(UsdtOnChainPayment.newBuilder()
                        .setChain(chain)
                        .setOnChainTxHash(txHash)
                        .setSenderAddress(senderAddress)
                        .build())
                .setReceivedAt(Timestamp.newBuilder()
                        .setSeconds(receivedAt.getEpochSecond())
                        .setNanos(receivedAt.getNano())
                        .build())
                .build();

        try {
            PaymentReceivedResponse response = t0.paymentReceived(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> log.info("§6 accepted: intent={} {} USDt via {}",
                        paymentIntentId, Decimals.format(amountUsdt), txHash);
                case REJECTED ->
                        // INTENT_EXPIRED / UNKNOWN_INTENT / AMOUNT_MISMATCH.
                        // A rejection is an acknowledgment — stop retrying. The key is not
                        // consumed: correct the fields and resend the same paymentIntentId.
                        // A payment that arrived late or in the wrong amount is yours to
                        // refund; it does not become the acquirer's problem.
                        log.warn("§6 rejected for intent {}: {}",
                                paymentIntentId, response.getRejected().getReason());
                default -> log.warn("PaymentReceived returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            // TODO: Step 3.1 — no acknowledgment means retry with backoff, same key,
            //       identical content. Drive it off your durable record, not this catch block.
            log.error("§6 failed for intent {}: {}", paymentIntentId, e.getStatus(), e);
        }
    }

    private PaymentReceived() {
    }
}
