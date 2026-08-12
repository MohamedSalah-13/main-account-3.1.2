package com.hamza.controlsfx.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A utility class for performing common number operations.
 */
public class NumberUtils {

    public static final int MONEY_SCALE = 2;

    private NumberUtils() {
    }

    /**
     * Rounds a given number to two decimal places.
     *
     * @param number the number to be rounded
     * @return the rounded number to two decimal places
     */
    public static double roundToTwoDecimalPlaces(double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Money value must be finite");
        }
        return roundMoney(BigDecimal.valueOf(number)).doubleValue();
    }

    /**
     * Rounds a monetary value at the domain boundary. Keeping the BigDecimal
     * overload lets new accounting code remain decimal all the way to JDBC,
     * while the double overload above preserves the existing UI/model API.
     */
    public static BigDecimal roundMoney(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Money value cannot be null");
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the rate of a given base value and rate.
     *
     * @param baseValue The initial value to which the rate is applied.
     * @param rate The percentage rate to be calculated.
     *
     * @return The calculated rate rounded to two decimal places.
     */
    public static double calculateRate(double baseValue, double rate) {
        if (!Double.isFinite(baseValue) || !Double.isFinite(rate)) {
            throw new IllegalArgumentException("Rate inputs must be finite");
        }
        BigDecimal result = BigDecimal.valueOf(baseValue)
                .multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(100));
        return roundMoney(result).doubleValue();
    }

}
