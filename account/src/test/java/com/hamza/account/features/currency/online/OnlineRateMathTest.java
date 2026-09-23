package com.hamza.account.features.currency.online;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The reciprocal, to six significant digits; the figures were worked out apart from this code. */
class OnlineRateMathTest {

    private static BigDecimal rate(String quote) {
        return OnlineRateMath.rateFrom(new BigDecimal(quote));
    }

    @Test
    @DisplayName("one pound is 0.020587 dollars, so the dollar is 48.5743 pounds")
    void reciprocal() {
        assertEquals(new BigDecimal("48.5743"), rate("0.020587"));
        assertEquals(new BigDecimal("12.9525"), rate("0.077205"), "the riyal");
        assertEquals(new BigDecimal("158.957"), rate("0.006291"), "the Kuwaiti dinar");
        assertEquals(new BigDecimal("0.328818"), rate("3.0412"), "the yen - below one pound");
    }

    @Test
    @DisplayName("six significant digits, not six places: the rial against a dinar base is not zero")
    void significantDigitsNotPlaces() {
        assertEquals(new BigDecimal("0.0000072993"), rate("137000"), "cut to the column's ten places");
        assertEquals(new BigDecimal("52.6316"), rate("0.019"));
    }

    @Test
    @DisplayName("a whole rate is written without an exponent")
    void noExponent() {
        assertEquals("42000", rate("0.0000238095238").toPlainString());
        assertEquals("42000", rate("0.0000238095238").toString(), "4.2E+4 would reach the screen and the column");
    }

    @Test
    @DisplayName("what the column cannot hold is no rate at all, never a rounded guess")
    void unusable() {
        assertNull(rate("1E12"), "zero at ten places");
        assertNull(rate("0.0000000001"), "eleven digits before the point");
        assertNull(rate("0"));
        assertNull(rate("-0.02"));
        assertNull(OnlineRateMath.rateFrom(null));
    }
}
