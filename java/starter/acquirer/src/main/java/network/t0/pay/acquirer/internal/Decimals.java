package network.t0.pay.acquirer.internal;

import network.t0.pay.proto.tzero.v1.pay.Decimal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Decimal is {@code unscaled * 10^exponent} — 123.45 is unscaled=12345, exponent=-2.
 * Money never travels as a double in this protocol; keep it that way on your side.
 */
public final class Decimals {

    /** The contract constrains exponent to this range; anything else is rejected on the wire. */
    private static final int MIN_EXPONENT = -8;
    private static final int MAX_EXPONENT = 8;

    /** ASCII digits, an optional minus and fraction — no exponent notation, no "+". */
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

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

        return build(value.toPlainString(), scaled.unscaledValue(), exponent);
    }

    /**
     * @param value a plain decimal string — {@code "100000.00"}, {@code "-0.5"}, {@code "7"} —
     *              taken digit for digit: {@code "1.000000000"} has nine fraction digits and
     *              is refused, trailing zeros or not
     * @throws IllegalArgumentException if the value is not a plain decimal, or carries more
     *         precision, or more magnitude, than the contract can hold. Round to the
     *         precision you mean before calling this; truncating money silently is not
     *         this function's decision to make.
     */
    public static Decimal of(String value) {
        // Checked before BigDecimal sees it, which would also take "1e3" and "+5".
        if (!PLAIN_DECIMAL.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not a plain decimal number".formatted(value));
        }
        // Its scale is the number of fraction digits as written.
        BigDecimal parsed = new BigDecimal(value);
        return build(value, parsed.unscaledValue(), -parsed.scale());
    }

    /** @param shown the value as the messages name it */
    private static Decimal build(String shown, BigInteger unscaled, int exponent) {
        if (exponent < MIN_EXPONENT || exponent > MAX_EXPONENT) {
            throw new IllegalArgumentException(
                    "%s needs exponent %d, outside the contract's [%d, %d] — round it first"
                            .formatted(shown, exponent, MIN_EXPONENT, MAX_EXPONENT));
        }

        long unscaledLong;
        try {
            unscaledLong = unscaled.longValueExact();
        } catch (ArithmeticException e) {
            // Reported the same way as an out-of-range exponent: the caller gave a
            // number this contract cannot carry, and the javadoc promises one type.
            throw new IllegalArgumentException(
                    "%s does not fit the contract's 64-bit unscaled value".formatted(shown));
        }

        return Decimal.newBuilder()
                .setUnscaled(unscaledLong)
                .setExponent(exponent)
                .build();
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
