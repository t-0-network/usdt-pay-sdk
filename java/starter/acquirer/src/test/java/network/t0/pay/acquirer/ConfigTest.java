package network.t0.pay.acquirer;

import network.t0.pay.server.UsdtPayServer;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configuration is read from a map here, never from the real environment, so the
 * machine running the tests cannot change their result.
 */
class ConfigTest {

    private static final String PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2";
    private static final String NETWORK_PUBLIC_KEY =
            "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    @TempDir
    Path dir;

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("PROVIDER_PRIVATE_KEY", PRIVATE_KEY);
        env.put("NETWORK_PUBLIC_KEY", NETWORK_PUBLIC_KEY);
        return env;
    }

    private Path envFile() {
        return dir.resolve(".env");
    }

    private Config load(Map<String, String> env) {
        return Config.load(env::get, envFile(), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private ConfigurationException refused(Map<String, String> env) {
        return assertThrows(ConfigurationException.class, () -> load(env));
    }

    @Test
    void defaults() {
        Config config = load(validEnv());

        assertEquals(PRIVATE_KEY, config.privateKey());
        assertEquals(NETWORK_PUBLIC_KEY, config.networkPublicKey());
        assertEquals("https://usdt-pay-api-sandbox.t-0.network", config.tzeroEndpoint());
        assertEquals(8080, config.port());
    }

    @Test
    void saysWhereItLookedWhenThereIsNoEnvFile() {
        load(validEnv());

        assertEquals("No .env at " + envFile() + " — taking configuration from the environment instead"
                + System.lineSeparator(), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void saysNothingAboutAnEnvFileThatExists() throws IOException {
        Files.writeString(envFile(), "");

        load(validEnv());

        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void trimsTheKeys() {
        Map<String, String> env = validEnv();
        env.put("PROVIDER_PRIVATE_KEY", "  " + PRIVATE_KEY + "\n");
        env.put("NETWORK_PUBLIC_KEY", "\t" + NETWORK_PUBLIC_KEY + " ");

        Config config = load(env);

        assertEquals(PRIVATE_KEY, config.privateKey());
        assertEquals(NETWORK_PUBLIC_KEY, config.networkPublicKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void anEmptyPrivateKeyIsNotSet(String value) {
        Map<String, String> env = validEnv();
        env.put("PROVIDER_PRIVATE_KEY", value);

        assertEquals("PROVIDER_PRIVATE_KEY is not set", refused(env).getMessage());
    }

    @Test
    void missingPrivateKey() {
        Map<String, String> env = validEnv();
        env.remove("PROVIDER_PRIVATE_KEY");

        ConfigurationException e = refused(env);

        assertEquals("PROVIDER_PRIVATE_KEY is not set", e.getMessage());
        assertEquals(".env is read from the working directory, and we looked in " + envFile()
                        + ". Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the"
                        + " environment. Only a project with no .env at all starts one from .env.example — an"
                        + " existing .env holds the key generated for you, and its private half is not recoverable.",
                e.getHelpMessage());
    }

    @Test
    void malformedPrivateKey() {
        Map<String, String> env = validEnv();
        env.put("PROVIDER_PRIVATE_KEY", "0x1234");
        String sdkMessage = assertThrows(IllegalArgumentException.class,
                () -> Signer.publicKeyFromPrivateKey("0x1234")).getMessage();

        ConfigurationException e = refused(env);

        assertEquals("PROVIDER_PRIVATE_KEY is not usable: " + sdkMessage, e.getMessage());
        assertEquals("Any 32 random bytes will do: openssl rand -hex 32.", e.getHelpMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void missingNetworkPublicKey(String value) {
        Map<String, String> env = validEnv();
        env.put("NETWORK_PUBLIC_KEY", value);

        ConfigurationException e = refused(env);

        assertEquals("NETWORK_PUBLIC_KEY is not set", e.getMessage());
        assertEquals("Ask the t-0 team for the network public key and put it in .env.", e.getHelpMessage());
    }

    @Test
    void malformedNetworkPublicKey() {
        // The SDK checks this one when the server is set up, before it binds.
        Map<String, String> env = validEnv();
        env.put("NETWORK_PUBLIC_KEY", "04zz");
        Config config = load(env);
        String sdkMessage = assertThrows(IllegalArgumentException.class,
                () -> UsdtPayServer.create(config.port(), "04zz")).getMessage();

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> Main.startCallbackServer(config));

        // The SDK's message, unchanged.
        assertTrue(sdkMessage.startsWith("invalid network public key: "), sdkMessage);
        assertEquals(sdkMessage, e.getMessage());
        assertEquals("Ask the t-0 team for the network public key and put it in .env.", e.getHelpMessage());
    }

    @Test
    void anEmptyEndpointIsUnset() {
        Map<String, String> env = validEnv();
        env.put("TZERO_ENDPOINT", "");

        assertEquals("https://usdt-pay-api-sandbox.t-0.network", load(env).tzeroEndpoint());
    }

    @Test
    void takesTheEndpointAsSet() {
        Map<String, String> env = validEnv();
        env.put("TZERO_ENDPOINT", "http://localhost:9000");

        assertEquals("http://localhost:9000", load(env).tzeroEndpoint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void anEmptyPortIsUnset(String value) {
        Map<String, String> env = validEnv();
        env.put("PORT", value);

        assertEquals(8080, load(env).port());
    }

    @Test
    void readsAPort() {
        Map<String, String> env = validEnv();
        env.put("PORT", " 8081 ");
        assertEquals(8081, load(env).port());

        env.put("PORT", "1");
        assertEquals(1, load(env).port());

        env.put("PORT", "65535");
        assertEquals(65535, load(env).port());

        // Digits, value 1 to 65535: leading zeros are still digits.
        env.put("PORT", "008080");
        assertEquals(8080, load(env).port());
    }

    // Out of range, a sign, hex, an exponent, an underscore, a fraction, words, more
    // digits than an int holds, and fullwidth and Arabic-Indic digits.
    @ParameterizedTest
    @ValueSource(strings = {
            "0", "65536", "-1", "+80", "0x50", "8e3", "8_080", "80.0", "eighty", " 80x ",
            "99999999999", "８０", "٨٠"})
    void refusesAPortThatIsNotOne(String value) {
        Map<String, String> env = validEnv();
        env.put("PORT", value);

        ConfigurationException e = refused(env);

        // The value as set, untrimmed.
        assertEquals("PORT is not a valid port number: " + value, e.getMessage());
        assertEquals("Set PORT to an integer between 1 and 65535, or leave it unset for 8080.", e.getHelpMessage());
    }

    @Test
    void reportsTheKeysFirst() {
        // Both keys missing: the private key, which comes first.
        Map<String, String> env = new HashMap<>();
        assertEquals("PROVIDER_PRIVATE_KEY is not set", refused(env).getMessage());

        // A malformed private key before a missing network key.
        env.put("PROVIDER_PRIVATE_KEY", "0x1234");
        String message = refused(env).getMessage();
        assertTrue(message.startsWith("PROVIDER_PRIVATE_KEY is not usable: "), message);

        // A missing network key before a bad PORT.
        env.put("PROVIDER_PRIVATE_KEY", PRIVATE_KEY);
        env.put("PORT", "eighty");
        assertEquals("NETWORK_PUBLIC_KEY is not set", refused(env).getMessage());
    }
}
