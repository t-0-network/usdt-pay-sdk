package network.t0.pay.acquirer.internal;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import network.t0.pay.proto.tzero.v1.pay.acquirer.AcquirerServiceGrpc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureLoggingTest {

    /** A transport failure retains its local cause; a remote status does not carry that stack. */
    private AcquirerServiceGrpc.AcquirerServiceBlockingStub t0(Status status) {
        return AcquirerServiceGrpc.newBlockingStub(new Channel() {
            @Override
            public String authority() {
                return "fake-t0";
            }

            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions options) {
                return new ClientCall<>() {
                    @Override
                    public void start(Listener<RespT> listener, Metadata headers) {
                        listener.onClose(status, new Metadata());
                    }

                    @Override
                    public void request(int count) {
                    }

                    @Override
                    public void cancel(String message, Throwable cause) {
                    }

                    @Override
                    public void halfClose() {
                    }

                    @Override
                    public void sendMessage(ReqT message) {
                    }
                };
            }
        });
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"UNAVAILABLE", "INVALID_ARGUMENT"})
    void failuresLogOneMessagePerCallToStderr_withoutTheCauseStack(Status.Code code) {
        var status = Status.fromCode(code)
                .withDescription("t-0 refused the connection")
                .withCause(new ConnectException("transport cause"));
        var t0 = t0(status);
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var stdout = (ConsoleAppender<ILoggingEvent>) root.getAppender("CONSOLE");
        var stderr = (ConsoleAppender<ILoggingEvent>) root.getAppender("STDERR");
        assertNotNull(stdout);
        assertNotNull(stderr);
        assertEquals("System.err", stderr.getTarget());
        var originalOut = stdout.getOutputStream();
        var originalErr = stderr.getOutputStream();
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        stdout.setOutputStream(out);
        stderr.setOutputStream(err);
        try {
            List<Outcome<?>> outcomes = List.of(
                    GetPaymentQuote.fetch(t0, "COP", Decimals.of("100000")),
                    CreatePaymentIntent.create(t0, "sale", "key", "COP", Decimals.of("100000"), 77),
                    SettlementReceived.confirm(t0, 1, "bank-ref", "COP", Decimals.of("100000"), Instant.now()));

            for (var outcome : outcomes) {
                assertEquals(code == Status.Code.UNAVAILABLE, outcome.shouldRetry());
            }
            assertEquals("", out.toString(StandardCharsets.UTF_8));
            var lines = err.toString(StandardCharsets.UTF_8).lines().toList();
            assertEquals(3, lines.size());
            var expected = List.of(
                    "GetPaymentQuote failed: ",
                    "CreatePaymentIntent failed for sale sale: ",
                    "SettlementReceived failed for bank-ref: ");
            for (int i = 0; i < expected.size(); i++) {
                assertTrue(lines.get(i).endsWith(expected.get(i) + code + ": t-0 refused the connection"));
            }
        } finally {
            stdout.setOutputStream(originalOut);
            stderr.setOutputStream(originalErr);
        }
    }
}
