package com.hamza.account.features.profitloss.yearly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YearlyReportPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @Test
    @DisplayName("a past year is its twelve months against the whole of the year before")
    void aPastYearIsWhole() {
        YearlyReportPeriod period = YearlyReportPeriod.of(2025, TODAY);

        assertEquals(12, period.lastMonth());
        assertEquals(LocalDate.of(2025, 1, 1), period.from());
        assertEquals(LocalDate.of(2025, 12, 31), period.to());
        assertEquals(LocalDate.of(2024, 1, 1), period.previousFrom());
        assertEquals(LocalDate.of(2024, 12, 31), period.previousTo());
        assertFalse(period.toDate());
        assertEquals(12, period.months().size());
    }

    @Test
    @DisplayName("the running year stops at this month, and the year before at the same day")
    void theRunningYearIsComparedToTheSameDay() {
        YearlyReportPeriod period = YearlyReportPeriod.of(2026, TODAY);

        assertEquals(9, period.lastMonth());
        assertEquals(LocalDate.of(2026, 9, 30), period.to(),
                "the running month is read whole - the breakdown knows only whole months");
        assertEquals(LocalDate.of(2025, 9, 23), period.previousTo());
        assertTrue(period.toDate());
        assertEquals(YearMonth.of(2026, 1), period.months().get(0));
        assertEquals(YearMonth.of(2026, 9), period.months().get(8));
    }

    @Test
    @DisplayName("on the last day of the year the comparison is whole again")
    void theLastDayOfTheYearIsWhole() {
        YearlyReportPeriod period = YearlyReportPeriod.of(2026, LocalDate.of(2026, 12, 31));

        assertEquals(12, period.lastMonth());
        assertFalse(period.toDate());
    }

    @Test
    @DisplayName("the 29th of February compares with the 28th a year back")
    void aLeapDay() {
        YearlyReportPeriod period = YearlyReportPeriod.of(2028, LocalDate.of(2028, 2, 29));

        assertEquals(LocalDate.of(2027, 2, 28), period.previousTo());
        assertEquals(LocalDate.of(2028, 2, 29), period.to());
    }

    @Test
    @DisplayName("a year not reached yet is shown whole rather than cut at a day it has not had")
    void aFutureYearIsWhole() {
        assertEquals(12, YearlyReportPeriod.of(2027, TODAY).lastMonth());
    }

    @Test
    void aMonthOutOfRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new YearlyReportPeriod(2026, 13,
                TODAY, TODAY, TODAY, TODAY));
    }
}
