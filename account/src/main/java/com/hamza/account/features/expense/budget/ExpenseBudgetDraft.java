package com.hamza.account.features.expense.budget;

import java.math.BigDecimal;

/**
 * What the budget form holds before it is saved. The rules that judge it are
 * {@link ExpenseBudgetRules}, so they can be tested without a screen.
 *
 * @param id    the budget being corrected, or 0 for a new one
 * @param month {@code null} for a budget covering the whole year
 */
public record ExpenseBudgetDraft(int id, int headingId, int year, Integer month, BigDecimal amount, String notes) {

    public ExpenseBudgetDraft {
        notes = notes == null ? "" : notes.strip();
    }

    public boolean isNew() {
        return id <= 0;
    }
}
