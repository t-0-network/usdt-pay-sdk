package network.t0.pay.acquirer;

/**
 * Configuration is missing or unusable — the process cannot start. Main prints the
 * message, then the help, and exits 1.
 */
final class ConfigurationException extends RuntimeException {

    private final String helpMessage;

    ConfigurationException(String message, String helpMessage) {
        super(message);
        this.helpMessage = helpMessage;
    }

    /** What to do about it. */
    String getHelpMessage() {
        return helpMessage;
    }
}
