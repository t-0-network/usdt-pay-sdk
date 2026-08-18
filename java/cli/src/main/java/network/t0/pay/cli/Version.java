package network.t0.pay.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The version this jar was built at. It is also the SDK version the scaffolded
 * project is pinned to, because the two are released together off one tag.
 */
public final class Version {

    private static final String VERSION_PROPERTIES = "/version.properties";
    private static final String DEFAULT_VERSION = "unknown";

    private static String cachedVersion;

    private Version() {
    }

    /**
     * @return the version string, or {@code "unknown"} if the resource is missing
     */
    public static String get() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        try (InputStream is = Version.class.getResourceAsStream(VERSION_PROPERTIES)) {
            if (is == null) {
                cachedVersion = DEFAULT_VERSION;
                return cachedVersion;
            }

            Properties props = new Properties();
            props.load(is);
            cachedVersion = props.getProperty("version", DEFAULT_VERSION);
            return cachedVersion;
        } catch (IOException e) {
            cachedVersion = DEFAULT_VERSION;
            return cachedVersion;
        }
    }
}
