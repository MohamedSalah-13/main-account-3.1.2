package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.hamza.account.features.expense.report.ReportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseSalesRatioTest {

    private static final ExpenseFilter SCOPE = ExpenseFilter.between(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 9, 30));

    @Test
    @DisplayName("month by month, the first month starting where the scope does")
    void monthByMonth() {
        ExpenseSalesRatio ratio = ExpenseSalesRatio.build(SCOPE,
                List.of(day("2026-07-12", "100"), day("2026-08-01", "300")),
                List.of(day("2026-07-11", "1000"), day("2026-08-15", "2000"), day("2026-09-02", "-50")));

        assertEquals(3, ratio.lines().size());
        assertEquals(LocalDate.of(2026, 7, 10), ratio.lines().get(0).start());
        assertEquals(0, new BigDecimal("10.0").compareTo(ratio.lines().get(0).ratio().orElseThrow()));
        assertEquals(0, new BigDecimal("15.0").compareTo(ratio.lines().get(1).ratio().orElseThrow()));
        assertEquals(0, new BigDecimal("400").compareTo(ratio.expenses()));
        assertEquals(0, new BigDecimal("2950").compareTo(ratio.netSales()));
    }

    @Test
    @DisplayName("a month that sold nothing, or whose returns outweigh its sales, has no ratio")
    void ratioAbsentWithoutPositiveSales() {
        ExpenseSalesRatio ratio = ExpenseSalesRatio.build(SCOPE,
                List.of(day("2026-08-01", "300"), day("2026-09-01", "10")), List.of(day("2026-09-02", "-50")));

        assertTrue(ratio.lines().get(1).ratio().isEmpty());
        assertTrue(ratio.lines().get(2).ratio().isEmpty());
        assertTrue(ratio.ratio().isEmpty());
    }

    @Test
    @DisplayName("the ratio needs a whole period of at most ten years")
    void periodRules() {
        assertEquals(ExpenseTrend.Problem.NO_PERIOD, ExpenseSalesRatio.problem(null, LocalDate.of(2026, 1, 1)));
        assertEquals(ExpenseTrend.Problem.TOO_MANY_PERIODS,
                ExpenseSalesRatio.problem(LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ExpenseSalesRatio.build(ExpenseFilter.between(null, null), List.of(), List.of()));
    }

    @Test
    @DisplayName("a month opens the list on that month's days inside the scope")
    void listFilter() {
        ExpenseSalesRatio ratio = ExpenseSalesRatio.build(SCOPE, List.of(), List.of());

        ExpenseFilter july = ratio.listFilter(ratio.lines().get(0));
        assertEquals(LocalDate.of(2026, 7, 10), july.from());
        assertEquals(LocalDate.of(2026, 7, 31), july.to());
    }
}
