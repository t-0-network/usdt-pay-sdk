package network.t0.pay.issuer.internal;

import network.t0.pay.proto.tzero.v1.pay.Decimal;

import java.math.BigDecimal;

/**
 * Decimal is {@code unscaled * 10^exponent} — 123.45 is unscaled=12345, exponent=-2.
 * Money never travels as a double in this protocol; keep it that way on your side.
 */
public final class Decimals {

    /** The contract constrains exponent to this range; anything else is rejected on the wire. */
    private static final int MIN_EXPONENT = -8;
    private static final int MAX_EXPONENT = 8;

    /**
     * @throws IllegalArgumentException if the value cannot be represented within the
     *         contract's exponent range — division results carry a scale well past it,
     *         so round to the precision you mean before calling this
     */
    public static Decimal of(BigDecimal value) {
        // A quotient like 1.000000000 is exactly representable; only its trailing
        // zeros push the scale out of range.
        BigDecimal scaled = value.scale() > MAX_EXPONENT ? value.stripTrailingZeros() : value;
        int exponent = -scaled.scale();

        // The mirror case: a round magnitude carried as a negative scale — 1E+9, or
        // anything the caller already stripped — is representable once the zeros go
        // back in. 1E+9 is unscaled=10, exponent=8, not exponent=9.
        if (exponent > MAX_EXPONENT) {
            scaled = scaled.setScale(-MAX_EXPONENT);
            exponent = -scaled.scale();
        }

        if (exponent < MIN_EXPONENT || exponent > MAX_EXPONENT) {
            throw new IllegalArgumentException(
                    "%s needs exponent %d, outside the contract's [%d, %d] — round it first"
                            .formatted(value.toPlainString(), exponent, MIN_EXPONENT, MAX_EXPONENT));
        }

        long unscaled;
        try {
            unscaled = scaled.unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            // Reported the same way as an out-of-range exponent: the caller gave a
            // number this contract cannot carry, and the javadoc promises one type.
            throw new IllegalArgumentException(
                    "%s does not fit the contract's 64-bit unscaled value"
                            .formatted(value.toPlainString()));
        }

        return Decimal.newBuilder()
                .setUnscaled(unscaled)
                .setExponent(exponent)
                .build();
    }

    public static Decimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static BigDecimal toBigDecimal(Decimal value) {
        return BigDecimal.valueOf(value.getUnscaled(), -value.getExponent());
    }

    public static String format(Decimal value) {
        return toBigDecimal(value).toPlainString();
    }

    private Decimals() {
    }
}
