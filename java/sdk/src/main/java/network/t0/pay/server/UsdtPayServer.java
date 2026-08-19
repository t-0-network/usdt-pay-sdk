package network.t0.pay.server;

import io.grpc.BindableService;
import network.t0.sdk.provider.ProviderServer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * The server a pay participant runs so t-0 can call it: acquirers host §7/§11/§13/§15,
 * issuers host §5, LPs host §8.
 *
 * <p>Every inbound request is signature-verified against the t-0 network public key
 * before it reaches your handler, and every response is validated against the
 * contract's constraints on the way out.
 *
 * <p>It serves the services you register, plus the standard
 * {@code grpc.health.v1.Health} the transport mounts on every server it builds.
 *
 * <pre>{@code
 * UsdtPayServer server = UsdtPayServer.create(port, networkPublicKey)
 *         .withService(new AcquirerCallbackHandler())
 *         .start();
 * }</pre>
 *
 * <p>This delegates to the transport in {@code provider-sdk-java}, which is signed
 * gRPC and carries nothing provider-specific. The wrapper exists so a pay
 * participant never has to import a type called {@code ProviderServer} to run a
 * server that is not a provider's; it goes away once the transport ships as an
 * artifact of its own.
 */
public final class UsdtPayServer implements Closeable {

    private final ProviderServer delegate;

    private UsdtPayServer(ProviderServer delegate) {
        this.delegate = delegate;
    }

    /**
     * @param port             the port to listen on; t-0 must be able to reach it
     * @param networkPublicKey t-0's public key — inbound calls that do not verify
     *                         against it are refused
     */
    public static Builder create(int port, String networkPublicKey) {
        return new Builder(ProviderServer.create(port, networkPublicKey));
    }

    public int getPort() {
        return delegate.getPort();
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public void shutdownNow() {
        delegate.shutdownNow();
    }

    /** Blocks until the server stops. */
    public void awaitTermination() throws InterruptedException {
        delegate.awaitTermination();
    }

    /** @return true if the server stopped within the timeout */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        delegate.close();
    }

    public static final class Builder {

        private final ProviderServer.Builder delegate;

        private Builder(ProviderServer.Builder delegate) {
            this.delegate = delegate;
        }

        /** Mounts one of your callback handlers. Call it once per service you implement. */
        public Builder withService(BindableService service) {
            delegate.withService(service);
            return this;
        }

        public Builder withMaxInboundMessageSize(int bytes) {
            delegate.withMaxInboundMessageSize(bytes);
            return this;
        }

        public UsdtPayServer start() throws IOException {
            return new UsdtPayServer(delegate.start());
        }
    }
}
