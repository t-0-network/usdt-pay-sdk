package network.t0.pay.issuer.internal;

import network.t0.pay.proto.tzero.v1.common.Decimal;

import java.math.BigDecimal;

/**
 * Decimal is {@code unscaled * 10^exponent} — 123.45 is unscaled=12345, exponent=-2.
 * Money never travels as a double in this protocol; keep it that way on your side.
 */
public final class Decimals {

    public static Decimal of(BigDecimal value) {
        return Decimal.newBuilder()
                .setUnscaled(value.unscaledValue().longValueExact())
                .setExponent(-value.scale())
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
