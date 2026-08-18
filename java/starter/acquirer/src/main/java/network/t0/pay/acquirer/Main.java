package network.t0.pay.acquirer;

import io.github.cdimascio.dotenv.Dotenv;
import network.t0.pay.client.CallDeadline;
import network.t0.pay.acquirer.handler.AcquirerCallbackHandler;
import network.t0.pay.acquirer.internal.CreatePaymentIntent;
import network.t0.pay.acquirer.internal.Decimals;
import network.t0.pay.acquirer.internal.GetPaymentQuote;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.pay.server.UsdtPayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Acquirer starter for the t-0 QR payment flow.
 *
 * <p>Work through the numbered TODOs in order; the README explains each phase.
 * Numbers such as §4 are shorthand for the endpoints — the README maps them to RPC
 * names, and the
 * <a href="https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_acquirer/">acquirer
 * API reference</a> documents every field.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /** Uncompressed secp256k1 point: 65 bytes as hex, 0x prefix optional. */
    private static final Pattern NETWORK_PUBLIC_KEY_PATTERN =
            Pattern.compile("(0x)?[0-9a-fA-F]{130}");

    public static void main(String[] args) {
        // First, before anything logs: grpc-java logs through java.util.logging, which
        // logback does not intercept on its own — its records bypass logback.xml
        // entirely and print in raw JUL format, levels and all. Dropping the JDK's own
        // root handler and installing the bridge is what makes the io.grpc level in
        // logback.xml mean something.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try {
            run();
        } catch (ConfigurationException e) {
            log.error("{}", e.getMessage());
            log.error("{}", e.getHelpMessage());
            System.exit(1);
        } catch (Exception e) {
            log.error("Acquirer failed to start", e);
            System.exit(1);
        }
    }

    private static void run() {
        Config config = loadConfig();
        Signer signer = Signer.fromHex(config.privateKey());

        log.info("Acquirer public key: {}", signer.getPublicKeyHexPrefixed());
        // TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

        // Outbound: everything you call on t-0 (§3, §4, §12).
        // BlockingNetworkClient signs each request with your private key, and
        // CallDeadline bounds every call at 10s. A call that needs a different
        // deadline sets one at its own call site and this steps aside — see §3.
        var t0 = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer,
                channel -> AcquirerServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(new CallDeadline(Duration.ofSeconds(10))));

        // Inbound: the callbacks t-0 pushes to you (§7, §11, §13, §15).
        // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
        UsdtPayServer server = startCallbackServer(config);

        // ──────────────────────────────────────────────────────────────────
        // Phase 2 — price a sale, then open an intent for it.
        //
        // This runs a single demo sale at startup so you can see the round
        // trip. Move it behind your POS integration once it works.
        // ──────────────────────────────────────────────────────────────────

        // TODO: Step 2.1 — replace the demo sale with a real one from your POS. One
        //       sale is one currency, one amount and one paymentRef: quote and intent
        //       must describe the same sale or you price one thing and charge another.
        String localCurrency = "COP";
        var localAmount = Decimals.of("100000.00");
        // paymentRef identifies the sale in your own ledger; t-0 echoes it on §7 and
        // §15 and does not require it to be unique.
        String paymentRef = UUID.randomUUID().toString();
        // idempotencyKey is the key for §4. Mint it with the sale and persist it — a
        // fresh key on a retry opens a second intent for one sale.
        String idempotencyKey = UUID.randomUUID().toString();

        // Both calls return an Outcome, and neither should be dropped on the floor —
        // Rejected and Unknown mean different things and want different handling.
        var quoted = GetPaymentQuote.fetch(t0.stub(), localCurrency, localAmount);
        if (quoted.shouldRetry()) {
            // TODO: Step 2.1 — §3 is a stateless lookup with no idempotency key, so an
            //       unanswered quote is safe to re-ask as often as you like.
            log.warn("No answer from §3 — the lookup is safe to retry");
        }

        quoted.value()
                // TODO: Step 2.2 — render the returned qrOptions as QR codes on the POS.
                .ifPresent(quote -> {
                    var intent = CreatePaymentIntent.create(
                            t0.stub(), paymentRef, idempotencyKey, localAmount, quote.getQuoteId());

                    // §4 is the opposite case: it is keyed, and Unknown means t-0 may
                    // already have opened the intent. Resending the same idempotencyKey
                    // is what makes the retry safe rather than a second sale.
                    if (intent.shouldRetry()) {
                        // TODO: Step 2.2 — hand this to your retry path, resending the
                        //       same idempotencyKey with identical content until t-0
                        //       answers. A fresh key here is a second sale.
                        log.warn("Intent for sale {} is unresolved — retry the same idempotencyKey",
                                paymentRef);
                    }
                });

        // TODO: Step 2.3 — deploy this service and give the t-0 team its base URL,
        //       so the Phase 3 callbacks can reach you.

        waitForShutdown(server, t0);
    }

    private static Config loadConfig() {
        // Dotenv reads .env from the process working directory, so run the binary
        // from the directory holding your .env. Say where we looked — otherwise a
        // .env one directory up looks exactly like a .env that is not filled in.
        Path env = Path.of(".env").toAbsolutePath();
        boolean hasEnvFile = Files.exists(env);
        if (!hasEnvFile) {
            log.info("No .env at {} — taking configuration from the environment instead", env);
        }
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String privateKey = dotenv.get("PRIVATE_KEY");
        String networkPublicKey = dotenv.get("NETWORK_PUBLIC_KEY");
        String endpoint = dotenv.get("TZERO_ENDPOINT", "https://usdt-pay-api-sandbox.t-0.network");

        if (privateKey == null || privateKey.isBlank()) {
            // Never "copy the example over it". A scaffolded project's .env already
            // holds the generated key, its public half is with the t-0 team, and the
            // private half exists nowhere else — overwriting it ends the integration.
            // With no .env in sight the likelier cause is the working directory, since
            // that is where it is read from.
            throw new ConfigurationException(
                    "PRIVATE_KEY is not set",
                    hasEnvFile
                            ? "Add it to " + env + ", editing that file in place."
                            : "There is no .env here. Run this from your project directory, whose "
                                    + ".env holds the key generated for you — or, in a project you "
                                    + "built by hand, set PRIVATE_KEY in the environment.");
        }

        if (networkPublicKey == null || networkPublicKey.isBlank()) {
            throw new ConfigurationException(
                    "NETWORK_PUBLIC_KEY is not set",
                    "Ask the t-0 team for the network public key and put it in .env.");
        }

        // Checked here so a typo reports as configuration, with somewhere to go for
        // the right value. The signature verifier does reject a malformed key on its
        // own, but not until the callback server starts and only as an
        // IllegalArgumentException about hex length.
        if (!NETWORK_PUBLIC_KEY_PATTERN.matcher(networkPublicKey).matches()) {
            throw new ConfigurationException(
                    "NETWORK_PUBLIC_KEY is not a valid uncompressed secp256k1 public key",
                    "Expected 130 hex characters (65 bytes), optionally 0x-prefixed; got "
                            + networkPublicKey.length() + " characters.");
        }

        // Last, so the keys — which nobody can guess for you — are reported before a
        // setting that has a working default.
        int port = parsePort(dotenv.get("PORT", "8080"));

        return new Config(privateKey, networkPublicKey, endpoint, port);
    }

    /**
     * Checked here so a typo reports as configuration. Left to {@code Integer.parseInt}
     * it surfaces as a NumberFormatException under "Acquirer failed to start", which
     * names neither the setting nor the value.
     */
    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // Same error as out-of-range: to whoever set PORT it is the same mistake.
        }
        throw new ConfigurationException(
                "PORT is not a valid port number: " + value,
                "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.");
    }

    private static UsdtPayServer startCallbackServer(Config config) {
        try {
            UsdtPayServer server = UsdtPayServer.create(config.port(), config.networkPublicKey())
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
            throw new RuntimeException("Failed to start the callback server", e);
        }
    }

    private static void waitForShutdown(
            UsdtPayServer server,
            BlockingNetworkClient<AcquirerServiceGrpc.AcquirerServiceBlockingStub> t0) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            server.shutdown();
            t0.shutdown();
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
                t0.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Main() {
    }

    /** Configuration is missing or unusable — the process cannot start. */
    private static class ConfigurationException extends RuntimeException {
        private final String helpMessage;

        ConfigurationException(String message, String helpMessage) {
            super(message);
            this.helpMessage = helpMessage;
        }

        String getHelpMessage() {
            return helpMessage;
        }
    }
}
