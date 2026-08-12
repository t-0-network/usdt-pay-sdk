package network.t0.pay.lp;

import io.github.cdimascio.dotenv.Dotenv;
import network.t0.pay.client.CallDeadline;
import network.t0.pay.lp.handler.LpCallbackHandler;
import network.t0.pay.lp.internal.PublishQuote;
import network.t0.pay.lp.internal.WithdrawQuote;
import network.t0.pay.proto.tzero.v1.pay.LpServiceGrpc;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.pay.server.UsdtPayServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Liquidity Provider starter for the t-0 QR payment flow.
 *
 * <p>Work through the numbered TODOs in order; the README explains each phase.
 * Numbers such as §1 are shorthand for the endpoints — the README maps them to RPC
 * names, and the
 * <a href="https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_lp/">LP
 * API reference</a> documents every field.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /** Uncompressed secp256k1 point: 65 bytes as hex, 0x prefix optional. */
    private static final Pattern NETWORK_PUBLIC_KEY_PATTERN =
            Pattern.compile("(0x)?[0-9a-fA-F]{130}");

    /** No standing quote means no fiat sales for your acquirer — refresh well before expiry. */
    private static final Duration QUOTE_REFRESH_INTERVAL = Duration.ofSeconds(30);
    private static final Duration QUOTE_VALIDITY = Duration.ofSeconds(90);

    /** t-0's id for the quote currently standing, or 0 before the first publish. */
    private static final AtomicLong standingQuoteId = new AtomicLong();

    public static void main(String[] args) {
        try {
            run();
        } catch (ConfigurationException e) {
            log.error("{}", e.getMessage());
            log.error("{}", e.getHelpMessage());
            System.exit(1);
        } catch (Exception e) {
            log.error("LP failed to start", e);
            System.exit(1);
        }
    }

    private static void run() {
        Config config = loadConfig();
        Signer signer = Signer.fromHex(config.privateKey());

        log.info("LP public key: {}", signer.getPublicKeyHexPrefixed());
        // TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

        // Outbound: everything you call on t-0 (§1, §2, §10).
        // CallDeadline bounds every call at 10s. A call that needs a different
        // deadline sets one at its own call site and this steps aside — see §10.
        var t0 = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer,
                channel -> LpServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(new CallDeadline(Duration.ofSeconds(10))));

        // Inbound: the one callback t-0 pushes to you (§8).
        // Every inbound signature is verified against NETWORK_PUBLIC_KEY.
        UsdtPayServer server = startCallbackServer(config);

        // ──────────────────────────────────────────────────────────────────
        // Phase 2 — keep a quote standing.
        //
        // Standing quotes are immutable: you refresh a price by publishing a new
        // quote and withdrawing the old one, never by amending it. Executions
        // already accepted against a withdrawn quote stay binding.
        // ──────────────────────────────────────────────────────────────────

        // TODO: Step 2.1 — put your own currency and rate in PublishQuote, then
        //       uncomment the loop below to start quoting.
        //
        // Commented out deliberately: publishing a quote is a live offer that any sale
        // can execute against at whatever rate the code carries, and the code carries a
        // hardcoded one. Quoting should be something you switch on, not something that
        // happens because you ran the binary.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // scheduler.scheduleAtFixedRate(
        //         () -> refreshStandingQuote(t0),
        //         0, QUOTE_REFRESH_INTERVAL.toSeconds(), TimeUnit.SECONDS);

        // TODO: Step 4.1 — after you release a bank transfer to the acquirer, call
        //       FiatSettlementSent.report(t0.stub(), ...) — §10. Drive it off your
        //       payment rail, not off a timer here.

        waitForShutdown(server, scheduler, t0);
    }

    /**
     * Publishes a fresh quote and withdraws the one it replaces, so exactly one
     * quote of yours stands at a time.
     *
     * <p>A withdrawal that does not complete leaves the old quote standing and
     * executable at a stale rate — so its id is reported rather than dropped
     * silently, because it is the only handle you have left to retire it.
     */
    private static void refreshStandingQuote(
            BlockingNetworkClient<LpServiceGrpc.LpServiceBlockingStub> t0) {
        // TODO: Step 2.1 — mint quoteRef from your own pricing run and persist it
        //       before you publish. It is the idempotency key: on an unanswered call
        //       you must resend this same ref, and a fresh one publishes a second
        //       quote at the same price. t-0 echoes it back on §8.
        String quoteRef = UUID.randomUUID().toString();

        var published = PublishQuote.publish(t0.stub(), quoteRef, QUOTE_VALIDITY).value();
        if (published.isEmpty()) {
            return;
        }

        long previous = standingQuoteId.getAndSet(published.get().getQuoteId());
        if (previous != 0 && WithdrawQuote.withdraw(t0.stub(), previous).shouldRetry()) {
            // TODO: Step 2.2 — hand this to your retry path. Until it is withdrawn or
            //       expires, two of your quotes stand at once and either can execute.
            log.warn("Quote {} may still be standing — its withdrawal did not complete", previous);
        }
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
                    "Copy .env.example to .env and put your LP private key in PRIVATE_KEY.");
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

        return new Config(privateKey, networkPublicKey, endpoint, port);
    }

    private static UsdtPayServer startCallbackServer(Config config) {
        try {
            UsdtPayServer server = UsdtPayServer.create(config.port(), config.networkPublicKey())
                    .withService(new LpCallbackHandler())
                    .start();

            log.info("Callback server listening on port {}", server.getPort());
            return server;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start the callback server", e);
        }
    }

    private static void waitForShutdown(
            UsdtPayServer server,
            ScheduledExecutorService scheduler,
            BlockingNetworkClient<LpServiceGrpc.LpServiceBlockingStub> t0) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");

            // Wait for a refresh that is already running before reading the standing
            // id: shutdown() only stops new runs, so an in-flight publish would
            // otherwise store its quote after we withdrew and leave it standing.
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Quote refresh did not finish; a newer quote may still be standing");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Step 2.2 — pull the standing quote before going away. Leaving it up
            // keeps t-0 pricing sales you are no longer around to execute.
            long standing = standingQuoteId.getAndSet(0);
            if (standing != 0) {
                WithdrawQuote.withdraw(t0.stub(), standing);
            }

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
