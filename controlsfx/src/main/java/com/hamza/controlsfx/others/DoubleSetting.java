package com.hamza.controlsfx.others;

public class DoubleSetting {

    /**
     * Parses the given string to a double value. If the string is null, empty,
     * or not a valid double, it returns a default value of 0.0.
     *
     * @param string the string to be parsed to a double
     * @return the parsed double value, or 0.0 if the string is null, empty, or not a valid double
     */
    public static double parseDoubleOrDefault(String string) {
        final double DEFAULT_VALUE = 0.0;

        if (isNullOrEmpty(string)) {
            return DEFAULT_VALUE;
        }

        try {
            return Double.parseDouble(string);
        } catch (NumberFormatException e) {
            return DEFAULT_VALUE;
        }
    }

    /**
     * Checks if a given string is null or empty.
     *
     * @param string the string to be checked
     * @return true if the string is null or empty, false otherwise
     */
    private static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }
}
