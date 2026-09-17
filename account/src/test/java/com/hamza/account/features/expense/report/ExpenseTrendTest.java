package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.party.trend.TrendGranularity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.hamza.account.features.expense.report.ReportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseTrendTest {

    private static final ExpenseFilter FIRST_QUARTER =
            ExpenseFilter.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    @Test
    @DisplayName("every period in the range is a point, the empty ones included")
    void emptyPeriodsAreDrawn() {
        ExpenseTrend trend = ExpenseTrend.build(new ExpenseTrend.Filter(FIRST_QUARTER, TrendGranularity.MONTH, false),
                List.of(day("2026-01-05", "100"), day("2026-03-20", "50")), List.of());

        assertEquals(List.of("2026-01", "2026-02", "2026-03"),
                trend.points().stream().map(ExpenseTrend.Point::label).toList());
        assertEquals(0, BigDecimal.ZERO.compareTo(trend.points().get(1).total()));
        assertEquals(0, new BigDecimal("150").compareTo(trend.total()));
        assertEquals(0, new BigDecimal("50.00").compareTo(trend.averagePerPeriod()), "three periods, not two");
    }

    @Test
    @DisplayName("the year before is the same dates, filed into this year's periods")
    void comparisonUsesTheSameDates() {
        ExpenseTrend trend = ExpenseTrend.build(new ExpenseTrend.Filter(FIRST_QUARTER, TrendGranularity.MONTH, true),
                List.of(day("2026-02-10", "300")), List.of(day("2025-02-10", "200"), day("2025-04-01", "999")));

        assertEquals(0, new BigDecimal("200").compareTo(trend.points().get(1).previousTotal()));
        assertEquals(0, new BigDecimal("200").compareTo(trend.previousTotal()), "April is outside the range");
        assertEquals(0, new BigDecimal("50.0").compareTo(trend.change().orElseThrow()));
    }

    @Test
    @DisplayName("by the year there is no comparison: last year is the point beside this one")
    void noComparisonByTheYear() {
        ExpenseTrend.Filter filter = new ExpenseTrend.Filter(FIRST_QUARTER, TrendGranularity.YEAR, true);

        assertTrue(!filter.compareWithPreviousYear());
    }

    @Test
    @DisplayName("a scope with no end, a reversed one, or too many periods cannot be charted")
    void problems() {
        assertEquals(ExpenseTrend.Problem.NO_PERIOD,
                ExpenseTrend.Filter.problem(TrendGranularity.MONTH, LocalDate.of(2026, 1, 1), null));
        assertEquals(ExpenseTrend.Problem.REVERSED,
                ExpenseTrend.Filter.problem(TrendGranularity.MONTH, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertEquals(ExpenseTrend.Problem.TOO_MANY_PERIODS,
                ExpenseTrend.Filter.problem(TrendGranularity.WEEK, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new ExpenseTrend.Filter(
                ExpenseFilter.between(null, null), TrendGranularity.MONTH, false));
    }

    @Test
    @DisplayName("a point opens the list on its own days, never past the scope")
    void pointFilterStaysInsideTheScope() {
        ExpenseFilter midMonth = ExpenseFilter.between(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 20));
        ExpenseTrend trend = ExpenseTrend.build(new ExpenseTrend.Filter(midMonth, TrendGranularity.MONTH, false),
                List.of(), List.of());

        ExpenseFilter january = trend.listFilter(trend.points().get(0));
        assertEquals(LocalDate.of(2026, 1, 10), january.from());
        assertEquals(LocalDate.of(2026, 1, 31), january.to());
        ExpenseFilter february = trend.listFilter(trend.points().get(1));
        assertEquals(LocalDate.of(2026, 2, 20), february.to());
    }
}
