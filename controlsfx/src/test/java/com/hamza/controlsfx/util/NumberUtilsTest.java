package com.hamza.controlsfx.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberUtilsTest {

    @Test
    void doesNotOverflowAtAmountsAboveTheOldIntegerLimit() {
        assertEquals(30_000_000.13, NumberUtils.roundToTwoDecimalPlaces(30_000_000.129));
        assertEquals(9_999_999_999.99, NumberUtils.roundToTwoDecimalPlaces(9_999_999_999.994));
    }

    @Test
    void usesFinancialHalfUpRoundingForBothSigns() {
        assertEquals(10.01, NumberUtils.roundToTwoDecimalPlaces(10.005));
        assertEquals(-10.01, NumberUtils.roundToTwoDecimalPlaces(-10.005));
    }

    @Test
    void keepsDecimalCallersDecimal() {
        assertEquals(new BigDecimal("123456789012.35"),
                NumberUtils.roundMoney(new BigDecimal("123456789012.345")));
    }

    @Test
    void rejectsValuesThatCannotRepresentMoney() {
        assertThrows(IllegalArgumentException.class,
                () -> NumberUtils.roundToTwoDecimalPlaces(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> NumberUtils.roundToTwoDecimalPlaces(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> NumberUtils.roundMoney(null));
    }

    @Test
    void calculatesRatesWithoutBinaryIntermediateRounding() {
        assertEquals(0.01, NumberUtils.calculateRate(0.10, 5));
        assertEquals(187_500.19, NumberUtils.calculateRate(30_000_030, 0.625));
    }
}
