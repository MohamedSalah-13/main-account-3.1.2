package com.hamza.account.features.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TierFillRuleTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("wholesale is retail less 5%, to the nearest quarter")
    void percentOffAnotherTier() {
        TierFillRule rule = TierFillRule.fromTier(1, d("-5"), d("0.25"));
        assertEquals(d("9.50"), rule.apply(d("10.00")));
        // 12.34 x 0.95 = 11.723, and the nearest quarter is 11.75
        assertEquals(d("11.75"), rule.apply(d("12.34")));
    }

    @Test
    @DisplayName("cost plus 12%, to the nearest five piastres")
    void percentOnTheCost() {
        TierFillRule rule = TierFillRule.fromCost(d("12"), d("0.05"));
        assertEquals(d("89.60"), rule.apply(d("80")));
        assertEquals(d("11.20"), rule.apply(d("10")));
    }

    @Test
    @DisplayName("half a step rounds up, as money does everywhere here")
    void halfUp() {
        TierFillRule rule = TierFillRule.fromTier(1, BigDecimal.ZERO, d("0.25"));
        assertEquals(d("1.25"), rule.apply(d("1.125")));
        assertEquals(d("5.00"), TierFillRule.fromTier(1, BigDecimal.ZERO, d("5")).apply(d("2.50")));
    }

    @Test
    @DisplayName("nothing to work from fills nothing - never a zero price")
    void noSourceNoPrice() {
        TierFillRule rule = TierFillRule.fromCost(d("10"), d("1"));
        assertNull(rule.apply(BigDecimal.ZERO));
        assertNull(rule.apply(null));
        assertNull(TierFillRule.fromTier(1, d("-99"), d("1")).apply(d("0.40")),
                "a result that rounds to zero is not a price");
    }

    @Test
    void refusesWhatTheDatabaseWouldRefuse() {
        assertThrows(IllegalArgumentException.class, () -> TierFillRule.fromCost(d("-100"), d("1")));
        assertThrows(IllegalArgumentException.class, () -> TierFillRule.fromCost(d("5"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TierFillRule.fromTier(4, d("5"), d("1")));
        assertThrows(IllegalArgumentException.class,
                () -> new TierFillRule(TierFillRule.Source.COST, 2, d("5"), d("1")));
    }
}
