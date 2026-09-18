package com.hamza.account.features.expense.recurring;

import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a recurring template may be. */
class ExpenseRecurringDraftTest {

    private static final ExpenseHeading RENT = new ExpenseHeading(11, "إيجار", 10, "إدارية", true, null, false);
    private static final ExpenseHeading STOPPED = new ExpenseHeading(12, "قديم", null, null, false, null, false);
    private static final ExpenseHeading SALARIES = new ExpenseHeading(1, "مرتبات", null, null, true, null, true);

    private static ExpenseRecurringDraft draft() {
        return new ExpenseRecurringDraft(0, RENT.id(), 1, new BigDecimal("5000"), "المالك", "",
                ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true);
    }

    private static String refusal(ExpenseRecurringDraft draft, ExpenseHeading heading) {
        return assertThrows(UserValidationException.class, () -> draft.require(heading)).getMessage();
    }

    @Test
    @DisplayName("an ordinary template with an open end passes")
    void ordinary() {
        assertDoesNotThrow(() -> draft().require(RENT));
        assertTrue(draft().isNew());
        assertFalse(new ExpenseRecurringDraft(4, RENT.id(), 1, BigDecimal.ONE, null, null,
                ExpenseFrequency.YEARLY, 1, LocalDate.of(2026, 1, 1), null, true).isNew());
    }

    @Test
    @DisplayName("a salary heading is refused: an employee is paid through the employee payment screen")
    void employeeHeadingsAreRefused() {
        // Otherwise a template would remind somebody to record a salary as an ordinary expense, which is
        // exactly the route decision q-5 closed - the employee's account would never see it.
        assertEquals("expense.recurring.error.heading.employee",
                refusal(new ExpenseRecurringDraft(0, SALARIES.id(), 1, new BigDecimal("5000"), null, null,
                        ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true), SALARIES));
    }

    @Test
    @DisplayName("no heading, a stopped heading, and no till")
    void headingAndTreasury() {
        assertEquals("expense.recurring.error.heading", refusal(draft(), null));
        assertEquals("expense.recurring.error.heading.stopped", refusal(draft(), STOPPED));
        assertEquals("expense.recurring.error.treasury",
                refusal(new ExpenseRecurringDraft(0, RENT.id(), 0, new BigDecimal("5000"), null, null,
                        ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true), RENT));
    }

    @Test
    @DisplayName("the amount is positive and fits the column")
    void amount() {
        assertEquals("expense.recurring.error.amount",
                refusal(new ExpenseRecurringDraft(0, RENT.id(), 1, BigDecimal.ZERO, null, null,
                        ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true), RENT));
        assertEquals("expense.recurring.error.amount.big",
                refusal(new ExpenseRecurringDraft(0, RENT.id(), 1,
                        ExpenseRecurringDraft.MAX_AMOUNT.add(BigDecimal.ONE), null, null,
                        ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true), RENT));
    }

    @Test
    @DisplayName("the day due is 1 to 31 - the 31st is clamped by the schedule, not refused here")
    void dayOfMonth() {
        for (int day : new int[]{0, 32}) {
            assertEquals("expense.recurring.error.day",
                    refusal(new ExpenseRecurringDraft(0, RENT.id(), 1, BigDecimal.TEN, null, null,
                            ExpenseFrequency.MONTHLY, day, LocalDate.of(2026, 1, 1), null, true), RENT));
        }
        assertDoesNotThrow(() -> new ExpenseRecurringDraft(0, RENT.id(), 1, BigDecimal.TEN, null, null,
                ExpenseFrequency.MONTHLY, 31, LocalDate.of(2026, 1, 1), null, true).require(RENT));
    }

    @Test
    @DisplayName("an end before the start is refused; no end at all is the ordinary case")
    void period() {
        assertEquals("expense.recurring.error.start",
                refusal(new ExpenseRecurringDraft(0, RENT.id(), 1, BigDecimal.TEN, null, null,
                        ExpenseFrequency.MONTHLY, 1, null, null, true), RENT));
        assertEquals("expense.recurring.error.period",
                refusal(new ExpenseRecurringDraft(0, RENT.id(), 1, BigDecimal.TEN, null, null,
                        ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 30), true),
                        RENT));
        assertDoesNotThrow(() -> new ExpenseRecurringDraft(0, RENT.id(), 1, BigDecimal.TEN, null, null,
                ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1), true)
                .require(RENT), "an end on the start day is one period, not a mistake");
    }

    @Test
    @DisplayName("the payee and the notes are stripped, and a missing one is blank rather than null")
    void textIsCleaned() {
        ExpenseRecurringDraft draft = new ExpenseRecurringDraft(0, RENT.id(), 1, BigDecimal.TEN, "  المالك  ",
                null, ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true);
        assertEquals("المالك", draft.payee());
        assertEquals("", draft.notes());
    }
}
