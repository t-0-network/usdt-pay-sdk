package network.t0.pay.acquirer.internal;

import java.util.List;
import java.util.Optional;

/**
 * What a call to t-0 did, from the caller's point of view. Every state-changing
 * endpoint has an idempotency key, and which of these three you got decides what
 * to do with it.
 *
 * @param <T> the accepted payload — the response's {@code Success} or
 *            {@code Accepted} sub-message
 */
public sealed interface Outcome<T> {

    /** The payload when t-0 accepted the call; empty when it did not. */
    Optional<T> value();

    /** True only when the outcome is unknown and the call must be retried under the same key. */
    boolean shouldRetry();

    /** t-0 accepted it. The operation is done; record it and move on. */
    record Accepted<T>(T payload) implements Outcome<T> {

        @Override
        public Optional<T> value() {
            return Optional.of(payload);
        }

        @Override
        public boolean shouldRetry() {
            return false;
        }
    }

    /**
     * t-0 refused this payload and will keep refusing it — retrying it unchanged
     * is pointless. The key is <em>not</em> consumed: correct the fields named by
     * {@code reason} (and {@code failingIds}, where the endpoint reports them) and
     * resend under the same key.
     *
     * <p>Covers both the synchronous {@code failure} and the asynchronous
     * {@code rejected} response variants — from the caller's side they mean the
     * same thing.
     */
    record Rejected<T>(String reason, List<Long> failingIds) implements Outcome<T> {

        public Rejected(String reason) {
            this(reason, List.of());
        }

        @Override
        public Optional<T> value() {
            return Optional.empty();
        }

        @Override
        public boolean shouldRetry() {
            return false;
        }
    }

    /**
     * No answer came back. t-0 may or may not have committed the call — this is
     * exactly the case the idempotency key exists for. Retry with the same key and
     * identical content until you get an {@link Accepted} or a {@link Rejected}.
     */
    record Unknown<T>(String detail) implements Outcome<T> {

        @Override
        public Optional<T> value() {
            return Optional.empty();
        }

        @Override
        public boolean shouldRetry() {
            return true;
        }
    }
}
