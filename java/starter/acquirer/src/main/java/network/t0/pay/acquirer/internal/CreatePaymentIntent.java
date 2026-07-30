package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentIntentRequest;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentIntentResponse;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.QrOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * §4 CreatePaymentIntent — opens an intent for a sale. t-0 calls the Issuer inline
 * and returns the QR options the customer picks from.
 *
 * <p>Idempotency key: {@code paymentRef}, your own sale id, unique per acquirer.
 * Mint it when the sale is created and store it with the sale — <em>not</em> here.
 * On {@link Outcome.Unknown} resend the same paymentRef with identical content and
 * t-0 replays the original result; a fresh id per attempt opens a second intent for
 * one sale.
 */
public final class CreatePaymentIntent {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentIntent.class);

    /** t-0 calls the Issuer inline, so this is the slowest call on the POS path. */
    private static final int TIMEOUT_SECONDS = 10;

    /**
     * @param paymentRef your sale id, stable across retries of the same sale
     * @param quoteId    the standing quote from §3 — it carries the currency and the rate
     */
    public static Outcome<CreatePaymentIntentResponse.Success> create(
            AcquirerServiceGrpc.AcquirerServiceBlockingStub t0,
            String paymentRef,
            Decimal localAmount,
            long quoteId) {

        CreatePaymentIntentRequest request = CreatePaymentIntentRequest.newBuilder()
                .setPaymentRef(paymentRef)
                .setLocalAmount(localAmount)
                .setFiatSettlement(CreatePaymentIntentRequest.FiatSettlementTerms.newBuilder()
                        .setQuoteId(quoteId)
                        .build())
                // USDt settlement instead? Drop the block above, run your own FX, and send:
                // .setUsdtSettlement(CreatePaymentIntentRequest.UsdtSettlementTerms.newBuilder()
                //         .setLocalCurrency("COP")
                //         .setFxRate(Decimals.of("4100.00"))
                //         .build())
                .build();

        try {
            CreatePaymentIntentResponse response =
                    t0.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS).createPaymentIntent(request);

            switch (response.getResultCase()) {
                case SUCCESS -> {
                    CreatePaymentIntentResponse.Success success = response.getSuccess();
                    log.info("Intent {} for sale {}: {} {} at rate {}, customer pays exactly {} USDt, expires at {}",
                            success.getPaymentIntentId(),
                            paymentRef,
                            Decimals.format(success.getLocalAmount()),
                            success.getLocalCurrency(),
                            Decimals.format(success.getFxRate()),
                            Decimals.format(success.getAmountUsdt()),
                            Times.format(success.getExpiresAt()));

                    // TODO: Step 2.2 — store paymentIntentId against your sale, then render
                    //       one QR per option. renderablePayload is a chain-native URI;
                    //       encode it as-is, do not rebuild it from the address and the amount.
                    for (QrOption option : success.getQrOptionsList()) {
                        log.info("  QR option — chain={} address={} payload={}",
                                option.getChain(),
                                option.getDepositAddress(),
                                option.getRenderablePayload());
                    }
                    return new Outcome.Accepted<>(success);
                }
                case FAILURE -> {
                    // ISSUER_UNAVAILABLE / ADDRESS_POOL_EMPTY / AMOUNT_OUT_OF_RANGE /
                    // QUOTE_EXPIRED / QUOTE_INSUFFICIENT_HEADROOM.
                    String reason = response.getFailure().getReason().name();
                    log.warn("Intent for sale {} declined: {}", paymentRef, reason);
                    return new Outcome.Rejected<>(reason);
                }
                default -> {
                    return new Outcome.Rejected<>("response carried no result variant");
                }
            }
        } catch (StatusRuntimeException e) {
            // You do not know whether t-0 opened the intent. Retry the same paymentRef.
            log.error("CreatePaymentIntent failed for sale {}: {}", paymentRef, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private CreatePaymentIntent() {
    }
}
