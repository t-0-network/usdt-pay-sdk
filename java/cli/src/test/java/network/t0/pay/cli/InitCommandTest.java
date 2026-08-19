package network.t0.pay.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitCommandTest {

    /**
     * A test JVM has no console, which is the state CI, a piped script and a Dockerfile
     * are all in. Asserting on the message rather than the exit code, because the
     * unguarded prompt also exits 1: it reads EOF and reports the empty answer as
     * "Invalid project name", blaming the caller for a name they were never able to
     * give. Refusing, and saying why, is what the npm generator does.
     */
    @Test
    void refusesToPromptForTheProjectNameWithoutATerminal(@TempDir Path tmp) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        int exitCode;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            exitCode = new CommandLine(new InitCommand()).execute("-d", tmp.toString(), "acquirer");
        } finally {
            System.setOut(original);
        }

        String output = captured.toString(StandardCharsets.UTF_8);
        assertEquals(1, exitCode, "a role with no project name and no terminal must fail");
        assertTrue(output.contains("stdin is not a terminal"),
                "must say why it cannot ask, not blame the project name:\n" + output);
        try (Stream<Path> created = Files.list(tmp)) {
            assertTrue(created.findAny().isEmpty(), "nothing may be scaffolded");
        }
    }
}
