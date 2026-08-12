package network.t0.pay.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import network.t0.pay.proto.tzero.v1.pay.AcquirerServiceGrpc;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallDeadlineTest {

    private final List<CallOptions> seen = new ArrayList<>();

    /** Records what the interceptor passed down, which is the whole observable effect. */
    private final Channel channel = new Channel() {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
            seen.add(callOptions);
            return null;
        }

        @Override
        public String authority() {
            return "test";
        }
    };

    private void call(CallDeadline interceptor, CallOptions options) {
        interceptor.interceptCall(AcquirerServiceGrpc.getCreatePaymentIntentMethod(), options, channel);
    }

    @Test
    void appliesTheDefaultWhenTheCallCarriesNone() {
        call(new CallDeadline(Duration.ofSeconds(10)), CallOptions.DEFAULT);

        Deadline applied = seen.get(0).getDeadline();
        assertNotNull(applied, "a call with no deadline must come out with the default");
        long remainingMs = applied.timeRemaining(TimeUnit.MILLISECONDS);
        assertTrue(remainingMs > 8_000 && remainingMs <= 10_000,
                "expected ~10s remaining, got " + remainingMs + "ms");
        assertNull(CallOptions.DEFAULT.getDeadline(), "must not mutate the options it was given");
    }

    /**
     * The reason this is an interceptor and not {@code stub.withDeadlineAfter(...)}:
     * a {@link Deadline} is absolute, so a stub built once with a 10s deadline stops
     * working 10s later. Each call must get a deadline computed at that moment.
     */
    @Test
    void recomputesTheDeadlineForEveryCall() throws InterruptedException {
        CallDeadline interceptor = new CallDeadline(Duration.ofSeconds(10));

        call(interceptor, CallOptions.DEFAULT);
        Thread.sleep(50);
        call(interceptor, CallOptions.DEFAULT);

        Deadline first = seen.get(0).getDeadline();
        Deadline second = seen.get(1).getDeadline();
        assertTrue(first.isBefore(second),
                "the second call's deadline must be later than the first's — "
                        + "a shared absolute deadline is the bug this guards against");
        assertTrue(second.timeRemaining(TimeUnit.MILLISECONDS) > 8_000,
                "the second call must still get a full budget, not the leftovers of the first");
    }

    @Test
    void leavesAnExplicitDeadlineAlone() {
        CallOptions explicit = CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS);

        call(new CallDeadline(Duration.ofSeconds(10)), explicit);

        assertSame(explicit.getDeadline(), seen.get(0).getDeadline(),
                "a per-call deadline set at the call site must win over the default");
    }
}
