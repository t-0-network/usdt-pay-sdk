package network.t0.pay.acquirer.internal;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeTest {

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {
            "INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED", "UNIMPLEMENTED", "FAILED_PRECONDITION"})
    void codesThatRefuseTheRequestAreRejected(Status.Code code) {
        Outcome<String> outcome = Outcome.fromError(
                Status.fromCode(code).withDescription("refused").asRuntimeException());

        assertEquals(new Outcome.Rejected<>(code.name() + ": refused"), outcome);
        assertFalse(outcome.shouldRetry());
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "OK", "INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED", "UNIMPLEMENTED", "FAILED_PRECONDITION"})
    void everyOtherCodeIsUnknown(Status.Code code) {
        Outcome<String> outcome = Outcome.fromError(Status.fromCode(code).asRuntimeException());

        assertInstanceOf(Outcome.Unknown.class, outcome);
        assertTrue(outcome.shouldRetry());
    }

    @Test
    void aTransportFailureIsUnknown() {
        // What grpc-java hands back when t-0 cannot be reached at all.
        Outcome<String> outcome = Outcome.fromError(
                Status.UNAVAILABLE.withCause(new ConnectException("Connection refused")).asRuntimeException());

        assertInstanceOf(Outcome.Unknown.class, outcome);
        assertTrue(outcome.shouldRetry());
    }

    @Test
    void aRefusalWithNoDescriptionIsTheCodeAlone() {
        Outcome<String> outcome = Outcome.fromError(Status.PERMISSION_DENIED.asRuntimeException());

        assertEquals(new Outcome.Rejected<>("PERMISSION_DENIED"), outcome);
    }

    @Test
    void onlyAcceptedCarriesThePayload() {
        assertEquals("quote", new Outcome.Accepted<>("quote").accepted().orElseThrow());
        assertTrue(new Outcome.Rejected<String>("QUOTE_UNAVAILABLE").accepted().isEmpty());
        assertTrue(new Outcome.Unknown<String>("no answer").accepted().isEmpty());
    }
}
