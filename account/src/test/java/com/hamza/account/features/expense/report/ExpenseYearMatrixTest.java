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

class ExpenseYearMatrixTest {

    private static ExpenseReportRows.HeadingDay on(int heading, String date, String amount) {
        return new ExpenseReportRows.HeadingDay(heading, LocalDate.parse(date), money(amount));
    }

    private static ExpenseYearMatrix matrix() {
        ExpenseFilter narrowed = SEPTEMBER.withTreasury(4);
        return ExpenseYearMatrix.build(narrowed, 2026, HEADINGS, List.of(
                on(11, "2026-01-31", "100"), on(11, "2026-02-01", "40"), on(12, "2026-02-14", "60"),
                on(20, "2026-12-31", "500"), on(20, "2025-12-31", "999")));
    }

    @Test
    @DisplayName("each day is filed into its month, and a day of another year into none")
    void daysAreFiledIntoMonths() {
        ExpenseYearMatrix matrix = matrix();

        ExpenseYearMatrix.Line admin = matrix.lines().stream().filter(line -> line.headingId() == 10).findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(admin.month(1)));
        assertEquals(0, new BigDecimal("100").compareTo(admin.month(2)));
        assertEquals(0, new BigDecimal("200").compareTo(admin.total()));
        assertEquals(0, new BigDecimal("700").compareTo(matrix.totals().total()), "999 was the year before");
        assertEquals(0, new BigDecimal("500").compareTo(matrix.totals().month(12)));
    }

    @Test
    @DisplayName("the year replaces the scope's dates and keeps its other conditions")
    void yearReplacesThePeriodOnly() {
        ExpenseYearMatrix matrix = matrix();

        assertEquals(LocalDate.of(2026, 1, 1), matrix.scope().from());
        assertEquals(LocalDate.of(2026, 12, 31), matrix.scope().to());
        assertEquals(4, matrix.scope().treasuryId());
    }

    @Test
    @DisplayName("a cell opens the list on its heading and month; the totals line on the month alone")
    void cellFilters() {
        ExpenseYearMatrix matrix = matrix();
        ExpenseYearMatrix.Line electricity = matrix.lines().stream().filter(line -> line.headingId() == 11)
                .findFirst().orElseThrow();

        ExpenseFilter february = matrix.listFilter(electricity, 2).orElseThrow();
        assertEquals(11, february.headingId());
        assertEquals(LocalDate.of(2026, 2, 1), february.from());
        assertEquals(LocalDate.of(2026, 2, 28), february.to());

        ExpenseFilter year = matrix.listFilter(matrix.totals(), 0).orElseThrow();
        assertEquals(null, year.headingId());
        assertEquals(LocalDate.of(2026, 1, 1), year.from());
        assertThrows(IllegalArgumentException.class, () -> matrix.listFilter(electricity, 13));
    }

    @Test
    @DisplayName("a direct line opens nothing: the list's heading condition would bring its children too")
    void directLineOpensNothing() {
        ExpenseYearMatrix matrix = ExpenseYearMatrix.build(SEPTEMBER, 2026, HEADINGS,
                List.of(on(10, "2026-03-01", "10"), on(11, "2026-03-02", "20")));
        ExpenseYearMatrix.Line direct = matrix.lines().stream()
                .filter(line -> line.kind() == ExpenseHeadingLineKind.DIRECT).findFirst().orElseThrow();

        assertTrue(matrix.listFilter(direct, 3).isEmpty());
    }
}
