package network.t0.pay.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts on the extracted result, not on {@link TemplateExtractor}'s replacements.
 * The replacements are keyed on the starter README's exact wording, so they stop
 * applying silently when that README is edited — this is what catches that.
 */
class TemplateExtractorTest {

    @Test
    void carriesAtLeastOneStarter() throws IOException {
        assertFalse(TemplateExtractor.availableStarters().isEmpty(),
                "the jar's /templates listing is the CLI's role menu");
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
