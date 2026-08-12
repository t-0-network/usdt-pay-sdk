package network.t0.pay.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Gives a long-lived stub a default deadline, applied fresh to every call.
 *
 * <p>Install it once where the stub is built and every call through that stub is
 * bounded:
 *
 * <pre>{@code
 * var t0 = BlockingNetworkClient.create(endpoint, signer,
 *         channel -> IssuerServiceGrpc.newBlockingStub(channel)
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
 * <p><strong>Do not reach for {@code BlockingNetworkClient.create(endpoint, signer,
 * stubFactory, timeoutSeconds)} instead.</strong> As of provider-sdk-java 1.1.25 that
 * fourth argument is never read — {@code NetworkClient.createChannel} ignores it — so
 * it looks like this knob and silently is not one. This class exists until that is
 * fixed upstream.
 *
 * <p><strong>If you copy one {@code internal/} helper out on its own,</strong> copy
 * this with it or set a deadline at the call site. The helpers rely on the stub they
 * are handed already carrying this interceptor; on a bare stub they would block on a
 * stalled t-0 indefinitely.
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
