package network.t0.pay.server;

import com.google.protobuf.Timestamp;
import io.grpc.CallOptions;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import network.t0.pay.client.UsdtPayClient;
import network.t0.pay.proto.tzero.v1.pay.Blockchain;
import network.t0.pay.proto.tzero.v1.pay.Decimal;
import network.t0.pay.proto.tzero.v1.pay.acquirer.AcquirerCallbackServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.acquirer.PaymentAuthorizedRequest;
import network.t0.pay.proto.tzero.v1.pay.acquirer.PaymentAuthorizedResponse;
import network.t0.pay.proto.tzero.v1.pay.issuer.CreatePaymentInstructionsRequest;
import network.t0.pay.proto.tzero.v1.pay.issuer.CreatePaymentInstructionsResponse;
import network.t0.pay.proto.tzero.v1.pay.issuer.IssuerCallbackServiceGrpc;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.ResponseValidationException;
import network.t0.sdk.provider.Validate;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code grpc.health.v1.Health} is on the port alongside the services you registered —
 * the transport mounts it so t-0 can see the endpoint is up.
 *
 * <p>The routing test calls health by raw method name (unsigned) rather than through a
 * generated stub: the claim is about a wire path, and stating it that way keeps that
 * test free of protocols it does not serve. An unrouted service is
 * {@code UNIMPLEMENTED}; a routed one gets as far as the signature check and is refused
 * {@code INVALID_ARGUMENT} — that code means routed. The version-header test uses the
 * generated {@code HealthGrpc} stub with a signed call because the SDK identity headers
 * only ride on a successful response.
 */
class UsdtPayServerTest {

    /** t-0's key in production; here the test plays t-0, so it holds both halves. */
    private static final String NETWORK_PRIVATE_KEY =
            "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    private static final String NETWORK_PUBLIC_KEY =
            "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe4456"
                    + "7713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    @Test
    void thePortCarriesWhatYouRegisteredPlusHealth() throws Exception {
        try (UsdtPayServer server = UsdtPayServer.create(0, NETWORK_PUBLIC_KEY)
                .withService(new AcquirerCallbackServiceGrpc.AcquirerCallbackServiceImplBase() {})
                .start()) {

            ManagedChannel channel = NettyChannelBuilder
                    .forAddress("localhost", server.getPort())
                    .usePlaintext()
                    .build();
            try {
                assertEquals(
                        Status.Code.INVALID_ARGUMENT,
                        callStatus(channel, "grpc.health.v1.Health/Check"),
                        "health is not mounted, so t-0 cannot tell this endpoint is up");

                assertEquals(
                        Status.Code.INVALID_ARGUMENT,
                        callStatus(channel, AcquirerCallbackServiceGrpc.SERVICE_NAME + "/PaymentAuthorized"),
                        "the service the caller registered is not routed");
            } finally {
                channel.shutdownNow();
                channel.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void healthResponseCarriesSdkVersion() throws Exception {
        try (UsdtPayServer server = UsdtPayServer.create(0, NETWORK_PUBLIC_KEY)
                .withService(new AcquirerCallbackServiceGrpc.AcquirerCallbackServiceImplBase() {})
                .start()) {

            var headersCapture = new AtomicReference<Metadata>();
            var trailersCapture = new AtomicReference<Metadata>();

            try (var t0 = BlockingNetworkClient.create(
                    "http://localhost:" + server.getPort(),
                    Signer.fromHex(NETWORK_PRIVATE_KEY),
                    channel -> HealthGrpc.newBlockingStub(
                            ClientInterceptors.intercept(channel,
                                    MetadataUtils.newCaptureMetadataInterceptor(
                                            headersCapture, trailersCapture))))) {

                t0.stub().check(HealthCheckRequest.getDefaultInstance());

                Metadata headers = headersCapture.get();
                assertNotNull(headers, "response headers must be present");
                assertEquals(Version.SDK_VERSION,
                        headers.get(Metadata.Key.of("t0-sdk-version",
                                Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals("java",
                        headers.get(Metadata.Key.of("t0-sdk-ecosystem",
                                Metadata.ASCII_STRING_MARSHALLER)));
            }
        }
    }

    @Test
    void signedCallbackStillAnswers() throws Exception {
        try (UsdtPayServer server = UsdtPayServer.create(0, NETWORK_PUBLIC_KEY)
                .withService(new AcquirerCallbackServiceGrpc.AcquirerCallbackServiceImplBase() {
                    @Override
                    public void paymentAuthorized(PaymentAuthorizedRequest request,
                                                  StreamObserver<PaymentAuthorizedResponse> observer) {
                        observer.onNext(PaymentAuthorizedResponse.getDefaultInstance());
                        observer.onCompleted();
                    }
                })
                .start();
             var t0 = BlockingNetworkClient.create(
                     "http://localhost:" + server.getPort(),
                     Signer.fromHex(NETWORK_PRIVATE_KEY),
                     AcquirerCallbackServiceGrpc::newBlockingStub)) {

            assertNotNull(t0.stub().paymentAuthorized(PaymentAuthorizedRequest.getDefaultInstance()));
        }
    }

    // The pay contract's custom rules (valid_address, valid_tx_hash) live in this SDK's
    // generated code, not in provider-sdk's. These tests make one fire through the server,
    // through a handler that checks its own response, and through Validate.check, so a rule
    // that could not be resolved would show up here as a different error.

    private static final String BAD_RESPONSE_MESSAGE =
            "response validation failed: success.deposit_options[0].deposit_address: must be 34-42 characters";

    @Test
    void serverRefusesResponseThatBreaksCustomRule() throws Exception {
        assertRefused(response -> response);
    }

    @Test
    void handlerThatChecksItsResponseGetsTheSameRefusal() throws Exception {
        assertRefused(Validate::check);
    }

    @Test
    void validateCheckFiresCustomRule() {
        ResponseValidationException thrown =
                assertThrows(ResponseValidationException.class, () -> Validate.check(badInstructions()));
        assertEquals(BAD_RESPONSE_MESSAGE, thrown.getMessage());
    }

    /** Serves badInstructions() through {@code beforeReturn} and expects the call to fail INTERNAL. */
    private static void assertRefused(UnaryOperator<CreatePaymentInstructionsResponse> beforeReturn)
            throws Exception {
        try (UsdtPayServer server = UsdtPayServer.create(0, NETWORK_PUBLIC_KEY)
                .withService(new IssuerCallbackServiceGrpc.IssuerCallbackServiceImplBase() {
                    @Override
                    public void createPaymentInstructions(
                            CreatePaymentInstructionsRequest request,
                            StreamObserver<CreatePaymentInstructionsResponse> observer) {
                        observer.onNext(beforeReturn.apply(badInstructions()));
                        observer.onCompleted();
                    }
                })
                .start();
             var t0 = UsdtPayClient.create(
                     "http://localhost:" + server.getPort(),
                     Signer.fromHex(NETWORK_PRIVATE_KEY),
                     IssuerCallbackServiceGrpc::newBlockingStub)) {

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                    () -> t0.stub().createPaymentInstructions(validInstructionsRequest()));
            assertEquals(Status.Code.INTERNAL, thrown.getStatus().getCode());
            assertEquals(BAD_RESPONSE_MESSAGE, thrown.getStatus().getDescription());
        }
    }

    /** Breaks exactly one rule: deposit_address is too short. */
    private static CreatePaymentInstructionsResponse badInstructions() {
        return CreatePaymentInstructionsResponse.newBuilder()
                .setSuccess(CreatePaymentInstructionsResponse.Success.newBuilder()
                        .setExpiresAt(inAnHour())
                        .addDepositOptions(CreatePaymentInstructionsResponse.Success.DepositOption.newBuilder()
                                .setChain(Blockchain.BLOCKCHAIN_ETH)
                                .setDepositAddress("bad")
                                .setTokenContract("0x" + "bb".repeat(20))))
                .build();
    }

    private static CreatePaymentInstructionsRequest validInstructionsRequest() {
        return CreatePaymentInstructionsRequest.newBuilder()
                .setPaymentIntentId(1)
                .setAcquirerId(2)
                .setAmountUsdt(Decimal.newBuilder().setUnscaled(1000).setExponent(-2))
                .setExpiresAt(inAnHour())
                .build();
    }

    private static Timestamp inAnHour() {
        return Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond() + 3600).build();
    }

    private static Status.Code callStatus(ManagedChannel channel, String fullMethodName) {
        MethodDescriptor<byte[], byte[]> method = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethodName)
                .setRequestMarshaller(BYTES)
                .setResponseMarshaller(BYTES)
                .build();

        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT, new byte[0]));
        return thrown.getStatus().getCode();
    }

    /** Enough of a marshaller to send an empty message and never read a reply. */
    private static final MethodDescriptor.Marshaller<byte[]> BYTES =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(byte[] value) {
                    return new ByteArrayInputStream(value);
                }

                @Override
                public byte[] parse(InputStream stream) {
                    try {
                        return stream.readAllBytes();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
}
