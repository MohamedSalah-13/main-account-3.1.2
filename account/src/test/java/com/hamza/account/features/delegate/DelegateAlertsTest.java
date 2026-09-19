package com.hamza.account.features.delegate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A reminder that fires on the wrong day is one people learn to dismiss, so every boundary is here. */
class DelegateAlertsTest {

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private static DelegatePerformanceRow row(int id, boolean active, String netSales, CommissionRule rule) {
        return DelegatePerformanceRow.of(
                new DelegateActivity(id, "D" + id, active, n(netSales), BigDecimal.ZERO, BigDecimal.ZERO),
                Optional.ofNullable(rule));
    }

    /** From 80% of a 100,000 target at 2%: the lowest tier starts at 80,000. */
    private static CommissionRule tiered(int employeeId) {
        return new CommissionRule(1, employeeId, LocalDate.of(2026, 1, 1), CommissionBasis.SALES, TierMode.WHOLE,
                n("100000"), new CommissionTiers(List.of(new CommissionTiers.Tier(n("80"), n("2")))), null);
    }

    private static CommissionRule flat(int employeeId) {
        return new CommissionRule(2, employeeId, LocalDate.of(2026, 1, 1), CommissionBasis.SALES, TierMode.WHOLE,
                BigDecimal.ZERO, new CommissionTiers(List.of(new CommissionTiers.Tier(BigDecimal.ZERO, n("2")))), null);
    }

    // ---- the month awaiting approval -------------------------------------------------------------

    @Test
    void lastMonthIsRemindedAboutWhenThereIsSomethingToApprove() {
        assertEquals(Optional.of(YearMonth.of(2026, 10)),
                DelegateAlerts.monthAwaitingApproval(LocalDate.of(2026, 11, 3), false, 2));
        assertEquals(Optional.of(YearMonth.of(2026, 12)),
                DelegateAlerts.monthAwaitingApproval(LocalDate.of(2027, 1, 1), false, 1), "across a new year");
    }

    /** Every shop that gives no delegate a rule: silent for ever, so upgrading raises nothing. */
    @Test
    void aMonthWithNothingToApproveIsNobodysReminder() {
        assertTrue(DelegateAlerts.monthAwaitingApproval(LocalDate.of(2026, 11, 3), false, 0).isEmpty());
    }

    @Test
    void anApprovedMonthStopsTheReminder() {
        assertTrue(DelegateAlerts.monthAwaitingApproval(LocalDate.of(2026, 11, 3), true, 2).isEmpty());
    }

    // ---- the pace ---------------------------------------------------------------------------------

    /** 40,000 by the 20th of a 30-day month is 60,000 at month end - short of the 80,000 the lowest tier needs. */
    @Test
    void aDelegateWhosePaceEndsBelowHisLowestTierIsLagging() {
        List<DelegateAlerts.Lagging> lagging = DelegateAlerts.lagging(
                List.of(row(5, true, "40000", tiered(5))), LocalDate.of(2026, 11, 20));
        assertEquals(1, lagging.size());
        assertEquals(n("60000.00"), lagging.get(0).projected());
        assertEquals(n("80000.00"), lagging.get(0).needed());
        assertEquals(5, lagging.get(0).employeeId());
    }

    /** 54,000 by the 20th is 81,000 at month end: below the threshold today, on course to pass it. */
    @Test
    void aDelegateOnCourseIsNotLaggingThoughHeHasNotReachedTheTierYet() {
        assertTrue(DelegateAlerts.lagging(List.of(row(5, true, "54000", tiered(5))), LocalDate.of(2026, 11, 20)).isEmpty());
    }

    /** Nineteen days say more about which weekday the month began on than about the delegate. */
    @Test
    void nobodyIsJudgedBeforeTheTwentieth() {
        assertTrue(DelegateAlerts.lagging(List.of(row(5, true, "0", tiered(5))), LocalDate.of(2026, 11, 19)).isEmpty());
        assertEquals(1, DelegateAlerts.lagging(List.of(row(5, true, "0", tiered(5))), LocalDate.of(2026, 11, 20)).size());
    }

    @Test
    void onlyAnActiveDelegateWithATargetCanLag() {
        LocalDate late = LocalDate.of(2026, 11, 25);
        assertTrue(DelegateAlerts.lagging(List.of(row(5, true, "10", flat(5))), late).isEmpty(),
                "a flat rate has nothing to fall short of");
        assertTrue(DelegateAlerts.lagging(List.of(row(5, true, "10", null)), late).isEmpty(), "no rule, no target");
        assertTrue(DelegateAlerts.lagging(List.of(row(5, false, "10", tiered(5))), late).isEmpty(),
                "somebody who has left is not behind on anything");
    }

    /** A month of returns projects to itself; there is no pace to extend, and it is lagging. */
    @Test
    void aNegativeMonthProjectsToItself() {
        assertEquals(n("-500"), DelegateAlerts.projected(n("-500"), LocalDate.of(2026, 11, 20)));
        assertEquals(BigDecimal.ZERO, DelegateAlerts.projected(null, LocalDate.of(2026, 11, 20)));
        assertEquals(1, DelegateAlerts.lagging(List.of(row(5, true, "-500", tiered(5))), LocalDate.of(2026, 11, 20)).size());
    }

    /** The month's own length, not thirty: February's 20th is further along than March's. */
    @Test
    void thePaceUsesTheMonthsOwnLength() {
        assertEquals(n("28000.00"), DelegateAlerts.projected(n("20000"), LocalDate.of(2026, 2, 20)));
        assertEquals(n("31000.00"), DelegateAlerts.projected(n("20000"), LocalDate.of(2026, 3, 20)));
        assertEquals(n("20000.00"), DelegateAlerts.projected(n("20000"), LocalDate.of(2026, 3, 31)), "the last day projects to itself");
    }
}
