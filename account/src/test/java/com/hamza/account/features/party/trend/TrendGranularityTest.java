package com.hamza.account.features.party.trend;

import com.hamza.account.features.party.statement.StatementPeriod;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendGranularityTest {

    /** A Friday - the last day of a Saturday-to-Friday week. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 11);

    @Test
    void theWeekStartsOnSaturday() {
        assertEquals(DayOfWeek.FRIDAY, FRIDAY.getDayOfWeek());
        assertEquals(LocalDate.of(2026, 9, 5), TrendGranularity.WEEK.start(FRIDAY));
        LocalDate saturday = LocalDate.of(2026, 9, 12);
        assertEquals(saturday, TrendGranularity.WEEK.start(saturday));
    }

    /** One definition of a week: the statement's "this week" and the chart's current week agree. */
    @Test
    void theChartAndTheStatementAgreeAboutTheWeek() {
        for (LocalDate day = LocalDate.of(2026, 1, 1); day.getYear() == 2026; day = day.plusDays(1)) {
            assertEquals(StatementPeriod.THIS_WEEK.from(day), TrendGranularity.WEEK.start(day), day::toString);
        }
    }

    @Test
    void aMonthAndAYearStartOnTheirFirstDay() {
        assertEquals(LocalDate.of(2026, 9, 1), TrendGranularity.MONTH.start(FRIDAY));
        assertEquals(LocalDate.of(2026, 1, 1), TrendGranularity.YEAR.start(FRIDAY));
        assertEquals(LocalDate.of(2024, 2, 1), TrendGranularity.MONTH.start(LocalDate.of(2024, 2, 29)));
    }

    @Test
    void theNextPeriodFollowsWithoutAGap() {
        assertEquals(LocalDate.of(2026, 3, 1), TrendGranularity.MONTH.next(LocalDate.of(2026, 2, 1)));
        assertEquals(LocalDate.of(2026, 9, 12), TrendGranularity.WEEK.next(LocalDate.of(2026, 9, 5)));
        assertEquals(LocalDate.of(2027, 1, 1), TrendGranularity.YEAR.next(LocalDate.of(2026, 1, 1)));
    }

    /** The default range is whole periods, the current one included. */
    @Test
    void theDefaultRangeCoversItsPeriodsCounted() {
        assertEquals(LocalDate.of(2025, 10, 1), TrendGranularity.MONTH.defaultFrom(FRIDAY));
        assertEquals(LocalDate.of(2026, 6, 20), TrendGranularity.WEEK.defaultFrom(FRIDAY));
        assertEquals(LocalDate.of(2022, 1, 1), TrendGranularity.YEAR.defaultFrom(FRIDAY));

        assertEquals(12, TrendGranularity.MONTH.periods(TrendGranularity.MONTH.defaultFrom(FRIDAY), FRIDAY));
        assertEquals(12, TrendGranularity.WEEK.periods(TrendGranularity.WEEK.defaultFrom(FRIDAY), FRIDAY));
        assertEquals(5, TrendGranularity.YEAR.periods(TrendGranularity.YEAR.defaultFrom(FRIDAY), FRIDAY));
    }

    @Test
    void aLabelIsDigitsOnly() {
        assertEquals("2026", TrendGranularity.YEAR.label(LocalDate.of(2026, 1, 1)));
        assertEquals("2026-09", TrendGranularity.MONTH.label(LocalDate.of(2026, 9, 1)));
        assertEquals("2026-09-05", TrendGranularity.WEEK.label(LocalDate.of(2026, 9, 5)));
    }

    @Test
    void aYearHasNothingToCompareWithThatTheChartDoesNotShow() {
        assertFalse(TrendGranularity.YEAR.comparesWithPreviousYear());
        assertTrue(TrendGranularity.MONTH.comparesWithPreviousYear());
        assertTrue(TrendGranularity.WEEK.comparesWithPreviousYear());
    }
}
