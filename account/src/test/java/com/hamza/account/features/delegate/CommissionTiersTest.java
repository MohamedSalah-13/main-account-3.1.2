package com.hamza.account.features.delegate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommissionTiersTest {

    private static final BigDecimal TARGET = new BigDecimal("100000");

    /** From 50% at 1%, from 80% at 2%, from 100% at 3%. */
    private static final CommissionTiers THREE = tiers("50", "1", "80", "2", "100", "3");

    private static CommissionTiers tiers(String... fromAndRate) {
        List<CommissionTiers.Tier> list = new java.util.ArrayList<>();
        for (int i = 0; i < fromAndRate.length; i += 2) {
            list.add(new CommissionTiers.Tier(new BigDecimal(fromAndRate[i]), new BigDecimal(fromAndRate[i + 1])));
        }
        return new CommissionTiers(list);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    /**
     * The defect this class replaces. The old view ended its chain with an unconditional
     * third rate, so a delegate at 1% of his target was paid it on everything he sold.
     */
    @Test
    void belowTheLowestThresholdThereIsNoCommission() {
        CommissionTiers.Result result = THREE.calculate(new BigDecimal("1000"), TARGET, TierMode.WHOLE);
        assertEquals(0, result.tier());
        assertEquals(money("0"), result.amount());
        assertEquals(new BigDecimal("1.00"), result.achievementPercent());

        assertEquals(0, THREE.calculate(new BigDecimal("49999.99"), TARGET, TierMode.WHOLE).tier());
    }

    @Test
    void exactlyOnAThresholdReachesItsTier() {
        CommissionTiers.Result lowest = THREE.calculate(new BigDecimal("50000"), TARGET, TierMode.WHOLE);
        assertEquals(1, lowest.tier());
        assertEquals(money("500"), lowest.amount());

        CommissionTiers.Result top = THREE.calculate(TARGET, TARGET, TierMode.WHOLE);
        assertEquals(3, top.tier());
        assertEquals(money("3000"), top.amount());
    }

    /**
     * 99,996 of 100,000 is 100.00% on a screen and is not the target. The threshold is compared
     * as an amount, never as the rounded percentage that is shown.
     */
    @Test
    void anAchievementThatRoundsToTheThresholdHasNotReachedIt() {
        CommissionTiers.Result result = THREE.calculate(new BigDecimal("99996"), TARGET, TierMode.WHOLE);
        assertEquals(new BigDecimal("100.00"), result.achievementPercent());
        assertEquals(2, result.tier());
        assertEquals(money("1999.92"), result.amount());
    }

    @Test
    void wholeModePaysTheReachedRateOnEverything() {
        CommissionTiers.Result result = THREE.calculate(new BigDecimal("90000"), TARGET, TierMode.WHOLE);
        assertEquals(2, result.tier());
        assertEquals(new BigDecimal("2"), result.ratePercent());
        assertEquals(money("1800"), result.amount());
    }

    /** 120,000: nothing on the first 50,000, 1% on 30,000, 2% on 20,000, 3% on the last 20,000. */
    @Test
    void marginalModePaysEachTierOnThePartInsideIt() {
        CommissionTiers.Result result = THREE.calculate(new BigDecimal("120000"), TARGET, TierMode.MARGINAL);
        assertEquals(3, result.tier());
        assertEquals(money("1300"), result.amount());
    }

    /** Crossing a threshold must never make a delegate worse off, and in marginal mode barely better. */
    @Test
    void marginalModeHasNoCliffAtAThreshold() {
        BigDecimal justBelow = THREE.calculate(new BigDecimal("79999"), TARGET, TierMode.MARGINAL).amount();
        BigDecimal justAbove = THREE.calculate(new BigDecimal("80001"), TARGET, TierMode.MARGINAL).amount();
        assertEquals(money("299.99"), justBelow);
        assertEquals(money("300.02"), justAbove);
    }

    /** A month whose returns outweigh its sales is not a commission to collect from the delegate. */
    @Test
    void aNegativeOrEmptyMonthEarnsNothingRatherThanANegativeCommission() {
        for (TierMode mode : TierMode.values()) {
            assertEquals(money("0"), THREE.calculate(new BigDecimal("-5000"), TARGET, mode).amount());
            assertEquals(money("0"), THREE.calculate(BigDecimal.ZERO, TARGET, mode).amount());
            assertEquals(0, THREE.calculate(null, TARGET, mode).tier());
        }
        assertEquals(new BigDecimal("-5.00"),
                THREE.calculate(new BigDecimal("-5000"), TARGET, TierMode.WHOLE).achievementPercent());
    }

    /** No target: a flat rate from the first pound, and no achievement to show. */
    @Test
    void aFlatRateNeedsNoTarget() {
        CommissionTiers flat = tiers("0", "2.5");
        assertTrue(flat.validWithoutTarget());
        for (TierMode mode : TierMode.values()) {
            CommissionTiers.Result result = flat.calculate(new BigDecimal("40000"), BigDecimal.ZERO, mode);
            assertEquals(1, result.tier());
            assertEquals(money("1000"), result.amount());
            assertNull(result.achievementPercent());
        }
    }

    /** Without a target a threshold above zero can never be met; it must not be met by accident. */
    @Test
    void withoutATargetATierAboveZeroIsNeverReached() {
        assertFalse(THREE.validWithoutTarget());
        assertFalse(tiers("10", "2").validWithoutTarget());
        assertEquals(0, THREE.calculate(new BigDecimal("999999"), BigDecimal.ZERO, TierMode.WHOLE).tier());
        assertEquals(0, THREE.calculate(new BigDecimal("999999"), null, TierMode.MARGINAL).tier());
    }

    @Test
    void moneyRoundsHalfUp() {
        // 1.5% of 333.00 is 4.995.
        CommissionTiers flat = tiers("0", "1.5");
        assertEquals(money("5.00"), flat.calculate(new BigDecimal("333"), BigDecimal.ZERO, TierMode.WHOLE).amount());
    }

    @Test
    void theTiersThemselvesAreRefusedWithTheKeyOfTheSentence() {
        assertEquals("commission.error.tier.count",
                assertThrows(IllegalArgumentException.class, () -> new CommissionTiers(List.of())).getMessage());
        assertEquals("commission.error.tier.count", assertThrows(IllegalArgumentException.class,
                () -> tiers("0", "1", "10", "1", "20", "1", "30", "1")).getMessage());
        // Two tiers at one threshold have no order, so which rate is paid would be chance.
        assertEquals("commission.error.tier.order",
                assertThrows(IllegalArgumentException.class, () -> tiers("50", "1", "50", "2")).getMessage());
        assertEquals("commission.error.tier.order",
                assertThrows(IllegalArgumentException.class, () -> tiers("80", "2", "50", "1")).getMessage());
        assertEquals("commission.error.tier.rate",
                assertThrows(IllegalArgumentException.class, () -> tiers("0", "101")).getMessage());
        assertEquals("commission.error.tier.rate",
                assertThrows(IllegalArgumentException.class, () -> tiers("0", "-1")).getMessage());
        assertEquals("commission.error.tier.threshold",
                assertThrows(IllegalArgumentException.class, () -> tiers("-1", "1")).getMessage());
    }

    /** A lower rate in a higher tier is unusual and is the shop's to decide - it is not refused. */
    @Test
    void ratesNeedNotRise() {
        assertEquals(money("1000"), tiers("50", "2", "100", "1")
                .calculate(TARGET, TARGET, TierMode.WHOLE).amount());
    }
}
