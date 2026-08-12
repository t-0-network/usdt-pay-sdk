package network.t0.pay.acquirer;

import io.github.cdimascio.dotenv.Dotenv;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Acquirer starter for the t-0 QR payment flow.
 *
 * <p>Work through the numbered TODOs in order; the README explains each phase.
 * Section references such as §4 point at the QR Payment API spec (`qr_api.md`).
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /** Uncompressed secp256k1 point: 65 bytes as hex, 0x prefix optional. */
    private static final Pattern NETWORK_PUBLIC_KEY_PATTERN =
            Pattern.compile("(0x)?[0-9a-fA-F]{130}");

    public static void main(String[] args) {
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
        // BlockingNetworkClient signs each request with your private key.
        var t0 = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer, AcquirerServiceGrpc::newBlockingStub);

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

        GetPaymentQuote.fetch(t0.stub(), localCurrency, localAmount).value()
                // TODO: Step 2.2 — render the returned qrOptions as QR codes on the POS.
                .ifPresent(quote -> CreatePaymentIntent.create(
                        t0.stub(), paymentRef, idempotencyKey, localAmount, quote.getQuoteId()));

        // TODO: Step 2.3 — deploy this service and give the t-0 team its base URL,
        //       so the Phase 3 callbacks can reach you.

        waitForShutdown(server, t0);
    }

    private static Config loadConfig() {
        // Dotenv reads .env from the process working directory, so run the binary
        // from the directory holding your .env. Say where we looked — otherwise a
        // .env one directory up looks exactly like a .env that is not filled in.
        Path env = Path.of(".env").toAbsolutePath();
        if (!Files.exists(env)) {
            log.info("No .env at {} — taking configuration from the environment instead", env);
        }
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String privateKey = dotenv.get("PRIVATE_KEY");
        String networkPublicKey = dotenv.get("NETWORK_PUBLIC_KEY");
        String endpoint = dotenv.get("TZERO_ENDPOINT", "https://usdt-pay-api-sandbox.t-0.network");
        int port = Integer.parseInt(dotenv.get("PORT", "8080"));

        if (privateKey == null || privateKey.isBlank()) {
            throw new ConfigurationException(
                    "PRIVATE_KEY is not set",
                    "Copy .env.example to .env and put your acquirer private key in PRIVATE_KEY.");
        }

        if (networkPublicKey == null || networkPublicKey.isBlank()) {
            throw new ConfigurationException(
                    "NETWORK_PUBLIC_KEY is not set",
                    "Ask the t-0 team for the network public key and put it in .env.");
        }

        // Checked here so a typo reports as configuration rather than as a stack
        // trace out of the signature verifier when the first callback arrives.
        if (!NETWORK_PUBLIC_KEY_PATTERN.matcher(networkPublicKey).matches()) {
            throw new ConfigurationException(
                    "NETWORK_PUBLIC_KEY is not a valid uncompressed secp256k1 public key",
                    "Expected 130 hex characters (65 bytes), optionally 0x-prefixed; got "
                            + networkPublicKey.length() + " characters.");
        }

        return new Config(privateKey, networkPublicKey, endpoint, port);
    }

    private static UsdtPayServer startCallbackServer(Config config) {
        try {
            UsdtPayServer server = UsdtPayServer.create(config.port(), config.networkPublicKey())
                    .withService(new AcquirerCallbackHandler())
                    .start();

            log.info("Callback server listening on port {}", server.getPort());
            return server;
        } catch (IOException e) {
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
