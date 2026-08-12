package network.t0.pay.lp.internal;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import network.t0.pay.proto.tzero.v1.pay.LpServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.PublishQuoteRequest;
import network.t0.pay.proto.tzero.v1.pay.PublishQuoteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How to test against t-0 without t-0: stand a fake up in memory and hand the helper
 * a real stub pointed at it. Copy this shape for your own tests.
 *
 * <p>The generated stubs are {@code final} with private constructors, so they cannot
 * be subclassed or mocked — faking the <em>service</em> is the way in.
 */
class PublishQuoteTest {

    private Server server;
    private ManagedChannel channel;

    /** Stands up a fake t-0 that answers publishQuote however the test says. */
    private LpServiceGrpc.LpServiceBlockingStub t0(
            Consumer<StreamObserver<PublishQuoteResponse>> answer) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new LpServiceGrpc.LpServiceImplBase() {
                    @Override
                    public void publishQuote(
                            PublishQuoteRequest request,
                            StreamObserver<PublishQuoteResponse> responseObserver) {
                        answer.accept(responseObserver);
                    }
                })
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        return LpServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void acceptedWhenTZeroPublishesTheQuote() throws IOException {
        // One quote in, one PublishedQuote back, in request order.
        var t0 = t0(observer -> {
            observer.onNext(PublishQuoteResponse.newBuilder()
                    .setSuccess(PublishQuoteResponse.Success.newBuilder()
                            .addQuotes(PublishQuoteResponse.Success.PublishedQuote.newBuilder()
                                    .setQuoteId(4242)))
                    .build());
            observer.onCompleted();
        });

        Outcome<PublishQuoteResponse.Success.PublishedQuote> outcome =
                PublishQuote.publish(t0, Duration.ofSeconds(90));

        assertInstanceOf(Outcome.Accepted.class, outcome);
        assertEquals(4242, outcome.value().orElseThrow().getQuoteId());
        assertFalse(outcome.shouldRetry());
    }

    @Test
    void rejectedIsAnAnswer_soDoNotRetryIt() throws IOException {
        var t0 = t0(observer -> {
            observer.onNext(PublishQuoteResponse.newBuilder()
                    .setFailure(PublishQuoteResponse.Failure.newBuilder()
                            .setReason(PublishQuoteResponse.Failure.Reason.REASON_VALIDITY_INVALID))
                    .build());
            observer.onCompleted();
        });

        Outcome<PublishQuoteResponse.Success.PublishedQuote> outcome =
                PublishQuote.publish(t0, Duration.ofSeconds(90));

        assertInstanceOf(Outcome.Rejected.class, outcome);
        assertFalse(outcome.shouldRetry(), "a rejection is an acknowledgment — fix and resend, do not spin");
    }

    /**
     * The branch a happy-path integration test never reaches, and the one that costs
     * money: no answer came back, so the quote may or may not be standing.
     */
    @Test
    void noAnswerBecomesUnknown_whichMustBeRetriedUnderTheSameRef() throws IOException {
        var t0 = t0(observer -> observer.onError(Status.UNAVAILABLE.asRuntimeException()));

        Outcome<PublishQuoteResponse.Success.PublishedQuote> outcome =
                PublishQuote.publish(t0, Duration.ofSeconds(90));

        assertInstanceOf(Outcome.Unknown.class, outcome);
        assertTrue(outcome.shouldRetry());
    }
}
