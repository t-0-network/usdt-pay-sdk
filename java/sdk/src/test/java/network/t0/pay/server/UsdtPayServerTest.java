package network.t0.pay.server;

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
import network.t0.pay.proto.tzero.v1.pay.acquirer.AcquirerCallbackServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.acquirer.PaymentAuthorizedRequest;
import network.t0.pay.proto.tzero.v1.pay.acquirer.PaymentAuthorizedResponse;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
