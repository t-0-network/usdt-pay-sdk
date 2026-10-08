package network.t0.pay.acquirer;

import network.t0.sdk.crypto.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/** Everything the starter reads from the environment. */
public record Config(
        String privateKey,
        String networkPublicKey,
        String tzeroEndpoint,
        int port
) {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    static final String DEFAULT_TZERO_ENDPOINT = "https://usdt-pay-api-sandbox.t-0.network";

    /** Shared by a missing and a malformed NETWORK_PUBLIC_KEY — the fix is the same. */
    static final String NETWORK_PUBLIC_KEY_HELP = "Ask the t-0 team for the network public key and put it in .env.";

    /**
     * Reads and checks every setting, reporting the first one that is missing or unusable.
     *
     * @param env     a variable's value, or null when it is not set. Main passes dotenv's
     *                lookup, which takes the environment first and .env under it.
     * @param envFile the absolute path .env is read from, named in the messages
     * @param err     where the missing-.env notice goes
     * @throws ConfigurationException for the first setting that is missing or unusable
     */
    static Config load(Function<String, String> env, Path envFile, PrintStream err) {
        // Dotenv reads .env from the process working directory, so run the app from the
        // directory holding your .env. Say where we looked — otherwise a .env one
        // directory up looks exactly like a .env that is not filled in.
        if (!Files.exists(envFile)) {
            err.println(missingEnvFileNotice(envFile));
        }

        // An empty value counts as unset; the keys are trimmed.
        String privateKey = strip(env.apply("PROVIDER_PRIVATE_KEY"));
        String networkPublicKey = strip(env.apply("NETWORK_PUBLIC_KEY"));
        String endpoint = orDefault(env.apply("TZERO_ENDPOINT"), DEFAULT_TZERO_ENDPOINT);

        if (privateKey.isEmpty()) {
            // Never "copy the example over it". A scaffolded project's .env already
            // holds the generated key, its public half is with the t-0 team, and the
            // private half exists nowhere else — overwriting it ends the integration.
            // The likelier cause is the working directory, since that is where .env
            // is read from.
            throw new ConfigurationException(
                    "PROVIDER_PRIVATE_KEY is not set",
                    ".env is read from the working directory, and we looked in " + envFile
                            + ". Run the app from the directory holding your .env, or set "
                            + "PROVIDER_PRIVATE_KEY in the environment. Only a project with no .env "
                            + "at all starts one from .env.example — an existing .env holds the key "
                            + "generated for you, and its private half is not recoverable.");
        }

        // Print the public key early — Phase 1 needs it before NETWORK_PUBLIC_KEY
        // arrives, so a missing network key must not block the print.
        String publicKey;
        try {
            publicKey = Signer.publicKeyFromPrivateKey(privateKey);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(
                    "PROVIDER_PRIVATE_KEY is not usable: " + e.getMessage(),
                    "Any 32 random bytes will do: openssl rand -hex 32.");
        }
        log.info("Acquirer public key: {}", publicKey);
        // TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

        if (networkPublicKey.isEmpty()) {
            throw new ConfigurationException("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP);
        }

        // Last, so the keys — which nobody can guess for you — are reported before a
        // setting that has a working default.
        int port = parsePort(env.apply("PORT"));

        return new Config(privateKey, networkPublicKey, endpoint, port);
    }

    static String missingEnvFileNotice(Path envFile) {
        return "No .env at " + envFile + " — taking configuration from the environment instead";
    }

    /**
     * Checked here so a typo reports as configuration. Left to {@code Integer.parseInt}
     * it would take a sign or non-ASCII digits, and fail on anything else as a
     * NumberFormatException that names neither the setting nor the value.
     *
     * @param value PORT as set; trimmed, and unset or empty means 8080
     */
    static int parsePort(String value) {
        String trimmed = orDefault(value == null ? null : value.strip(), "8080");
        // ASCII digits only: no sign, no 0x, no exponent, no underscore.
        if (trimmed.matches("[0-9]+")) {
            try {
                int port = Integer.parseInt(trimmed);
                if (port >= 1 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException e) {
                // More digits than an int holds — out of range all the same.
            }
        }
        // The value as set, spaces and all: it is what the reader has to find in .env.
        throw new ConfigurationException(
                "PORT is not a valid port number: " + value,
                "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.");
    }

    private static String orDefault(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
