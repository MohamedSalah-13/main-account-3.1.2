package com.hamza.account.finance;

import java.math.BigDecimal;
import java.util.stream.DoubleStream;

import static com.hamza.controlsfx.util.NumberUtils.MONEY_SCALE;
import static com.hamza.controlsfx.util.NumberUtils.roundMoney;

/**
 * Decimal arithmetic boundary for monetary values.
 *
 * <p>Calculations stay as {@link BigDecimal}; conversion to {@code double} is
 * reserved for the legacy JavaFX models and DAO interfaces.</p>
 */
public final class MoneyMath {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private MoneyMath() {
    }

    /** Converts a finite legacy value without rounding it. */
    public static BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Money value must be finite");
        }
        return BigDecimal.valueOf(value);
    }

    /** Parses a form value, matching the former empty/invalid-as-zero behaviour. */
    public static BigDecimal parseOrZero(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return ZERO;
        }
    }

    public static BigDecimal money(double value) {
        return money(decimal(value));
    }

    public static BigDecimal money(BigDecimal value) {
        return roundMoney(value);
    }

    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return money(require(left).add(require(right)));
    }

    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return money(require(left).subtract(require(right)));
    }

    public static BigDecimal multiply(double left, double right) {
        return money(decimal(left).multiply(decimal(right)));
    }

    public static BigDecimal sum(DoubleStream values) {
        if (values == null) {
            throw new IllegalArgumentException("Money stream cannot be null");
        }
        return money(values.mapToObj(MoneyMath::decimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static double asDouble(BigDecimal value) {
        return money(value).doubleValue();
    }

    public static String text(BigDecimal value) {
        return money(value).toPlainString();
    }

    private static BigDecimal require(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Money value cannot be null");
        }
        return value;
    }
}
