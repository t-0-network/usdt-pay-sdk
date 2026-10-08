package network.t0.pay.client;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import network.t0.pay.proto.tzero.v1.pay.acquirer.AcquirerCallbackServiceGrpc;
import network.t0.pay.server.UsdtPayServer;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A call through {@link UsdtPayClient} is signed: a server that verifies signatures answers it. */
class UsdtPayClientTest {

    /** The test signs as t-0 would, so the server is given the matching public key. */
    private static final String PRIVATE_KEY =
            "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    @Test
    void signsEveryCall() throws Exception {
        String publicKey = Signer.publicKeyFromPrivateKey(PRIVATE_KEY);
        try (UsdtPayServer server = UsdtPayServer.create(0, publicKey)
                .withService(new AcquirerCallbackServiceGrpc.AcquirerCallbackServiceImplBase() {})
                .start();
             var t0 = UsdtPayClient.create(
                     "http://localhost:" + server.getPort(),
                     Signer.fromHex(PRIVATE_KEY),
                     HealthGrpc::newBlockingStub)) {

            assertEquals(HealthCheckResponse.ServingStatus.SERVING,
                    t0.stub().check(HealthCheckRequest.getDefaultInstance()).getStatus());
        }
    }
}
