package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.hamza.account.features.expense.report.ReportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseDimensionReportTest {

    private static ExpenseReportRows.DimensionTotal row(String key, String label, int count, String total) {
        return new ExpenseReportRows.DimensionTotal(key, label, count, money(total));
    }

    @Test
    @DisplayName("the largest first, and the line with no value last whatever it came to")
    void orderByTotalWithNoValueLast() {
        ExpenseDimensionReport report = ExpenseDimensionReport.build(SEPTEMBER, ExpenseDimension.PAYEE, List.of(
                row(null, null, 9, "5000"), row("الكهرباء", "الكهرباء", 1, "300"), row("المالك", "المالك", 1, "900")));

        assertEquals(List.of("المالك", "الكهرباء"),
                report.lines().subList(0, 2).stream().map(ExpenseDimensionReport.Line::label).toList());
        assertNull(report.lines().get(2).key());
        assertEquals(11, report.count());
        assertEquals(0, money("80.6").compareTo(report.lines().get(2).sharePercent()));
    }

    @Test
    @DisplayName("months in calendar order, written 2026-09, opening the list on the month inside the scope")
    void months() {
        ExpenseFilter scope = ExpenseFilter.between(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 30));
        ExpenseDimensionReport report = ExpenseDimensionReport.build(scope, ExpenseDimension.MONTH, List.of(
                row("202609", null, 1, "10"), row("202608", null, 1, "900")));

        assertEquals(List.of("2026-08", "2026-09"),
                report.lines().stream().map(ExpenseDimensionReport.Line::label).toList());
        ExpenseFilter august = report.listFilter(report.lines().get(0)).orElseThrow();
        assertEquals(LocalDate.of(2026, 8, 15), august.from(), "never widened past the scope");
        assertEquals(LocalDate.of(2026, 8, 31), august.to());
    }

    @Test
    @DisplayName("a till and a user narrow the list exactly; a shift, a payee and the empty line do not")
    void whatOpensTheList() {
        assertEquals(3, ExpenseDimension.TREASURY.narrow(SEPTEMBER, "3").orElseThrow().treasuryId());
        assertEquals(7, ExpenseDimension.USER.narrow(SEPTEMBER, "7").orElseThrow().userId());
        assertTrue(ExpenseDimension.SHIFT.narrow(SEPTEMBER, "12").isEmpty());
        assertTrue(ExpenseDimension.PAYEE.narrow(SEPTEMBER, "محمد").isEmpty(),
                "the list's text search is a contains match over six columns");
        assertTrue(ExpenseDimension.TREASURY.narrow(SEPTEMBER, null).isEmpty());
    }

    @Test
    @DisplayName("a shift is called by its number when no name was read")
    void labels() {
        assertEquals("12", ExpenseDimension.SHIFT.label("12", null));
        assertEquals("الخزينة الرئيسية", ExpenseDimension.TREASURY.label("1", "الخزينة الرئيسية"));
        assertNull(ExpenseDimension.PAYEE.label(null, null));
    }
}
