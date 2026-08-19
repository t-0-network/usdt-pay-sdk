package network.t0.pay.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Unpacks a starter into a new project directory.
 *
 * <p>Each starter is a verbatim copy of {@code java/starter/<role>}, carried in this
 * jar under {@code /templates/<role>}. That listing <em>is</em> the set of roles the
 * CLI offers — there is no registry to update when a starter is added.
 *
 * <p>Two things cannot be verbatim, because the in-repo starter builds against the
 * local {@code :sdk} project and an extracted one cannot:
 * <ul>
 *   <li>{@code /overlay/<role>} holds files that replace the template's — the
 *       Dockerfile, whose in-repo form needs the repo root as its build context;</li>
 *   <li>a {@code gradle.properties} pinning {@code usdtPaySdkVersion}, which is what
 *       the starter's standalone mode reads.</li>
 * </ul>
 */
public final class TemplateExtractor {

    private static final String TEMPLATES = "/templates";
    private static final String OVERLAY = "/overlay";

    private TemplateExtractor() {
    }

    /**
     * @return the roles this jar carries a starter for, sorted
     * @throws IOException if the jar's own resources cannot be read
     */
    public static List<String> availableStarters() throws IOException {
        List<String> roles = withResource(TEMPLATES, root -> {
            try (Stream<Path> children = Files.list(root)) {
                return children.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString().replace("/", ""))
                        .sorted()
                        .toList();
            }
        });
        return roles == null ? List.of() : roles;
    }

    /**
     * @param targetDir   the (already created) project directory
     * @param projectName the name to stamp into {@code settings.gradle.kts}
     * @param starter     one of {@link #availableStarters()}
     * @throws IOException if extraction fails
     */
    public static void extractTo(Path targetDir, String projectName, String starter) throws IOException {
        Boolean copied = withResource(TEMPLATES + "/" + starter, source -> {
            copyTree(source, targetDir);
            return true;
        });
        if (copied == null) {
            throw new IOException("no starter named '" + starter + "' in this jar");
        }

        // Optional, and applied second so it wins over the template.
        withResource(OVERLAY + "/" + starter, source -> {
            copyTree(source, targetDir);
            return true;
        });

        restoreGitignore(targetDir);
        pinSdkVersion(targetDir);
        renameProject(targetDir, projectName);
        deRepoReadme(targetDir);

        // A zip carries no POSIX modes, so gradlew comes out non-executable.
        makeExecutable(targetDir.resolve("gradlew"));
    }

    /**
     * The template carries the starter's ignore file undotted, because Ant's default
     * excludes drop every {@code .gitignore} from a directory scan and each Gradle copy
     * on the way into this jar is one. Without the name back, a scaffolded project
     * commits its own {@code .env} on the first {@code git add -A}.
     */
    private static void restoreGitignore(Path targetDir) throws IOException {
        Path undotted = targetDir.resolve("gitignore");
        if (Files.exists(undotted)) {
            Files.move(undotted, targetDir.resolve(".gitignore"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The starter's standalone mode reads exactly this property. */
    private static void pinSdkVersion(Path targetDir) throws IOException {
        Path props = targetDir.resolve("gradle.properties");
        String line = "usdtPaySdkVersion=" + Version.get() + "\n";
        if (Files.exists(props)) {
            Files.writeString(props, Files.readString(props) + "\n" + line);
        } else {
            Files.writeString(props, "# The published SDK this project builds against.\n" + line);
        }
    }

    private static void renameProject(Path targetDir, String projectName) throws IOException {
        Path settings = targetDir.resolve("settings.gradle.kts");
        if (!Files.exists(settings)) {
            return;
        }
        Files.writeString(settings, Files.readString(settings).replaceFirst(
                "(?m)^rootProject\\.name\\s*=\\s*\".*\"$",
                Matcher.quoteReplacement("rootProject.name = \"" + projectName + "\"")));
    }

    /**
     * The starter README documents the in-repo build, which an extracted project
     * cannot run. These rewrites are keyed on that README's exact wording; if it
     * changes they simply stop applying, which is why {@code TemplateExtractorTest}
     * asserts on the extracted result rather than on this method.
     */
    private static void deRepoReadme(Path targetDir) throws IOException {
        Path readme = targetDir.resolve("README.md");
        if (!Files.exists(readme)) {
            return;
        }
        String content = Files.readString(readme);

        // The whole Run-it block, not the build line inside it. The first line of the
        // in-repo version is `cp .env.example .env`, and following it in a scaffolded
        // project overwrites the .env EnvFileWriter just produced — destroying the only
        // copy of the generated private key, whose public half the user has already been
        // told to send to the t-0 team.
        content = content.replace(
                """
                cp .env.example .env      # then fill in PRIVATE_KEY and NETWORK_PUBLIC_KEY

                # Build from the java/ root — the starter compiles against the local :sdk project.
                (cd ../.. && ./gradlew :starter:acquirer:installDist)""",
                """
                # .env already exists and holds the PRIVATE_KEY generated for you. Do not
                # overwrite it — add NETWORK_PUBLIC_KEY, which the t-0 team gives you.

                ./gradlew installDist""");

        content = content.replace(
                "- A secp256k1 private key. Any 32 random bytes will do: `openssl rand -hex 32`.",
                "- Your secp256k1 private key — already generated, in `.env`.");

        content = content.replace(
                """
                The build context is the repository root — `java/sdk/src/main/proto` is a symlink
                into `proto/`, so a narrower context cannot resolve it.""",
                "The build context is this project directory.");

        content = content.replace(
                """
                cd ../../..                 # repository root
                docker build -f java/starter/acquirer/Dockerfile -t usdt-pay-acquirer .
                docker run -p 8080:8080 --env-file java/starter/acquirer/.env usdt-pay-acquirer""",
                """
                docker build -t usdt-pay-acquirer .
                docker run -p 8080:8080 --env-file .env usdt-pay-acquirer""");

        Files.writeString(readme, content);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                // Across file systems (zip → default) only the string form travels.
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void makeExecutable(Path file) throws IOException {
        if (!Files.exists(file) || Files.getFileAttributeView(file, PosixFileAttributeView.class) == null) {
            return;
        }
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private interface PathAction<T> {
        T apply(Path root) throws IOException;
    }

    /**
     * Runs {@code action} against a resource directory, whether this is a shadow jar
     * or an exploded build directory. Returns null when the resource is absent.
     */
    private static <T> T withResource(String resourcePath, PathAction<T> action) throws IOException {
        URL url = TemplateExtractor.class.getResource(resourcePath);
        if (url == null) {
            return null;
        }

        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("cannot locate " + resourcePath, e);
        }

        if (!"jar".equals(uri.getScheme())) {
            return action.apply(Path.of(uri));
        }
        try (FileSystem jar = FileSystems.newFileSystem(URI.create(uri.toString().split("!")[0]), Map.of())) {
            return action.apply(jar.getPath(resourcePath));
        }
    }
}
