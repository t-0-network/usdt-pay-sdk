package network.t0.pay.lp.internal;

import network.t0.pay.proto.tzero.v1.pay.Decimal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecimalsTest {

    @Test
    void roundTripsThroughTheWireForm() {
        Decimal wire = Decimals.of("100000.00");

        assertEquals(10_000_000L, wire.getUnscaled());
        assertEquals(-2, wire.getExponent());
        assertEquals("100000.00", Decimals.format(wire));
    }

    @Test
    void keepsExactValuesWhoseTrailingZerosOverflowTheExponentRange() {
        // 1.000000000 has scale 9 — out of range — but is exactly 1.
        assertEquals(BigDecimal.ONE, Decimals.toBigDecimal(Decimals.of("1.000000000")));
    }

    @Test
    void keepsRoundMagnitudesCarriedAsANegativeScale() {
        // 1E+9 has scale -9, so it looks like exponent 9 — out of range — but it is
        // exactly representable as unscaled=10, exponent=8.
        Decimal wire = Decimals.of(new BigDecimal("1E+9"));

        assertEquals(10L, wire.getUnscaled());
        assertEquals(8, wire.getExponent());
        assertEquals("1000000000", Decimals.format(wire));

        // The realistic route in: the caller stripped the zeros themselves. Compared
        // by value, not by BigDecimal.equals, which also compares scale.
        assertEquals("1000000000",
                Decimals.format(Decimals.of(new BigDecimal("1000000000").stripTrailingZeros())));
    }

    @Test
    void refusesMagnitudesTooLargeForTheUnscaledLong() {
        // Putting the zeros back would overflow the wire's int64 unscaled value.
        // Reported as IllegalArgumentException, as the javadoc promises.
        assertThrows(IllegalArgumentException.class, () -> Decimals.of(new BigDecimal("1E+30")));
    }

    @Test
    void refusesValuesTheContractCannotCarry() {
        // A quotient carries far more precision than the wire allows; the caller has
        // to decide how to round rather than have it silently truncated.
        BigDecimal quotient = new BigDecimal("100000.00").divide(new BigDecimal("4100.00"), 20, RoundingMode.HALF_UP);

        assertThrows(IllegalArgumentException.class, () -> Decimals.of(quotient));
        // Rounded to the amount's own precision it goes through.
        assertEquals("24.39", Decimals.format(Decimals.of(quotient.setScale(2, RoundingMode.HALF_UP))));
    }
}
