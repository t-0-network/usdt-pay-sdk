package network.t0.pay.lp.handler;

import io.grpc.stub.StreamObserver;
import network.t0.pay.lp.internal.Decimals;
import network.t0.pay.lp.internal.Times;
import network.t0.pay.proto.tzero.v1.pay.ExecuteQuoteRequest;
import network.t0.pay.proto.tzero.v1.pay.ExecuteQuoteResponse;
import network.t0.pay.proto.tzero.v1.pay.LpCallbackServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * §8 ExecuteQuote — the only callback t-0 pushes to the LP.
 *
 * <p>Not a request you can decline. t-0 binds you to the standing quote's locked
 * rate for one authorized sale at the moment it <em>transmits</em> this message;
 * the response is an acknowledgment, and the obligation exists whether or not it
 * arrives.
 *
 * <p><b>Delivered at least once.</b> t-0 retries until you acknowledge, and the ack
 * means "recorded, stop retrying". Returning before {@code executionId} is durably
 * written throws the obligation away: the retries stop and nothing on your side
 * knows you owe the money. Write first, then return.
 *
 * <p>Dedup key: {@code executionId}. It is also your handle for the obligation —
 * §10 FiatSettlementSent reports settlements as lists of executionIds.
 */
public class LpCallbackHandler extends LpCallbackServiceGrpc.LpCallbackServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(LpCallbackHandler.class);

    @Override
    public void executeQuote(
            ExecuteQuoteRequest request,
            StreamObserver<ExecuteQuoteResponse> responseObserver) {

        log.info("§8 execution {}: owe {} to acquirer {} for {} USDt (quote {}, your ref {}) at {}",
                request.getExecutionId(),
                Decimals.format(request.getLocalAmount()),
                request.getAcquirerId(),
                Decimals.format(request.getAmountUsdt()),
                request.getQuoteId(),
                request.getQuoteRef(),
                Times.format(request.getExecutedAt()));

        // TODO: Step 3.1 — dedup on executionId and write the obligation down before
        //       returning: acquirerId, localAmount, amountUsdt.
        // TODO: Step 3.2 — resolve the acquirer's registered bank destination from
        //       acquirerId; you will need it for §10.
        //
        // quoteRef is a correlation echo, not the key: it lets you attribute the
        // execution even when it lands before you have recorded t-0's quoteId for
        // that publish.

        responseObserver.onNext(ExecuteQuoteResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
