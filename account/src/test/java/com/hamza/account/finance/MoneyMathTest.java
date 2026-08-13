package com.hamza.account.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyMathTest {

    @Test
    void addsDecimalMoneyWithoutBinaryFloatingPointDrift() {
        BigDecimal result = MoneyMath.add(
                MoneyMath.decimal(0.1), MoneyMath.decimal(0.2));

        assertEquals(new BigDecimal("0.30"), result);
    }

    @Test
    void appliesHalfUpOnlyAtTheMoneyBoundary() {
        assertEquals(new BigDecimal("10.01"),
                MoneyMath.money(new BigDecimal("10.005")));
        assertEquals(new BigDecimal("-10.01"),
                MoneyMath.money(new BigDecimal("-10.005")));
    }

    @Test
    void multipliesPriceAndFractionalQuantityAsDecimals() {
        assertEquals(new BigDecimal("0.03"), MoneyMath.multiply(0.1, 0.3));
    }

    @Test
    void sumsLegacyValuesAsDecimalsBeforeRounding() {
        assertEquals(new BigDecimal("0.60"),
                MoneyMath.sum(DoubleStream.of(0.1, 0.2, 0.3)));
    }

    @Test
    void rejectsNonFiniteLegacyValues() {
        assertThrows(IllegalArgumentException.class, () -> MoneyMath.decimal(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MoneyMath.decimal(Double.POSITIVE_INFINITY));
    }
}
