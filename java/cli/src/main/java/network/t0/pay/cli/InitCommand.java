package network.t0.pay.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Scaffolds a USDt Pay integration from one of the starters this jar carries.
 */
@Command(
    name = "usdt-pay-init",
    mixinStandardHelpOptions = true,
    versionProvider = InitCommand.VersionProvider.class,
    description = "Create a new USDt Pay project from a starter"
)
public class InitCommand implements Callable<Integer> {

    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    @Parameters(
        index = "0",
        description = "Project name (also the directory name)",
        defaultValue = ""
    )
    private String projectName;

    @Option(
        names = {"-s", "--starter"},
        description = "Which role to scaffold. Omit to pick from the starters this jar carries."
    )
    private String starter;

    @Option(
        names = {"-d", "--directory"},
        description = "Where to create the project (defaults to the current directory)"
    )
    private Path directory;

    @Option(
        names = {"--no-color"},
        description = "Disable colored output"
    )
    private boolean noColor;

    private BufferedReader stdinReader;

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new InitCommand()).execute(args));
    }

    @Override
    public Integer call() {
        try {
            printHeader();

            List<String> starters = TemplateExtractor.availableStarters();
            if (starters.isEmpty()) {
                printError("This jar carries no starters — it was built wrong.");
                return 1;
            }

            String role = resolveStarter(starters);
            if (role == null) {
                return 1;
            }

            if (projectName == null || projectName.isEmpty()) {
                System.out.print("Enter your project name: ");
                System.out.flush();
                projectName = readLine();
            }
            projectName = sanitizeProjectName(projectName);
            if (projectName.isEmpty()) {
                printError("Invalid project name. Use only letters, numbers, and hyphens.");
                return 1;
            }

            Path targetDir = directory != null ? directory.resolve(projectName) : Path.of(projectName);
            if (Files.exists(targetDir)) {
                printError("Directory '" + targetDir + "' already exists. Please choose a different name.");
                return 1;
            }

            printInfo("Creating " + role + " project: " + projectName);
            Files.createDirectories(targetDir);

            printInfo("Extracting starter...");
            TemplateExtractor.extractTo(targetDir, projectName, role);
            printSuccess("Starter extracted");

            printInfo("Generating secp256k1 keypair...");
            KeyGenerator.KeyPair keyPair = KeyGenerator.generate();
            printSuccess("Keypair generated");

            printInfo("Writing .env...");
            EnvFileWriter.write(targetDir, keyPair);
            printSuccess("Environment configured");

            printCompletionMessage(targetDir, keyPair.publicKeyHex());
            return 0;

        } catch (Exception e) {
            printError("Failed to initialize project: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /** @return the chosen role, or null when the caller named one that does not exist */
    private String resolveStarter(List<String> starters) throws IOException {
        if (starter != null) {
            String requested = starter.trim().toLowerCase();
            if (starters.contains(requested)) {
                return requested;
            }
            printError("No starter named '" + starter + "'. Available: " + String.join(", ", starters));
            return null;
        }

        // One starter and no preference stated is not a question worth asking.
        if (starters.size() == 1) {
            return starters.get(0);
        }

        println("");
        println("Select a starter:");
        for (int i = 0; i < starters.size(); i++) {
            println("  " + color(BLUE, (i + 1) + ")") + " " + starters.get(i));
        }
        println("");
        System.out.print("Enter choice [1]: ");
        System.out.flush();

        String input = readLine();
        if (input == null || input.trim().isEmpty()) {
            return starters.get(0);
        }
        try {
            int choice = Integer.parseInt(input.trim());
            if (choice >= 1 && choice <= starters.size()) {
                return starters.get(choice - 1);
            }
        } catch (NumberFormatException ignored) {
            // falls through to the same error
        }
        printError("Invalid choice.");
        return null;
    }

    private String readLine() throws IOException {
        Console console = System.console();
        if (console != null) {
            return console.readLine();
        }
        if (stdinReader == null) {
            stdinReader = new BufferedReader(new InputStreamReader(System.in));
        }
        return stdinReader.readLine();
    }

    private String sanitizeProjectName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase()
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9-]", "");
    }

    private void printHeader() {
        println("");
        println(color(BLUE, "+-----------------------------------------------------------+"));
        println(color(BLUE, "|") + "            USDt Pay SDK - Java Initializer                " + color(BLUE, "|"));
        println(color(BLUE, "+-----------------------------------------------------------+"));
        println("");
    }

    private void printCompletionMessage(Path targetDir, String publicKey) {
        println("");
        println(color(GREEN, "Project created at ") + color(BLUE, targetDir.toAbsolutePath().toString()));
        println("");
        println(color(YELLOW, "Your public key — send it to the t-0 team, they cannot accept your calls until they have it:"));
        println(color(BLUE, "0x" + publicKey));
        println("");
        println(color(YELLOW, "Next steps:"));
        println("");
        println("  1. " + color(BLUE, "cd " + targetDir));
        println("  2. Put the t-0 network public key in " + color(BLUE, ".env") + " (NETWORK_PUBLIC_KEY)");
        println("  3. " + color(BLUE, "./gradlew installDist") + ", then read " + color(BLUE, "README.md") + " for the phases");
        println("");
        println("Docs: " + color(BLUE, "https://usdt-pay-docs.t-0.network/"));
        println("");
    }

    private void printInfo(String message) {
        println(color(BLUE, "[INFO]") + " " + message);
    }

    private void printSuccess(String message) {
        println(color(GREEN, "[SUCCESS]") + " " + message);
    }

    private void printError(String message) {
        println(color(RED, "[ERROR]") + " " + message);
    }

    private void println(String message) {
        System.out.println(message);
    }

    private String color(String colorCode, String text) {
        return noColor ? text : colorCode + text + RESET;
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] { "usdt-pay-init " + Version.get() };
        }
    }
}
