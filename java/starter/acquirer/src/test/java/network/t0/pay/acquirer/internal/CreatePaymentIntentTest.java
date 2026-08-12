package network.t0.pay.acquirer.internal;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.Blockchain;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentIntentRequest;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentIntentResponse;
import network.t0.pay.proto.tzero.v1.pay.QrOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
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
class CreatePaymentIntentTest {

    private static final String PAYMENT_REF = "sale-1";
    private static final String IDEMPOTENCY_KEY = "0f0a1b2c-3d4e-5f60-7182-93a4b5c6d7e8";
    private static final long QUOTE_ID = 77;

    private Server server;
    private ManagedChannel channel;
    private CreatePaymentIntentRequest sent;

    /** Stands up a fake t-0 that answers createPaymentIntent however the test says. */
    private AcquirerServiceGrpc.AcquirerServiceBlockingStub t0(
            Consumer<StreamObserver<CreatePaymentIntentResponse>> answer) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new AcquirerServiceGrpc.AcquirerServiceImplBase() {
                    @Override
                    public void createPaymentIntent(
                            CreatePaymentIntentRequest request,
                            StreamObserver<CreatePaymentIntentResponse> responseObserver) {
                        sent = request;
                        answer.accept(responseObserver);
                    }
                })
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        return AcquirerServiceGrpc.newBlockingStub(channel);
    }

    private Outcome<CreatePaymentIntentResponse.Success> create(
            AcquirerServiceGrpc.AcquirerServiceBlockingStub t0) {

        return CreatePaymentIntent.create(
                t0, PAYMENT_REF, IDEMPOTENCY_KEY, Decimals.of("100000.00"), QUOTE_ID);
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
    void acceptedWhenTZeroOpensTheIntent() throws IOException {
        // COP 100,000 at 4100, one QR option to render.
        var t0 = t0(observer -> {
            observer.onNext(CreatePaymentIntentResponse.newBuilder()
                    .setSuccess(CreatePaymentIntentResponse.Success.newBuilder()
                            .setPaymentIntentId(4242)
                            .setLocalCurrency("COP")
                            .setLocalAmount(Decimals.of("100000.00"))
                            .setFxRate(Decimals.of("4100.00"))
                            .setAmountUsdt(Decimals.of("24.39"))
                            .setExpiresAt(Times.from(Instant.parse("2026-01-01T00:05:00Z")))
                            .addQrOptions(QrOption.newBuilder()
                                    .setChain(Blockchain.BLOCKCHAIN_TRON)
                                    .setDepositAddress("TXYZexampleDepositAddress")
                                    .setRenderablePayload("tron:TXYZexampleDepositAddress?amount=24.39")))
                    .build());
            observer.onCompleted();
        });

        Outcome<CreatePaymentIntentResponse.Success> outcome = create(t0);

        assertInstanceOf(Outcome.Accepted.class, outcome);
        assertEquals(4242, outcome.value().orElseThrow().getPaymentIntentId());
        assertFalse(outcome.shouldRetry());

        // The caller's key reaches the wire unchanged. It is what makes the Unknown
        // branch below a retry rather than a second sale — a key minted inside
        // create() would be a fresh one on every attempt, and t-0 would open a
        // second intent for the same sale.
        assertEquals(IDEMPOTENCY_KEY, sent.getIdempotencyKey());
        assertEquals(PAYMENT_REF, sent.getPaymentRef());
        assertEquals(QUOTE_ID, sent.getFiatSettlement().getQuoteId());
    }

    @Test
    void rejectedIsAnAnswer_soDoNotRetryIt() throws IOException {
        var t0 = t0(observer -> {
            observer.onNext(CreatePaymentIntentResponse.newBuilder()
                    .setFailure(CreatePaymentIntentResponse.Failure.newBuilder()
                            .setReason(CreatePaymentIntentResponse.Failure.Reason.REASON_QUOTE_EXPIRED))
                    .build());
            observer.onCompleted();
        });

        Outcome<CreatePaymentIntentResponse.Success> outcome = create(t0);

        assertInstanceOf(Outcome.Rejected.class, outcome);
        assertFalse(outcome.shouldRetry(), "a rejection is an acknowledgment — re-quote and resend, do not spin");
    }

    /**
     * An answer this stub cannot read is not a decline. The contract may add a result
     * variant before 1.0, and an old stub sees it as RESULT_NOT_SET — treating that as
     * Rejected would send the next attempt under a fresh idempotency key, against an
     * intent t-0 may already have opened.
     */
    @Test
    void unrecognisedResultVariantBecomesUnknown_notRejected() throws IOException {
        var t0 = t0(observer -> {
            observer.onNext(CreatePaymentIntentResponse.getDefaultInstance());
            observer.onCompleted();
        });

        Outcome<CreatePaymentIntentResponse.Success> outcome = create(t0);

        assertInstanceOf(Outcome.Unknown.class, outcome);
        assertTrue(outcome.shouldRetry());
    }

    /**
     * The branch a happy-path integration test never reaches, and the one that costs
     * money: no answer came back, so the intent may or may not be open.
     */
    @Test
    void noAnswerBecomesUnknown_whichMustBeRetriedUnderTheSameKey() throws IOException {
        var t0 = t0(observer -> observer.onError(Status.UNAVAILABLE.asRuntimeException()));

        Outcome<CreatePaymentIntentResponse.Success> outcome = create(t0);

        assertInstanceOf(Outcome.Unknown.class, outcome);
        assertTrue(outcome.shouldRetry());
    }
}
