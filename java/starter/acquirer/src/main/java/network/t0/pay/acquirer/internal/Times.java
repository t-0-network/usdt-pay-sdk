package network.t0.pay.acquirer.internal;

import com.google.protobuf.Timestamp;

import java.time.Instant;

/**
 * Timestamps on the wire are absolute UTC.
 *
 * <p>Use {@link #format} in logs: protobuf's own {@code Timestamp.toString()} is
 * multi-line text format and turns one log line into six.
 */
public final class Times {

    public static Timestamp from(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static String format(Timestamp timestamp) {
        return toInstant(timestamp).toString();
    }

    private Times() {
    }
}
