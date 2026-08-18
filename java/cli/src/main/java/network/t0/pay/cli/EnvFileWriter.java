package network.t0.pay.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the project's {@code .env} from its {@code .env.example}, with the generated
 * keypair filled in.
 */
public final class EnvFileWriter {

    private EnvFileWriter() {
    }

    /**
     * @param targetDir the scaffolded project, which must already contain {@code .env.example}
     * @param keyPair   the generated keypair
     * @throws IOException if reading the example or writing {@code .env} fails
     */
    public static void write(Path targetDir, KeyGenerator.KeyPair keyPair) throws IOException {
        String content = Files.readString(targetDir.resolve(".env.example"));

        // Line-anchored so this cannot hit a PRIVATE_KEY mentioned in a comment, and
        // so it fills the empty assignment rather than appending to a set one.
        content = content.replaceFirst(
                "(?m)^PRIVATE_KEY=$",
                "PRIVATE_KEY=" + keyPair.privateKeyHex()
                        + "\n\n# The public half of the key above — send this to the t-0 team."
                        + "\n# 0x" + keyPair.publicKeyHex());

        Files.writeString(targetDir.resolve(".env"), content);
    }
}
