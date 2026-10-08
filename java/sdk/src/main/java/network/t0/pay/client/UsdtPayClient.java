package network.t0.pay.client;

import io.grpc.Channel;
import io.grpc.stub.AbstractBlockingStub;
import network.t0.sdk.crypto.DigestSigner;
import network.t0.sdk.network.BlockingNetworkClient;

import java.util.function.Function;

/**
 * A client for the t-0 endpoints your role calls. Every request is signed with your
 * private key; t-0 knows you by the matching public key.
 *
 * <pre>{@code
 * try (var t0 = UsdtPayClient.create(endpoint, Signer.fromHex(privateKey),
 *         AcquirerServiceGrpc::newBlockingStub)) {
 *     var quote = t0.stub().getPaymentQuote(request);
 * }
 * }</pre>
 *
 * <p>This delegates to {@code BlockingNetworkClient.create} in {@code provider-sdk-java}
 * with its default deadlines. For any other client option, call
 * {@code BlockingNetworkClient} directly.
 */
public final class UsdtPayClient {

    /**
     * @param endpoint    t-0 API base URL, e.g. {@code https://usdt-pay-api-sandbox.t-0.network};
     *                    the provider SDK's default is a different API
     * @param signer      your secp256k1 key, as a {@code Signer}, or a {@code DigestSigner}
     *                    when the key lives in an HSM or KMS and never reaches this process
     * @param stubFactory the stub for your role, e.g. {@code AcquirerServiceGrpc::newBlockingStub}
     * @param <S>         the blocking stub type
     * @return the client; {@code close()} it to shut its channel down
     */
    public static <S extends AbstractBlockingStub<S>> BlockingNetworkClient<S> create(
            String endpoint,
            DigestSigner signer,
            Function<Channel, S> stubFactory) {
        return BlockingNetworkClient.create(endpoint, signer, stubFactory);
    }

    private UsdtPayClient() {
    }
}
