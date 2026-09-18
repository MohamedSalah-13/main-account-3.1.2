package com.hamza.account.features.expense.budget;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.report.ExpenseHeadingLineKind;
import com.hamza.account.features.expense.report.ExpenseReportRows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rule that decides whether a family's budget is counted once or twice. */
class ExpenseBudgetReportTest {

    private static final ExpenseHeading ADMIN = new ExpenseHeading(10, "إدارية", null, null, true, null, false);
    private static final ExpenseHeading POWER = new ExpenseHeading(11, "كهرباء", 10, "إدارية", true, null, false);
    private static final ExpenseHeading WATER = new ExpenseHeading(12, "مياه", 10, "إدارية", true, null, false);
    private static final ExpenseHeading RENT = new ExpenseHeading(20, "إيجار", null, null, true, null, false);
    private static final List<ExpenseHeading> HEADINGS = List.of(ADMIN, POWER, WATER, RENT);

    private static final ExpenseFilter SEPTEMBER =
            ExpenseFilter.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    private static ExpenseBudget budget(int heading, String amount) {
        return new ExpenseBudget(heading, heading, "", null, 2026, 9, new BigDecimal(amount), "");
    }

    private static ExpenseReportRows.HeadingTotal spent(int heading, String amount) {
        return new ExpenseReportRows.HeadingTotal(heading, 1, new BigDecimal(amount));
    }

    private static ExpenseBudgetReport.Line line(ExpenseBudgetReport report, int headingId,
                                                 ExpenseHeadingLineKind kind) {
        return report.lines().stream()
                .filter(candidate -> candidate.headingId() == headingId && candidate.kind() == kind)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a main heading with no budget of its own takes the sum of its children's")
    void childrenRollUpIntoAnUnbudgetedMain() {
        ExpenseBudgetReport report = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS,
                List.of(budget(11, "3000"), budget(12, "1000")),
                List.of(spent(11, "2500"), spent(12, "1200"), spent(10, "300")));

        ExpenseBudgetReport.Line admin = line(report, 10, ExpenseHeadingLineKind.MAIN);
        assertEquals(0, new BigDecimal("4000").compareTo(admin.budget()));
        assertEquals(0, new BigDecimal("4000").compareTo(admin.actual()), "its own 300 plus its children's");
        assertTrue(admin.hasBudget());
    }

    @Test
    @DisplayName("a main heading with its own budget does not add its children's a second time")
    void ownBudgetWins() {
        ExpenseBudgetReport report = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS,
                List.of(budget(10, "5000"), budget(11, "3000"), budget(12, "1000")),
                List.of(spent(11, "2500")));

        assertEquals(0, new BigDecimal("5000").compareTo(line(report, 10, ExpenseHeadingLineKind.MAIN).budget()),
                "5000 is the ceiling for the family, not 9000");
        assertEquals(0, new BigDecimal("5000").compareTo(report.budget()));
        // The child still shows its own budget beside its own spend.
        ExpenseBudgetReport.Line power = line(report, 11, ExpenseHeadingLineKind.SUB);
        assertEquals(0, new BigDecimal("3000").compareTo(power.budget()));
        assertEquals(0, new BigDecimal("2500").compareTo(power.actual()));
    }

    @Test
    @DisplayName("the total is the main lines only, so no child is counted twice")
    void totalCountsEachFamilyOnce() {
        ExpenseBudgetReport report = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS,
                List.of(budget(11, "3000"), budget(20, "8000")),
                List.of(spent(11, "2500"), spent(20, "8500")));

        assertEquals(0, new BigDecimal("11000").compareTo(report.budget()));
        assertEquals(0, new BigDecimal("11000").compareTo(report.actual()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.remaining()));
    }

    @Test
    @DisplayName("a budget of a period's month and its year are added, not one chosen")
    void monthlyAndYearlyBudgetsAddUp() {
        ExpenseBudget yearly = new ExpenseBudget(1, 20, "", null, 2026, null, new BigDecimal("12000"), "");
        ExpenseBudgetReport report = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS,
                List.of(yearly, budget(20, "500")), List.of());

        assertEquals(0, new BigDecimal("12500").compareTo(line(report, 20, ExpenseHeadingLineKind.MAIN).budget()),
                "the repository fetches what falls in the period; the report adds what it is given");
    }

    @Test
    @DisplayName("overspending, the share used, and a spend with no budget to divide by")
    void overspentAndPercentages() {
        ExpenseBudgetReport report = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS,
                List.of(budget(20, "1000")), List.of(spent(20, "1500"), spent(11, "400")));

        ExpenseBudgetReport.Line rent = line(report, 20, ExpenseHeadingLineKind.MAIN);
        assertTrue(rent.overspent());
        assertEquals(0, new BigDecimal("-500").compareTo(rent.remaining()));
        assertEquals(0, new BigDecimal("150.0").compareTo(rent.usedPercent().orElseThrow()));

        ExpenseBudgetReport.Line power = line(report, 11, ExpenseHeadingLineKind.SUB);
        assertFalse(power.hasBudget());
        assertFalse(power.overspent(), "nothing budgeted cannot be overspent");
        assertTrue(power.usedPercent().isEmpty(), "no budget is not a budget of zero");
    }

    @Test
    @DisplayName("a heading with neither a budget nor a movement is left out; either one keeps it")
    void emptyHeadingsAreLeftOut() {
        ExpenseBudgetReport quiet = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS, List.of(), List.of());
        assertTrue(quiet.isEmpty());

        ExpenseBudgetReport budgetedOnly = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS,
                List.of(budget(20, "1000")), List.of());
        assertEquals(1, budgetedOnly.lines().size(), "a budget nobody spent against is what this report finds");
        assertEquals(0, BigDecimal.ZERO.compareTo(budgetedOnly.actual()));

        ExpenseBudgetReport spentOnly = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS, List.of(),
                List.of(spent(20, "70")));
        assertEquals(1, spentOnly.lines().size(), "a heading spent on with no budget is kept too");
        assertFalse(spentOnly.lines().get(0).hasBudget());
    }

    @Test
    @DisplayName("a line opens the list on its heading over the report's own period")
    void listFilter() {
        ExpenseBudgetReport report = ExpenseBudgetReport.build(SEPTEMBER, HEADINGS,
                List.of(budget(20, "1000")), List.of());

        ExpenseFilter filter = report.listFilter(report.lines().get(0));
        assertEquals(20, filter.headingId());
        assertEquals(SEPTEMBER.from(), filter.from());
        assertEquals(SEPTEMBER.to(), filter.to());
    }
}
