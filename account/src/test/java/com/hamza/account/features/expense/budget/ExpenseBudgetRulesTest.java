package com.hamza.account.features.expense.budget;

import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** What a budget may be, asked without a database or a screen. */
class ExpenseBudgetRulesTest {

    private static final ExpenseHeading POWER = new ExpenseHeading(11, "كهرباء", 10, "إدارية", true, null, false);
    private static final ExpenseHeading OLD = new ExpenseHeading(12, "إيجار قديم", null, null, false, null, false);
    private static final ExpenseHeading SALARIES = new ExpenseHeading(1, "مرتبات", null, null, true, null, true);

    private static ExpenseBudgetDraft draft(int year, Integer month, String amount) {
        return new ExpenseBudgetDraft(0, POWER.id(), year, month, new BigDecimal(amount), "");
    }

    private static String refusal(ExpenseBudgetDraft draft, ExpenseHeading heading) {
        return assertThrows(UserValidationException.class,
                () -> ExpenseBudgetRules.require(draft, heading)).getMessage();
    }

    @Test
    @DisplayName("a whole year and a single month are both budgets")
    void bothPeriodsPass() {
        assertDoesNotThrow(() -> ExpenseBudgetRules.require(draft(2026, null, "12000"), POWER));
        assertDoesNotThrow(() -> ExpenseBudgetRules.require(draft(2026, 9, "1000"), POWER));
    }

    @Test
    @DisplayName("a heading employees are paid under takes a budget: the wages bill is what a shop budgets")
    void employeeHeadingsAreBudgetable() {
        // The recurring templates refuse this heading, because a template would record a salary as an
        // ordinary expense. Reading what was spent on it, and deciding what may be, is a different act.
        assertDoesNotThrow(() -> ExpenseBudgetRules.require(
                new ExpenseBudgetDraft(0, SALARIES.id(), 2026, null, new BigDecimal("50000"), ""), SALARIES));
    }

    @Test
    @DisplayName("no heading, and a stopped one, are refused - the second keeps its old budgets")
    void headingMustBeUsable() {
        assertEquals("expense.budget.error.heading", refusal(draft(2026, null, "100"), null));
        assertEquals("expense.budget.error.heading.stopped", refusal(draft(2026, null, "100"), OLD));
    }

    @Test
    @DisplayName("the year is the column's CHECK, said in words first")
    void yearRange() {
        assertEquals("expense.budget.error.year",
                refusal(draft(ExpenseBudgetRules.FIRST_YEAR - 1, null, "100"), POWER));
        assertEquals("expense.budget.error.year",
                refusal(draft(ExpenseBudgetRules.LAST_YEAR + 1, null, "100"), POWER));
        assertDoesNotThrow(() -> ExpenseBudgetRules.require(draft(ExpenseBudgetRules.FIRST_YEAR, null, "1"), POWER));
        assertDoesNotThrow(() -> ExpenseBudgetRules.require(draft(ExpenseBudgetRules.LAST_YEAR, null, "1"), POWER));
    }

    @Test
    @DisplayName("a month outside 1-12 is refused; no month at all is the yearly budget")
    void monthRange() {
        assertEquals("expense.budget.error.month", refusal(draft(2026, 0, "100"), POWER));
        assertEquals("expense.budget.error.month", refusal(draft(2026, 13, "100"), POWER));
    }

    @Test
    @DisplayName("a budget of nothing is not a budget, and neither is a negative one")
    void amountMustBePositive() {
        assertEquals("expense.budget.error.amount", refusal(draft(2026, null, "0"), POWER));
        assertEquals("expense.budget.error.amount", refusal(draft(2026, null, "-5"), POWER));
        assertEquals("expense.budget.error.amount",
                assertThrows(UserValidationException.class, () -> ExpenseBudgetRules.require(
                        new ExpenseBudgetDraft(0, POWER.id(), 2026, null, null, ""), POWER)).getMessage());
        assertEquals("expense.budget.error.amount.big",
                refusal(draft(2026, null, ExpenseBudgetRules.MAX_AMOUNT.add(BigDecimal.ONE).toPlainString()),
                        POWER));
    }

    @Test
    @DisplayName("a second budget for the same period is refused, and the message says which period")
    void duplicates() {
        assertEquals("expense.budget.error.duplicate.year",
                assertThrows(UserValidationException.class,
                        () -> ExpenseBudgetRules.requireNotTaken(draft(2026, null, "100"), true)).getMessage());
        assertEquals("expense.budget.error.duplicate.month",
                assertThrows(UserValidationException.class,
                        () -> ExpenseBudgetRules.requireNotTaken(draft(2026, 9, "100"), true)).getMessage());
        assertDoesNotThrow(() -> ExpenseBudgetRules.requireNotTaken(draft(2026, 9, "100"), false));
    }
}
