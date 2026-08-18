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
import java.util.ArrayList;
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

    // [project-name] <role>, the same shape the Node generator takes. The role is
    // required and comes last; because it cannot be omitted, a lone argument can only
    // be the role and the project name is asked for instead.
    @Parameters(
        paramLabel = "[project-name] <role>",
        arity = "0..2",
        description = "Project name (also the directory name), then the role to scaffold."
    )
    private List<String> args = new ArrayList<>();

    private String projectName = "";

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

            // The role is required, so a lone argument can only be it — there is
            // nothing else it could have been. With two, the name comes first.
            if (args.size() == 1) {
                starter = args.get(0);
            } else if (args.size() == 2) {
                projectName = args.get(0);
                starter = args.get(1);
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

    /**
     * Required, deliberately — acquirer, issuer and lp are different integrations, and
     * which one you get is not something to infer.
     *
     * <p>Defaulting it would be a contract that changes under you: with one starter in
     * the jar a bare invocation would silently mean that role, and adding a second
     * starter would change what the same command does. Anything scripted against it
     * would switch roles without a character changing.
     *
     * @return the chosen role, or null when it is missing or names one that does not exist
     */
    private String resolveStarter(List<String> starters) {
        if (starter == null || starter.trim().isEmpty()) {
            printError("A role is required, and comes last. Available: " + String.join(", ", starters));
            return null;
        }
        String requested = starter.trim().toLowerCase();
        if (!starters.contains(requested)) {
            printError("No starter named '" + starter + "'. Available: " + String.join(", ", starters));
            return null;
        }
        return requested;
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
