package network.t0.pay.server;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import network.t0.pay.proto.tzero.v1.pay.AcquirerCallbackServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.PaymentAuthorizedRequest;
import network.t0.pay.proto.tzero.v1.pay.PaymentAuthorizedResponse;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code grpc.health.v1.Health} is on the port alongside the services you registered —
 * the transport mounts it so t-0 can see the endpoint is up. Health is the one thing
 * mounted for you, and it can be, because it belongs to nobody's business protocol.
 *
 * <p>Health is called by raw method name rather than through a generated stub: the claim
 * is about a wire path, and stating it that way keeps the pay tests free of protocols
 * they do not serve.
 *
 * <p>The calls are unsigned on purpose. gRPC resolves the method before any per-service
 * interceptor runs, so an unrouted service is {@code UNIMPLEMENTED} while a routed one
 * gets as far as the signature check and is refused {@code INVALID_ARGUMENT}. So that
 * code here means routed — and the second test is the other half, that a properly signed
 * callback to a service you did register still answers.
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
