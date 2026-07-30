package network.t0.pay.lp;

import io.github.cdimascio.dotenv.Dotenv;
import network.t0.pay.lp.handler.LpCallbackHandler;
import network.t0.pay.lp.internal.PublishQuote;
import network.t0.pay.lp.internal.WithdrawQuote;
import network.t0.pay.proto.tzero.v1.pay.LpServiceGrpc;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.ProviderServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Liquidity Provider starter for the t-0 QR payment flow.
 *
 * <p>Work through the numbered TODOs in order; the README explains each phase.
 * Section references such as §1 point at the QR Payment API spec (`qr_api.md`).
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

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
        var t0 = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer, LpServiceGrpc::newBlockingStub);

        // Inbound: the one callback t-0 pushes to you (§8).
        // ProviderServer verifies every inbound signature against NETWORK_PUBLIC_KEY.
        ProviderServer server = startCallbackServer(config);

        // ──────────────────────────────────────────────────────────────────
        // Phase 2 — keep a quote standing.
        //
        // Standing quotes are immutable: you refresh a price by publishing a new
        // quote and withdrawing the old one, never by amending it. Executions
        // already accepted against a withdrawn quote stay binding.
        // ──────────────────────────────────────────────────────────────────

        // TODO: Step 2.1 — replace the hardcoded currency, rate and bounds with your
        //       own pricing.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                () -> refreshStandingQuote(t0),
                0, QUOTE_REFRESH_INTERVAL.toSeconds(), TimeUnit.SECONDS);

        // TODO: Step 4.1 — after you release a bank transfer to the acquirer, call
        //       FiatSettlementSent.report(t0.stub(), ...) — §10. Drive it off your
        //       payment rail, not off a timer here.

        waitForShutdown(server, scheduler, t0);
    }

    /**
     * Publishes a fresh quote and withdraws the one it replaces, so exactly one
     * quote of yours stands at a time.
     */
    private static void refreshStandingQuote(
            BlockingNetworkClient<LpServiceGrpc.LpServiceBlockingStub> t0) {
        PublishQuote.publish(t0.stub(), QUOTE_VALIDITY).ifPresent(newQuoteId -> {
            long previous = standingQuoteId.getAndSet(newQuoteId);
            if (previous != 0) {
                WithdrawQuote.withdraw(t0.stub(), previous);
            }
        });
    }

    private static Config loadConfig() {
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

        return new Config(privateKey, networkPublicKey, endpoint, port);
    }

    /**
     * ProviderServer comes from the provider SDK — it is the signed gRPC transport,
     * not the provider protocol. It hosts whatever services you give it.
     */
    private static ProviderServer startCallbackServer(Config config) {
        try {
            ProviderServer server = ProviderServer.create(config.port(), config.networkPublicKey())
                    .withService(new LpCallbackHandler())
                    .start();

            log.info("Callback server listening on port {}", server.getPort());
            return server;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start the callback server", e);
        }
    }

    private static void waitForShutdown(
            ProviderServer server,
            ScheduledExecutorService scheduler,
            BlockingNetworkClient<LpServiceGrpc.LpServiceBlockingStub> t0) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            scheduler.shutdown();

            // Step 2.2 — pull the standing quote before going away. Leaving it up
            // keeps t-0 pricing sales you are no longer around to execute.
            long standing = standingQuoteId.getAndSet(0);
            if (standing != 0) {
                WithdrawQuote.withdraw(t0.stub(), standing);
            }

            server.shutdown();
            t0.shutdown();
            try {
                scheduler.awaitTermination(10, TimeUnit.SECONDS);
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
