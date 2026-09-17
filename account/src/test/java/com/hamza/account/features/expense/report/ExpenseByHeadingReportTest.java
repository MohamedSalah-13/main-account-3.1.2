package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.hamza.account.features.expense.report.ReportFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseByHeadingReportTest {

    private static ExpenseByHeadingReport september(List<ExpenseReportRows.HeadingTotal> previous) {
        return ExpenseByHeadingReport.build(SEPTEMBER, HEADINGS,
                List.of(total(10, 1, "100"), total(11, 3, "600"), total(12, 2, "300"), total(20, 1, "2000")),
                previous);
    }

    @Test
    @DisplayName("a main heading is its own expenses plus its children's, and its direct line says the difference")
    void mainHeadingRollsUp() {
        ExpenseByHeadingReport report = september(null);

        List<ExpenseByHeadingReport.Line> lines = report.lines();
        assertEquals(List.of("إيجار", "إدارية", "كهرباء", "مياه", "إدارية"),
                lines.stream().map(ExpenseByHeadingReport.Line::name).toList(),
                "mains by total, children by total, the direct line last under its main");
        ExpenseByHeadingReport.Line admin = lines.get(1);
        assertEquals(ExpenseHeadingLineKind.MAIN, admin.kind());
        assertEquals(0, new BigDecimal("1000").compareTo(admin.total()));
        assertEquals(6, admin.count());
        assertEquals(ExpenseHeadingLineKind.DIRECT, lines.get(4).kind());
        assertEquals(0, new BigDecimal("100").compareTo(lines.get(4).total()));
    }

    @Test
    @DisplayName("the total adds up the main lines only - never a child a second time")
    void totalCountsEachExpenseOnce() {
        ExpenseByHeadingReport report = september(null);

        assertEquals(0, new BigDecimal("3000").compareTo(report.total()));
        assertEquals(7, report.count());
        assertEquals(0, new BigDecimal("66.7").compareTo(report.lines().get(0).sharePercent()));
    }

    @Test
    @DisplayName("a heading with nothing now and nothing before is left out; a heading that fell to nothing stays")
    void emptyHeadingsAreLeftOutButAFallIsNot() {
        ExpenseByHeadingReport report = september(List.of(total(30, 1, "50")));

        assertTrue(report.lines().stream().anyMatch(line -> line.headingId() == 30),
                "the stopped heading had something last period");
        ExpenseByHeadingReport.Line old = report.lines().stream().filter(line -> line.headingId() == 30).findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("-100.0").compareTo(old.changePercent()));

        ExpenseByHeadingReport withoutHistory = september(null);
        assertFalse(withoutHistory.lines().stream().anyMatch(line -> line.headingId() == 30));
    }

    @Test
    @DisplayName("a main heading with no children on the report has no direct line to repeat it")
    void noDirectLineWithoutChildren() {
        ExpenseByHeadingReport report = ExpenseByHeadingReport.build(SEPTEMBER, HEADINGS,
                List.of(total(10, 1, "100"), total(20, 1, "50")), null);

        assertTrue(report.lines().stream().noneMatch(line -> line.kind() == ExpenseHeadingLineKind.DIRECT));
    }

    @Test
    @DisplayName("without a comparison the change is absent, not zero; a rise from nothing is not a percentage")
    void changeIsAbsentWithoutSomethingToDivideBy() {
        assertFalse(september(null).compared());
        assertNull(september(null).lines().get(0).changePercent());
        assertTrue(september(null).change().isEmpty());

        ExpenseByHeadingReport compared = september(List.of(total(20, 1, "1000")));
        assertEquals(0, new BigDecimal("100.0").compareTo(compared.lines().get(0).changePercent()));
        ExpenseByHeadingReport.Line electricity = compared.lines().stream()
                .filter(line -> line.headingId() == 11).findFirst().orElseThrow();
        assertNull(electricity.changePercent(), "nothing last period to divide by");
    }

    @Test
    @DisplayName("an amount under a heading the list does not name still counts, under its number")
    void unknownHeadingIsKept() {
        ExpenseByHeadingReport report = ExpenseByHeadingReport.build(SEPTEMBER, HEADINGS,
                List.of(total(99, 1, "5")), null);

        assertEquals("#99", report.lines().get(0).name());
        assertEquals(0, new BigDecimal("5").compareTo(report.total()));
    }

    @Test
    @DisplayName("a line opens the list on its heading within the scope; a direct line opens nothing")
    void listFilterPerLine() {
        ExpenseByHeadingReport report = september(null);

        ExpenseFilter admin = report.listFilter(report.lines().get(1)).orElseThrow();
        assertEquals(10, admin.headingId());
        assertEquals(SEPTEMBER.from(), admin.from());
        assertEquals(SEPTEMBER.to(), admin.to());
        assertTrue(report.listFilter(report.lines().get(4)).isEmpty());
    }
}
