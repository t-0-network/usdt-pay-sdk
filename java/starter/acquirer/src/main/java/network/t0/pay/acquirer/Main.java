package network.t0.pay.acquirer;

import io.github.cdimascio.dotenv.Dotenv;
import network.t0.pay.acquirer.handler.AcquirerCallbackHandler;
import network.t0.pay.acquirer.internal.CreatePaymentIntent;
import network.t0.pay.acquirer.internal.Decimals;
import network.t0.pay.acquirer.internal.GetPaymentQuote;
import network.t0.pay.client.UsdtPayClient;
import network.t0.pay.proto.tzero.v1.pay.acquirer.AcquirerServiceGrpc;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.pay.server.UsdtPayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Acquirer starter for the t-0 USDt pay flow.
 *
 * <p>Work through the numbered TODOs in order; the README explains each phase.
 * The RPC names ({@code GetPaymentQuote}, {@code CreatePaymentIntent}, …) match the
 * methods in the proto and in the code; the
 * <a href="https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_acquirer/">acquirer
 * API reference</a> documents every field.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // First, before anything logs: grpc-java logs through java.util.logging, which
        // logback does not intercept on its own — its records bypass logback.xml
        // entirely and print in raw JUL format, levels and all. Dropping the JDK's own
        // root handler and installing the bridge is what makes the io.grpc level in
        // logback.xml mean something.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Startup errors go to stderr as plain lines, not through logback, so nothing
        // but the message is on them. UTF-8 whatever the locale, like logback's own
        // output: System.err encodes in the locale's charset, which turns the em dash
        // into "?" in a container with no locale set.
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

        try {
            run(err);
        } catch (ConfigurationException e) {
            printConfigurationError(err, e);
            System.exit(1);
        } catch (Exception e) {
            err.println(startupFailure(e));
            System.exit(1);
        }
    }

    private static void run(PrintStream err) {
        // A variable set in the environment wins over the same one in .env.
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        Config config = Config.load(dotenv::get, Path.of(".env").toAbsolutePath(), err);
        Signer signer = Signer.fromHex(config.privateKey());

        // Outbound: GetPaymentQuote, CreatePaymentIntent, SettlementReceived.
        // UsdtPayClient signs each request with your private key.
        var t0 = UsdtPayClient.create(
                config.tzeroEndpoint(), signer, AcquirerServiceGrpc::newBlockingStub);

        // Inbound: PaymentAuthorized, SettlementInitiated, SettlementCompleted, PaymentExpired.
        // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
        UsdtPayServer server = startCallbackServer(config);

        // Before the demo sale, so a Ctrl-C while it waits on t-0 still shuts down cleanly.
        stopOnSignal(server, t0);

        // ──────────────────────────────────────────────────────────────────
        // Phase 2 — price a sale, then open an intent for it.
        //
        // This runs a single demo sale at startup so you can see the round
        // trip. Move it behind your POS integration once it works.
        // ──────────────────────────────────────────────────────────────────

        // TODO: Step 2.1 — replace the demo sale with a real one from your POS. One
        //       sale is one currency, one amount and one paymentRef: quote and intent
        //       must describe the same sale or you price one thing and charge another.
        //       On-chain settlement: skip GetPaymentQuote and send the amount in USDt.
        String localCurrency = "COP";
        var localAmount = Decimals.of("100000");
        // paymentRef identifies the sale in your own ledger; t-0 echoes it on
        // PaymentAuthorized and PaymentExpired and does not require it to be unique.
        String paymentRef = UUID.randomUUID().toString();
        // idempotencyKey is the key for CreatePaymentIntent. Mint it with the sale
        // and persist it — a fresh key on a retry opens a second intent for one sale.
        String idempotencyKey = UUID.randomUUID().toString();

        // Both calls return an Outcome, and neither should be dropped on the floor —
        // Rejected and Unknown mean different things and want different handling.
        var quoted = GetPaymentQuote.fetch(t0.stub(), localCurrency, localAmount);
        if (quoted.shouldRetry()) {
            // See Step 2.1 — GetPaymentQuote is a stateless lookup with no
            // idempotency key, so an unanswered quote is safe to re-ask.
            log.warn("No answer from GetPaymentQuote — the lookup is safe to retry");
        }

        quoted.accepted()
                .ifPresent(quote -> {
                    var intent = CreatePaymentIntent.create(
                            t0.stub(), paymentRef, idempotencyKey, localCurrency, localAmount, quote.getQuoteId());

                    // CreatePaymentIntent is the opposite case: it is keyed, and
                    // Unknown means t-0 may already have opened the intent. Resending
                    // the same idempotencyKey is what makes the retry safe rather than
                    // a second sale.
                    if (intent.shouldRetry()) {
                        // See Step 2.2 — hand this to your retry path, resending
                        // the same idempotencyKey with identical content until
                        // t-0 answers. A fresh key here is a second sale.
                        log.warn("Intent for sale {} is unresolved — retry the same idempotencyKey",
                                paymentRef);
                    }
                });

        // TODO: Step 2.3 — deploy this service and give the t-0 team its base URL,
        //       so the Phase 3 callbacks can reach you.

        // Serve the callbacks until SIGINT or SIGTERM stops the server.
        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static UsdtPayServer startCallbackServer(Config config) {
        UsdtPayServer.Builder builder;
        try {
            builder = UsdtPayServer.create(config.port(), config.networkPublicKey());
        } catch (IllegalArgumentException e) {
            // The SDK parses the key here, before anything binds, and says what is
            // wrong with it: "invalid network public key: <reason>".
            throw new ConfigurationException(e.getMessage(), Config.NETWORK_PUBLIC_KEY_HELP);
        }

        try {
            UsdtPayServer server = builder
                    .withService(new AcquirerCallbackHandler())
                    .start();

            log.info("Callback server listening on port {}", server.getPort());
            return server;
        } catch (IOException e) {
            // grpc wraps the bind failure, so the BindException sits two levels down
            // rather than being what we caught. Worth digging out: 8080 is the busiest
            // port on a developer's laptop, and the netty frames under it say nothing
            // the port number does not.
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof BindException) {
                    throw new ConfigurationException(
                            "Port " + config.port() + " is already in use",
                            "Something else is listening on it. Set PORT in .env to a free port.");
                }
            }
            throw new UncheckedIOException("the callback server did not start: " + e.getMessage(), e);
        }
    }

    /**
     * SIGINT (Ctrl-C) and SIGTERM (docker stop, Kubernetes) close the server — it takes
     * no new calls and gives the ones in flight the SDK's grace period — then the
     * client, and exit 0. Left to the JVM they exit 130 and 143, which a supervisor
     * reads as a crash.
     *
     * <p>This replaces a shutdown hook rather than adding to one, so nothing closes
     * twice; logback's and gRPC's own hooks still run on the way out.
     */
    private static void stopOnSignal(UsdtPayServer server, BlockingNetworkClient<?> t0) {
        AtomicBoolean stopping = new AtomicBoolean();
        SignalHandler stop = signal -> {
            // A second Ctrl-C while the first is still draining changes nothing.
            if (!stopping.compareAndSet(false, true)) {
                return;
            }
            log.info("Shutting down");
            try {
                server.close();
                // A demo call still waiting on t-0 gets the client's grace period, then
                // is cancelled and comes back as Unknown.
                t0.close();
            } finally {
                System.exit(0);
            }
        };
        Signal.handle(new Signal("TERM"), stop);
        Signal.handle(new Signal("INT"), stop);
    }

    /** Two lines and nothing else on them: what is wrong, then what to do about it. */
    static void printConfigurationError(PrintStream err, ConfigurationException e) {
        err.println("ERROR: " + e.getMessage());
        err.println(e.getHelpMessage());
    }

    /** One line, no stack trace: the frames say less than the message does. */
    static String startupFailure(Exception e) {
        return "Acquirer failed to start: " + (e.getMessage() != null ? e.getMessage() : e.toString());
    }

    private Main() {
    }
}
