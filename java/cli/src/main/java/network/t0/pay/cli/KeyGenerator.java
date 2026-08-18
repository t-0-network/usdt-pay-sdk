package network.t0.pay.cli;

import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Signer;

import java.security.SecureRandom;

/**
 * Generates the secp256k1 keypair a new project signs its requests with. The public
 * half is what the t-0 team registers you by.
 */
public final class KeyGenerator {

    private static final int PRIVATE_KEY_LENGTH = 32;

    private KeyGenerator() {
    }

    /** A keypair, both halves hex-encoded without a 0x prefix. */
    public record KeyPair(String privateKeyHex, String publicKeyHex) {}

    /**
     * @return a fresh keypair from {@link SecureRandom}
     */
    public static KeyPair generate() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] privateKeyBytes = new byte[PRIVATE_KEY_LENGTH];
        secureRandom.nextBytes(privateKeyBytes);

        // Derive the public half the same way the runtime does, so a key that works
        // here works there.
        Signer signer = Signer.fromBytes(privateKeyBytes);

        return new KeyPair(HexUtils.bytesToHex(privateKeyBytes), signer.getPublicKeyHex());
    }
}
