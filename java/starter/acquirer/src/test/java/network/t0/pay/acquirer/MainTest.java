package network.t0.pay.acquirer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @Test
    void aConfigurationErrorIsTwoBareLines() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Main.printConfigurationError(new PrintStream(out, true, StandardCharsets.UTF_8),
                new ConfigurationException("NETWORK_PUBLIC_KEY is not set",
                        "Ask the t-0 team for the network public key and put it in .env."));

        assertEquals("ERROR: NETWORK_PUBLIC_KEY is not set" + System.lineSeparator()
                        + "Ask the t-0 team for the network public key and put it in .env." + System.lineSeparator(),
                out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void anyOtherStartupFailureIsOneLine() {
        assertEquals("Acquirer failed to start: the base URL is not valid",
                Main.startupFailure(new IllegalArgumentException("the base URL is not valid")));
    }

    @Test
    void aFailureWithNoMessageIsNamedByItsType() {
        assertEquals("Acquirer failed to start: java.lang.IllegalStateException",
                Main.startupFailure(new IllegalStateException()));
    }
}
