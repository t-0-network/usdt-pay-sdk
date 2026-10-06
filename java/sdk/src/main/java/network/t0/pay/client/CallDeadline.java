package network.t0.pay.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Gives a long-lived stub its own default deadline, applied fresh to every call.
 *
 * <p>Install it once where the stub is built and every call through that stub gets
 * this deadline instead of the client's default:
 *
 * <pre>{@code
 * var t0 = BlockingNetworkClient.create(endpoint, signer,
 *         channel -> AcquirerServiceGrpc.newBlockingStub(channel)
 *                 .withInterceptors(new CallDeadline(Duration.ofSeconds(10))));
 * }</pre>
 *
 * <p><strong>Why an interceptor and not {@code stub.withDeadlineAfter(...)}.</strong>
 * A gRPC {@link io.grpc.Deadline} is an absolute point in time, computed when
 * {@code withDeadlineAfter} is called — not a per-call duration. Build a stub once
 * with {@code withDeadlineAfter(10, SECONDS)} and keep it, and it works for ten
 * seconds and then fails every later call with {@code DEADLINE_EXCEEDED}. An
 * interceptor runs per call, so the deadline is computed per call.
 *
 * <p><strong>An explicit deadline wins.</strong> A call that already carries one —
 * from {@code stub.withDeadlineAfter(...)} at the call site, which sets it on the
 * {@link CallOptions} — is left alone. That is how a single RPC opts out of the
 * default without the default having to know about it.
 *
 * <p><strong>The client already has a default.</strong> Since provider-sdk-java 1.2.0,
 * {@code BlockingNetworkClient} gives every call a deadline of 15 s (5 min for
 * streams), set with {@code create(endpoint, signer, stubFactory, Duration timeout,
 * Duration streamTimeout)}; the {@code int timeoutSeconds} overload is honored but
 * deprecated. That default is a channel interceptor that fills in a deadline only when
 * the call has none, and a stub interceptor runs first — so this class replaces the
 * client's default for the stub it is installed on, and a call-site deadline still
 * wins over both.
 *
 * <p><strong>If you copy one {@code internal/} helper out on its own,</strong> copy
 * this with it or set a deadline at the call site. The helpers are written for the
 * stub they are handed already carrying this interceptor; on a bare stub they get the
 * client's 15 s default instead.
 *
 * @param timeout how long a call may run before it fails {@code DEADLINE_EXCEEDED}
 */
public record CallDeadline(Duration timeout) implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return next.newCall(method, callOptions.getDeadline() == null
                ? callOptions.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                : callOptions);
    }
}
