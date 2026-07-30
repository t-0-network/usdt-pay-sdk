package network.t0.pay.issuer;

import io.github.cdimascio.dotenv.Dotenv;
import network.t0.pay.issuer.handler.IssuerCallbackHandler;
import network.t0.pay.proto.tzero.v1.pay.IssuerServiceGrpc;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.pay.server.UsdtPayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Issuer starter for the t-0 QR payment flow.
 *
 * <p>Work through the numbered TODOs in order; the README explains each phase.
 * Section references such as §6 point at the QR Payment API spec (`qr_api.md`).
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
            log.error("Issuer failed to start", e);
            System.exit(1);
        }
    }

    private static void run() {
        Config config = loadConfig();
        Signer signer = Signer.fromHex(config.privateKey());

        log.info("Issuer public key: {}", signer.getPublicKeyHexPrefixed());
        // TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

        // Outbound: everything you call on t-0 (§6, §9, §14).
        var t0 = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer, IssuerServiceGrpc::newBlockingStub);

        // Inbound: the one callback t-0 pushes to you (§5).
        // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
        UsdtPayServer server = startCallbackServer(config);

        // ──────────────────────────────────────────────────────────────────
        // Phase 3 — report what you see on-chain.
        //
        // Nothing runs at startup: every outbound call is driven by your chain
        // watcher, not by a timer.
        //
        // TODO: Step 3.1 — when a deposit address funds and clears KYT, call
        //       PaymentReceived.report(t0.stub(), ...) — §6.
        // TODO: Step 3.2 — after you broadcast a settlement transfer, call
        //       SettlementSent.report(t0.stub(), ...) — §9.
        // TODO: Step 3.3 — when a reservation lapses and you release its addresses,
        //       call PaymentExpired.report(t0.stub(), ...) — §14.
        //
        // t0 is not passed into the handler on purpose: §5 must answer inline with
        // reserved addresses and nothing else. Hand `t0` to your chain watcher.
        // ──────────────────────────────────────────────────────────────────

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
                    "Copy .env.example to .env and put your issuer private key in PRIVATE_KEY.");
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
                    .withService(new IssuerCallbackHandler())
                    .start();

            log.info("Callback server listening on port {}", server.getPort());
            return server;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start the callback server", e);
        }
    }

    private static void waitForShutdown(
            UsdtPayServer server,
            BlockingNetworkClient<IssuerServiceGrpc.IssuerServiceBlockingStub> t0) {
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
