package com.hamza.account.features.capital;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquityPeriodTest {

    /** Opened at 10,000, 2,000 paid in, 500 drawn, 900 earned: closed at 12,400, averaged 11,200. */
    @Test
    void theReturnIsTheProfitOverTheMeanOfTheOpeningAndTheClose() {
        EquityPeriod march = period("2000", "500", "900", "12400");
        assertMoney("10000", march.opening());
        assertMoney("11200", march.averageEquity());
        assertMoney("8.04", march.returnOnEquity().orElseThrow());
    }

    /** Opened at 10,000 and lost 500: the mean is 9,750 and the return -5.13%. */
    @Test
    void aLossIsANegativeReturn() {
        assertMoney("-5.13", period("0", "0", "-500", "9500").returnOnEquity().orElseThrow());
    }

    /** A return on nothing, or on a deficit, is not a number - and a zero would read as "earned nothing". */
    @Test
    void thereIsNoReturnOnAnAverageOfZeroOrLess() {
        assertTrue(period("0", "0", "0", "0").returnOnEquity().isEmpty());
        assertTrue(period("0", "0", "200", "-1000").returnOnEquity().isEmpty());
    }

    private static EquityPeriod period(String paidIn, String drawn, String profit, String closing) {
        return new EquityPeriod(LocalDate.of(2026, 3, 1), "2026-03", new BigDecimal(paidIn), new BigDecimal(drawn),
                new BigDecimal(profit), new BigDecimal(closing));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " but was " + actual);
    }
}
