package network.t0.pay.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts on the extracted result, not on {@link TemplateExtractor}'s replacements.
 * The replacements are keyed on the starter README's exact wording, so they stop
 * applying silently when that README is edited — this is what catches that.
 */
class TemplateExtractorTest {

    /**
     * What a developer-experience review took out, held out. Two of these destroy
     * data — a scaffolded {@code .env} is the only copy of the generated private key,
     * whose public half is already with the t-0 team — and two are simply untrue: the
     * SDK is on Maven Central, and standalone is the mode every scaffolded project
     * builds in. They came from three different files, which is why the check below
     * reads the whole extracted tree rather than any one of them.
     */
    private static final List<String> BANNED = List.of(
            "Put your private key",
            "Copy .env.example",
            "not published yet",
            "once the artifact is published");

    /**
     * Spelled out rather than asserted non-empty. The listing is the CLI's role menu,
     * and `usdt-pay-init.jar my-acquirer acquirer` is a published invocation — a role
     * appearing, vanishing or being renamed is an API change, so it has to be made
     * here deliberately instead of following whatever `processResources` happened to
     * copy.
     */
    @Test
    void carriesExactlyTheDeclaredStarters() throws IOException {
        assertEquals(List.of("acquirer"), TemplateExtractor.availableStarters());
    }

    @Test
    void extractedReadmeNeverTellsYouToOverwriteTheGeneratedEnv(@TempDir Path tmp) throws IOException {
        for (String role : TemplateExtractor.availableStarters()) {
            Path project = tmp.resolve(role);
            Files.createDirectories(project);
            TemplateExtractor.extractTo(project, "my-" + role, role);

            Path readme = project.resolve("README.md");
            assertTrue(Files.exists(readme), role + ": README.md extracted");
            String content = Files.readString(readme);

            // The one that loses data if it regresses. EnvFileWriter has already written
            // .env with the generated key, and its public half has been printed for the
            // user to send to t-0 — `cp .env.example .env` destroys the only copy.
            assertFalse(content.contains("cp .env.example .env"),
                    role + ": would destroy the generated key");
            assertFalse(content.contains("openssl rand -hex 32"),
                    role + ": the key is already generated");

            // The in-repo build instructions cannot run from an extracted project.
            assertFalse(content.contains("cd ../.."),
                    role + ": repo-relative path left in the README");
        }
    }

    @Test
    void extractedProjectRepeatsNoneOfTheWordingsTheReviewRemoved(@TempDir Path tmp) throws IOException {
        for (String role : TemplateExtractor.availableStarters()) {
            Path project = tmp.resolve(role);
            Files.createDirectories(project);
            TemplateExtractor.extractTo(project, "my-" + role, role);

            try (Stream<Path> files = Files.walk(project)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    // ISO-8859-1 maps every byte, so gradle-wrapper.jar reads as
                    // harmless mojibake instead of a MalformedInputException.
                    String content = new String(
                            Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                    for (String banned : BANNED) {
                        assertFalse(content.contains(banned),
                                role + ": '" + banned + "' is back, in " + project.relativize(file));
                    }
                }
            }
        }
    }

    @Test
    void extractedProjectCarriesTheExampleAndNoRealEnv(@TempDir Path tmp) throws IOException {
        for (String role : TemplateExtractor.availableStarters()) {
            Path project = tmp.resolve(role);
            Files.createDirectories(project);
            TemplateExtractor.extractTo(project, "my-" + role, role);

            assertTrue(Files.exists(project.resolve(".env.example")), role + ": .env.example");
            assertFalse(Files.exists(project.resolve(".env")),
                    role + ": the template must not carry a .env — EnvFileWriter writes it");

            // Standalone mode reads exactly this property; without it the scaffolded
            // build has no SDK version to resolve.
            List<String> props = Files.readAllLines(project.resolve("gradle.properties"));
            assertTrue(props.stream().anyMatch(l -> l.startsWith("usdtPaySdkVersion=")),
                    role + ": usdtPaySdkVersion pinned");
        }
    }
}
