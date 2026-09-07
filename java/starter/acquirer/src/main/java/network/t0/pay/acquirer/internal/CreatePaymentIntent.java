package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;
import network.t0.pay.proto.tzero.v1.pay.acquirer.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.acquirer.CreatePaymentIntentRequest;
import network.t0.pay.proto.tzero.v1.pay.acquirer.CreatePaymentIntentResponse;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.DepositOption;
import network.t0.pay.proto.tzero.v1.pay.acquirer.LocalAmount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CreatePaymentIntent — opens an intent for a sale. t-0 calls the Issuer inline
 * and returns the payment instructions (deposit options), which makes this the slowest
 * call on the POS path — it runs on the default 10s deadline.
 *
 * <p>Idempotency key: {@code idempotencyKey}, unique per acquirer. Mint it when the
 * sale is created and store it with the sale — <em>not</em> here. On
 * {@link Outcome.Unknown} resend the same key with identical content and t-0 replays
 * the original result; a fresh key per attempt opens a second intent for one sale.
 *
 * <p>{@code paymentRef} is your sale's correlation ref, echoed back on
 * PaymentAuthorized and PaymentExpired. It need not be unique, and it is not the
 * key: retrying a <em>declined</em> sale takes a fresh idempotencyKey under the
 * same paymentRef.
 */
public final class CreatePaymentIntent {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentIntent.class);

    /**
     * @param paymentRef     your sale id, echoed on PaymentAuthorized and PaymentExpired
     * @param idempotencyKey the retry identity, stable across retries of this call
     * @param localCurrency  three-letter ISO 4217 currency code (e.g. COP)
     * @param localAmount    fiat amount in localCurrency
     * @param quoteId        the standing quote from GetPaymentQuote
     */
    public static Outcome<CreatePaymentIntentResponse.Success> create(
            AcquirerServiceGrpc.AcquirerServiceBlockingStub t0,
            String paymentRef,
            String idempotencyKey,
            String localCurrency,
            Decimal localAmount,
            long quoteId) {

        CreatePaymentIntentRequest request = CreatePaymentIntentRequest.newBuilder()
                .setPaymentRef(paymentRef)
                .setIdempotencyKey(idempotencyKey)
                .setLocal(LocalAmount.newBuilder()
                        .setValue(localAmount)
                        .setCurrency(localCurrency)
                        .build())
                .setQuoteId(quoteId)
                // On-chain settlement instead? Drop setLocal/setQuoteId above and send:
                // .setSettlement(CreatePaymentIntentRequest.SettlementAmount.newBuilder()
                //         .setValue(usdtAmount)
                //         .build())
                .build();

        try {
            CreatePaymentIntentResponse response = t0.createPaymentIntent(request);

            switch (response.getResultCase()) {
                case SUCCESS -> {
                    CreatePaymentIntentResponse.Success success = response.getSuccess();
                    log.info("Intent {} for sale {}: {} USDt, expires at {}",
                            success.getPaymentIntentId(),
                            paymentRef,
                            Decimals.format(success.getSettlementAmount()),
                            Times.format(success.getExpiresAt()));

                    if (success.getSettlementCase() == CreatePaymentIntentResponse.Success.SettlementCase.FIAT) {
                        var fiat = success.getFiat();
                        log.info("  fiat settlement: {} {} at rate {}, quoteId={}",
                                Decimals.format(fiat.getLocal().getValue()),
                                fiat.getLocal().getCurrency(),
                                Decimals.format(fiat.getFxRate()),
                                fiat.getQuoteId());
                    }

                    // TODO: Step 2.2 — store paymentIntentId against your sale, then render
                    //       one deposit option per chain. paymentUri is chain-native;
                    //       encode it as-is, do not rebuild it from the address and the amount.
                    if (success.getInstructionsCase() == CreatePaymentIntentResponse.Success.InstructionsCase.USDT_ON_CHAIN) {
                        for (DepositOption option : success.getUsdtOnChain().getDepositOptionsList()) {
                            log.info("  deposit option — chain={} address={} uri={} contract={}",
                                    option.getChain(),
                                    option.getDepositAddress(),
                                    option.getPaymentUri(),
                                    option.getTokenContract());
                        }
                    }
                    return new Outcome.Accepted<>(success);
                }
                case FAILURE -> {
                    // ISSUER_UNAVAILABLE / ADDRESS_POOL_EMPTY / AMOUNT_OUT_OF_RANGE /
                    // QUOTE_EXPIRED / QUOTE_INSUFFICIENT_HEADROOM / QUOTE_UNAVAILABLE.
                    String reason = response.getFailure().getReason().name();
                    log.warn("Intent for sale {} declined: {}", paymentRef, reason);
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
            // You do not know whether t-0 opened the intent. Retry the same idempotencyKey.
            log.error("CreatePaymentIntent failed for sale {}: {}", paymentRef, e.getStatus());
            return new Outcome.Unknown<>(e.getStatus().toString());
        }
    }

    private CreatePaymentIntent() {
    }
}
