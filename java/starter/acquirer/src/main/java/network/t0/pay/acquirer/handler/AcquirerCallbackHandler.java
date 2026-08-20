package network.t0.pay.acquirer.handler;

import io.grpc.stub.StreamObserver;
import network.t0.pay.proto.tzero.v1.pay.AcquirerPaymentExpiredRequest;
import network.t0.pay.proto.tzero.v1.pay.AcquirerPaymentExpiredResponse;
import network.t0.pay.proto.tzero.v1.pay.AcquirerCallbackServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.PaymentAuthorizedRequest;
import network.t0.pay.proto.tzero.v1.pay.PaymentAuthorizedResponse;
import network.t0.pay.proto.tzero.v1.pay.SettlementCompletedRequest;
import network.t0.pay.proto.tzero.v1.pay.SettlementCompletedResponse;
import network.t0.pay.proto.tzero.v1.pay.SettlementInitiatedRequest;
import network.t0.pay.proto.tzero.v1.pay.SettlementInitiatedResponse;
import network.t0.pay.acquirer.internal.Decimals;
import network.t0.pay.acquirer.internal.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The callbacks t-0 pushes to the acquirer: PaymentAuthorized, SettlementInitiated,
 * SettlementCompleted, PaymentExpired.
 *
 * <p><b>Every one of these is delivered at least once.</b> t-0 retries with backoff
 * until you acknowledge, and your acknowledgment means "recorded, stop retrying".
 * Acknowledging before the event is durably written throws it away: the retries
 * stop and nothing on your side remembers it happened. So each method must, in
 * this order — write the event under its dedup key, and only then return.
 *
 * <p>Dedup key per callback:
 * <ul>
 *   <li>PaymentAuthorized — {@code paymentIntentId}</li>
 *   <li>SettlementInitiated — {@code fiatSettlementId}</li>
 *   <li>SettlementCompleted — {@code settlementId}</li>
 *   <li>PaymentExpired — {@code paymentIntentId}</li>
 * </ul>
 * A repeat delivery under a key you have already written is a no-op that you
 * still acknowledge.
 *
 * <p>Scope the key <b>per callback</b>, not globally: PaymentAuthorized and
 * PaymentExpired are both keyed on {@code paymentIntentId}, so a single
 * {@code processed(key)} table collides the expiry of an intent with its
 * authorization and silently drops one of them.
 */
public class AcquirerCallbackHandler extends AcquirerCallbackServiceGrpc.AcquirerCallbackServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AcquirerCallbackHandler.class);

    /**
     * PaymentAuthorized — the sale is approved and the Issuer is now obligated to
     * settle it. This is the moment to release the goods; settlement follows later.
     */
    @Override
    public void paymentAuthorized(
            PaymentAuthorizedRequest request,
            StreamObserver<PaymentAuthorizedResponse> responseObserver) {

        log.info("PaymentAuthorized: intent={} sale={} tx={} at {}",
                request.getPaymentIntentId(),
                request.getPaymentRef(),
                request.getUsdtOnChain().getOnChainTxHash(),
                Times.format(request.getApprovedAt()));

        // TODO: Step 3.1 — dedup on paymentIntentId, mark the sale authorized, tell
        //       the POS. Write it before returning; the ack stops the retries.

        responseObserver.onNext(PaymentAuthorizedResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /**
     * SettlementInitiated — pre-notice that the LP sent a bank transfer, naming the
     * reference to expect on your statement. Fiat mode only. Not proof of receipt:
     * the intent settles on your SettlementReceived call, not on this.
     */
    @Override
    public void settlementInitiated(
            SettlementInitiatedRequest request,
            StreamObserver<SettlementInitiatedResponse> responseObserver) {

        log.info("SettlementInitiated: fiatSettlementId={} lp={} ref={} {} {} covering {}",
                request.getFiatSettlementId(),
                request.getLpId(),
                request.getBankTransferRef(),
                Decimals.format(request.getSettlementAmount()),
                request.getLocalCurrency(),
                request.getSettledPaymentIntentIdsList());

        // TODO: Step 3.2 — dedup on fiatSettlementId, then record (lpId, bankTransferRef)
        //       as a transfer to watch for on the bank statement.
        // See Step 4.1 — when reconciliation matches it, call
        //       SettlementReceived.confirm(stub, lpId, bankTransferRef, ...).

        responseObserver.onNext(SettlementInitiatedResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /**
     * SettlementCompleted — settlement verified on-chain. USDt mode only; in fiat
     * mode your own SettlementReceived call is terminal and this never fires.
     */
    @Override
    public void settlementCompleted(
            SettlementCompletedRequest request,
            StreamObserver<SettlementCompletedResponse> responseObserver) {

        log.info("SettlementCompleted: settlementId={} amount={} tx={} covering {}",
                request.getSettlementId(),
                Decimals.format(request.getSettlementAmount()),
                request.getSettlement().getOnChainTxHash(),
                request.getSettledPaymentIntentIdsList());

        // USDt only — leave as a no-op in fiat mode. Dedup on settlementId, then
        // close out every intent in settledPaymentIntentIds.

        responseObserver.onNext(SettlementCompletedResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    /**
     * PaymentExpired — the intent expired with no payment. Terminal: drop the QR
     * and clear the pending order.
     */
    @Override
    public void paymentExpired(
            AcquirerPaymentExpiredRequest request,
            StreamObserver<AcquirerPaymentExpiredResponse> responseObserver) {

        log.info("PaymentExpired: intent={} sale={} at {}",
                request.getPaymentIntentId(),
                request.getPaymentRef(),
                Times.format(request.getExpiredAt()));

        // TODO: Step 3.4 — dedup on paymentIntentId, cancel the pending sale, take the
        //       QR off the POS.

        responseObserver.onNext(AcquirerPaymentExpiredResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
