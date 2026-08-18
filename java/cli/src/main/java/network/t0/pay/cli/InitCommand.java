package network.t0.pay.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

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

    // Colour follows the terminal, so piping or redirecting this output yields text
    // rather than escape codes — nobody has to remember --no-color for `| tee`.
    private static final boolean STDOUT_IS_TERMINAL = stdoutIsTerminal();

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

    @Spec
    private CommandSpec spec;

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

            // Said plainly, because the alternative is worse than an error: a third
            // argument silently ignored leaves `starter` unset, and the failure that
            // follows claims a role is missing when one was given.
            if (args.size() > 2) {
                printError("Too many arguments: " + String.join(" ", args));
                spec.commandLine().usage(System.out);
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

            try {
                printInfo("Extracting starter...");
                TemplateExtractor.extractTo(targetDir, projectName, role);
                printSuccess("Starter extracted");

                printInfo("Generating secp256k1 keypair...");
                KeyGenerator.KeyPair keyPair = KeyGenerator.generate();
                printSuccess("Keypair generated");

                printInfo("Writing .env...");
                EnvFileWriter.write(targetDir, keyPair);
                printSuccess("Environment configured");

                printCompletionMessage(targetDir, role, keyPair.publicKeyHex());
            } catch (Exception e) {
                // A half-written project holding a half-written .env is worse than
                // none — and left behind it holds the name too, so the obvious retry
                // stops at "already exists" instead. Safe to delete because this try
                // opens after the createDirectories above, which the exists check
                // guards: the directory can only be one this run made.
                deleteTree(targetDir);
                throw e;
            }
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

    /** Reverse order so children go before the parent — {@code delete} needs them empty. */
    private void deleteTree(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            // Best effort. The scaffold has already failed, and throwing from the
            // cleanup would replace the message that says why with this one.
            printError("Could not remove the partial project at " + root + ": " + e.getMessage());
        }
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

    private void printCompletionMessage(Path targetDir, String role, String publicKey) {
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
        println("  3. " + color(BLUE, "./gradlew installDist"));
        // The last step is the point of the first three, and stopping at the build
        // leaves it unsaid. The binary is named for the role — that is the starter's
        // `applicationName`, and renameProject does not touch it.
        println("  4. " + color(BLUE, "./build/install/" + role + "/bin/" + role)
                + " — run it from the project root, where your " + color(BLUE, ".env") + " is");
        println("");
        println("Then read " + color(BLUE, "README.md") + " for the phases.");
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
        return noColor || !STDOUT_IS_TERMINAL ? text : colorCode + text + RESET;
    }

    /**
     * A non-null {@code System.console()} was the whole answer until Java 22, which
     * started returning one for a redirected stream too; from there {@code isTerminal()}
     * is the only honest check. Called reflectively because this jar compiles to a 17
     * floor, where the method does not exist — and on 17 through 21 the null check
     * above has already answered it.
     */
    private static boolean stdoutIsTerminal() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] { "usdt-pay-init " + Version.get() };
        }
    }
}
