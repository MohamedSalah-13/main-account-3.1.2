package com.hamza.controlsfx.util;

/**
 * A utility class for performing common number operations.
 */
public class NumberUtils {

    /**
     * Rounds a given number to two decimal places.
     *
     * @param number the number to be rounded
     * @return the rounded number to two decimal places
     */
    public static double roundToTwoDecimalPlaces(double number) {
        int roundedValue = (int) Math.round(number * 100);
        return roundedValue / 100.0;
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
        double rateResult = (baseValue * rate) / 100;
        return roundToTwoDecimalPlaces(rateResult);
    }

}
