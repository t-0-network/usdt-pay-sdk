package network.t0.pay.signature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.t0.sdk.common.Headers;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignatureVerifier;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@code vectors/signature-v1.json} against the Java crypto.
 *
 * <p>The vectors were produced with a different curve library than this one signs with, so
 * a passing run is two independent implementations agreeing on every byte — which is the
 * whole reason the file exists. What it pins here is the algorithm: the digest, the
 * signature bytes, and which signatures verify. The window and the reject codes live in
 * the interceptor and are exercised over the wire by the Node suite.
 */
class SignatureVectorsTest {

    private static final JsonObject VECTORS = load();
    private static final byte[] TRUSTED_KEY = trustedKey();

    @Test
    void thePrivateKeyInTheVectorsDerivesTheirPublicKey() {
        assertEquals(
                key(VECTORS.get("trustedKey").getAsString()).get("publicKey").getAsString(),
                signer().getPublicKeyHexPrefixed());
    }

    /** Every signing vector, re-derived from the private key and the payload. */
    @TestFactory
    Stream<DynamicTest> signing() {
        Signer signer = signer();

        return cases("signing").map(vector -> DynamicTest.dynamicTest(name(vector), () -> {
            byte[] digest = digestOf(vector);

            assertEquals(vector.get("digest").getAsString(), hex(digest), "digest");
            assertEquals(
                    vector.get("signature").getAsString(),
                    hex(signer.sign(digest).getSignature()),
                    "signature");
            assertTrue(
                    SignatureVerifier.verify(TRUSTED_KEY, digest, bytes(vector, "signature")),
                    "its own signature has to verify");
        }));
    }

    /**
     * The verification vectors a verifier settles with the curve alone. The ones marked
     * {@code timestamp} turn on the clock and {@code identity} on which key is presented —
     * both belong to the interceptor, and the Node suite drives them over HTTP.
     */
    @TestFactory
    Stream<DynamicTest> verification() {
        return cases("verification")
                .filter(vector -> "signature".equals(vector.get("check").getAsString()))
                .map(vector -> DynamicTest.dynamicTest(name(vector), () -> assertEquals(
                        vector.get("accept").getAsBoolean(),
                        SignatureVerifier.verify(
                                TRUSTED_KEY, digestOf(vector), bytes(vector, "signature")),
                        vector.get("note").getAsString())));
    }

    private static byte[] digestOf(JsonObject vector) {
        return Keccak256.hash(
                bytes(vector, "payload"),
                Headers.encodeTimestamp(vector.get("timestampMs").getAsLong()));
    }

    private static Signer signer() {
        return Signer.fromHex(
                key(VECTORS.get("trustedKey").getAsString()).get("privateKey").getAsString());
    }

    private static byte[] trustedKey() {
        return SignatureVerifier.parsePublicKeyHex(
                key(VECTORS.get("trustedKey").getAsString()).get("publicKey").getAsString());
    }

    private static JsonObject key(String name) {
        return VECTORS.getAsJsonObject("keys").getAsJsonObject(name);
    }

    private static Stream<JsonObject> cases(String array) {
        return StreamSupport.stream(VECTORS.getAsJsonArray(array).spliterator(), false)
                .map(JsonElement::getAsJsonObject);
    }

    private static String name(JsonObject vector) {
        return vector.get("name").getAsString();
    }

    private static byte[] bytes(JsonObject vector, String field) {
        return HexUtils.hexToBytes(HexUtils.stripHexPrefix(vector.get(field).getAsString()));
    }

    private static String hex(byte[] bytes) {
        return "0x" + HexUtils.bytesToHex(bytes);
    }

    private static JsonObject load() {
        try (InputStream stream =
                SignatureVectorsTest.class.getResourceAsStream("/signature-v1.json")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "signature-v1.json is not on the test classpath — see sourceSets in build.gradle.kts");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read the signature vectors", e);
        }
    }
}
