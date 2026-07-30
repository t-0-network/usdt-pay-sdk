package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentIntentRequest;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentIntentResponse;
import network.t0.pay.proto.tzero.v1.pay.QrOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * §4 CreatePaymentIntent — opens an intent for a sale. t-0 calls the Issuer inline
 * and returns the QR options the customer picks from.
 *
 * <p>Idempotency key: {@code paymentRef}, your own sale id, unique per acquirer.
 * On a retry send the <em>same</em> paymentRef with identical content and t-0
 * replays the original result. A new UUID per attempt would open a second intent
 * for one sale — so mint paymentRef when the sale is created, not when this call is
 * made.
 */
public final class CreatePaymentIntent {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentIntent.class);

    public static void create(AcquirerServiceGrpc.AcquirerServiceBlockingStub t0, long quoteId) {
        // TODO: Step 2.2 — use your order id here, persisted with the sale.
        String paymentRef = UUID.randomUUID().toString();

        CreatePaymentIntentRequest request = CreatePaymentIntentRequest.newBuilder()
                .setPaymentRef(paymentRef)
                .setLocalAmount(Decimals.of("100000.00"))
                // Fiat settlement: the quote from §3 carries the currency and the rate.
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
            CreatePaymentIntentResponse response = t0.createPaymentIntent(request);

            switch (response.getResultCase()) {
                case SUCCESS -> {
                    CreatePaymentIntentResponse.Success success = response.getSuccess();
                    log.info("Intent {} for sale {}: customer pays exactly {} USDt, expires at {}",
                            success.getPaymentIntentId(),
                            paymentRef,
                            Decimals.format(success.getAmountUsdt()),
                            success.getExpiresAt());

                    // TODO: Step 2.2 — store paymentIntentId against your sale, then render
                    //       one QR per option. renderablePayload is chain-native; encode it
                    //       as-is, do not rebuild it from the address and the amount.
                    for (QrOption option : success.getQrOptionsList()) {
                        log.info("  QR option — chain={} address={} payload={}",
                                option.getChain(),
                                option.getDepositAddress(),
                                option.getRenderablePayload());
                    }
                }
                case FAILURE ->
                        // ISSUER_UNAVAILABLE / ADDRESS_POOL_EMPTY / AMOUNT_OUT_OF_RANGE /
                        // QUOTE_EXPIRED / QUOTE_INSUFFICIENT_HEADROOM.
                        // A failure never consumes paymentRef: fix the input and resend the
                        // same ref.
                        log.warn("Intent for sale {} declined: {}",
                                paymentRef, response.getFailure().getReason());
                default -> log.warn("CreatePaymentIntent returned no result variant");
            }
        } catch (StatusRuntimeException e) {
            // Transport failure — you do not know whether t-0 opened the intent.
            // Retry with the same paymentRef; that is what the key is for.
            log.error("CreatePaymentIntent failed for sale {}: {}", paymentRef, e.getStatus(), e);
        }
    }

    private CreatePaymentIntent() {
    }
}
