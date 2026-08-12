package network.t0.pay.issuer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
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

    public static Outcome<PaymentReceivedResponse.Accepted> report(
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
                .setReceivedAt(Times.from(receivedAt))
                .build();

        try {
            PaymentReceivedResponse response = t0.paymentReceived(request);

            switch (response.getResultCase()) {
                case ACCEPTED -> {
                    log.info("§6 accepted: intent={} {} USDt via {}",
                            paymentIntentId, Decimals.format(amountUsdt), txHash);
                    return new Outcome.Accepted<>(response.getAccepted());
                }
                case REJECTED -> {
                    // INTENT_EXPIRED / UNKNOWN_INTENT / AMOUNT_MISMATCH.
                    // A payment that arrived late or in the wrong amount is yours to
                    // refund; it does not become the acquirer's problem.
                    String reason = response.getRejected().getReason().name();
                    log.warn("§6 rejected for intent {}: {}", paymentIntentId, reason);
                    return new Outcome.Rejected<>(reason);
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            log.error("§6 failed for intent {}: {}", paymentIntentId, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private PaymentReceived() {
    }
}
