package network.t0.pay.acquirer.internal;

import io.grpc.StatusRuntimeException;

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
    Optional<T> accepted();

    /** True only when the outcome is unknown and the call must be retried under the same key. */
    boolean shouldRetry();

    /** t-0 accepted it. The operation is done; record it and move on. */
    record Accepted<T>(T value) implements Outcome<T> {

        @Override
        public Optional<T> accepted() {
            return Optional.of(value);
        }

        @Override
        public boolean shouldRetry() {
            return false;
        }
    }

    /**
     * t-0 refused this payload and will keep refusing it — retrying it unchanged
     * is pointless. The key is <em>not</em> consumed: correct the fields named by
     * {@code reason} and resend under the same key.
     *
     * <p>Covers the synchronous {@code failure} and the asynchronous {@code rejected}
     * response variants, and a call t-0 refused with a status code that says so
     * (see {@link #fromError}) — from the caller's side they mean the same thing.
     */
    record Rejected<T>(String reason) implements Outcome<T> {

        @Override
        public Optional<T> accepted() {
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
        public Optional<T> accepted() {
            return Optional.empty();
        }

        @Override
        public boolean shouldRetry() {
            return true;
        }
    }

    /**
     * A failed call, classified by its status code. Five codes mean t-0 read the
     * request and refused it, and the same bytes would be refused the same way
     * forever — treating those as retryable is how you get an infinite loop against
     * a request with a typo in it. They are {@link Rejected}, with the reason
     * {@code "<CODE>: <description>"}.
     *
     * <p>Every other code is {@link Unknown}: a transport failure, a deadline or a
     * server error may still have committed on t-0's side, so the key has to be
     * retried.
     */
    static <T> Outcome<T> fromError(StatusRuntimeException e) {
        return switch (e.getStatus().getCode()) {
            case INVALID_ARGUMENT, UNAUTHENTICATED, PERMISSION_DENIED, UNIMPLEMENTED, FAILED_PRECONDITION ->
                    // grpc-java's own rendering: "INVALID_ARGUMENT: <description>", or
                    // the code alone when the status carries no description.
                    new Rejected<>(e.getMessage());
            default -> new Unknown<>(e.getStatus().toString());
        };
    }
}
