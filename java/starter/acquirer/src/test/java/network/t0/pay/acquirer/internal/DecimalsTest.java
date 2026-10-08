package network.t0.pay.acquirer.internal;

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
    void readsAStringDigitForDigit() {
        Decimal negative = Decimals.of("-0.5");
        assertEquals(-5L, negative.getUnscaled());
        assertEquals(-1, negative.getExponent());

        // Trailing zeros are digits like any other: 1.50 is 150 × 10^-2.
        Decimal trailing = Decimals.of("1.50");
        assertEquals(150L, trailing.getUnscaled());
        assertEquals(-2, trailing.getExponent());

        assertEquals(-8, Decimals.of("0.00000001").getExponent());
    }

    @Test
    void refusesAStringThatIsNotAPlainDecimal() {
        // Exponent notation, a sign other than "-", and anything but ASCII digits.
        for (String value : new String[] {"1e3", "1E+9", "+5", "", ".5", "5.", "1,5", " 5", "٥"}) {
            var e = assertThrows(IllegalArgumentException.class, () -> Decimals.of(value), value);
            assertEquals("'" + value + "' is not a plain decimal number", e.getMessage());
        }
    }

    @Test
    void refusesAStringWithMoreThanEightFractionDigits() {
        // Exactly 1, but written with nine fraction digits — and a string is taken as
        // written. Strip the zeros yourself, or go through of(BigDecimal).
        var e = assertThrows(IllegalArgumentException.class, () -> Decimals.of("1.000000000"));
        assertEquals("1.000000000 needs exponent -9, outside the contract's [-8, 8] — round it first",
                e.getMessage());
    }

    @Test
    void refusesAStringTooLargeForTheUnscaledLong() {
        var e = assertThrows(IllegalArgumentException.class, () -> Decimals.of("92233720368547758.08"));
        assertEquals("92233720368547758.08 does not fit the contract's 64-bit unscaled value", e.getMessage());

        // The largest unscaled value still goes through.
        assertEquals(Long.MAX_VALUE, Decimals.of("92233720368547758.07").getUnscaled());
    }

    @Test
    void keepsExactValuesWhoseTrailingZerosOverflowTheExponentRange() {
        // 1.000000000 has scale 9 — out of range — but is exactly 1.
        assertEquals(BigDecimal.ONE, Decimals.toBigDecimal(Decimals.of(new BigDecimal("1.000000000"))));
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
