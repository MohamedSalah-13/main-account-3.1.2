package com.hamza.account.features.expense.budget;

import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;

/**
 * What a budget may be. Every refusal is a message key, never a sentence, and every rule is here rather
 * than in the screen or in the service - so each can be asked about without a database or a toolkit.
 * <p>
 * <b>A budget is set on the heading the money is filed under, including a main heading.</b> A main
 * heading with children carries expenses of its own (the "direct" line of the report by heading), so
 * refusing it a budget would leave that money unbudgetable. What the report must not do is add a main
 * heading's own budget to its children's - that rule is {@link ExpenseBudgetReport}'s, and it is the one
 * place double counting could enter.
 */
public final class ExpenseBudgetRules {

    /** The years a budget may name. The column's CHECK says the same, so the two cannot drift apart. */
    public static final int FIRST_YEAR = 2000;
    public static final int LAST_YEAR = 2100;

    /** The most a budget may be - the column is DECIMAL(15,2). */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private ExpenseBudgetRules() {
    }

    /** Refuses a draft that cannot be saved, in the order a person reads the form. */
    public static void require(ExpenseBudgetDraft draft, ExpenseHeading heading) throws UserValidationException {
        if (heading == null) {
            throw new UserValidationException("expense.budget.error.heading");
        }
        // A stopped heading keeps its old budgets - they are history - but takes no new one: budgeting
        // for a heading nothing may be filed under is a figure nothing can ever be measured against.
        if (!heading.active()) {
            throw new UserValidationException("expense.budget.error.heading.stopped");
        }
        if (draft.year() < FIRST_YEAR || draft.year() > LAST_YEAR) {
            throw new UserValidationException("expense.budget.error.year");
        }
        if (draft.month() != null && (draft.month() < 1 || draft.month() > 12)) {
            throw new UserValidationException("expense.budget.error.month");
        }
        if (draft.amount() == null || draft.amount().signum() <= 0) {
            throw new UserValidationException("expense.budget.error.amount");
        }
        if (draft.amount().compareTo(MAX_AMOUNT) > 0) {
            throw new UserValidationException("expense.budget.error.amount.big");
        }
    }

    /**
     * Refuses a second budget for the same heading and period.
     * <p>
     * The unique index refuses it too, and that is what actually holds - two people saving at once both
     * pass this check. It is here so the ordinary case reads a sentence rather than a reference code,
     * which is the rule {@code MasterDataService} follows for a duplicate name.
     */
    public static void requireNotTaken(ExpenseBudgetDraft draft, boolean taken) throws UserValidationException {
        if (taken) {
            throw new UserValidationException(draft.month() == null
                    ? "expense.budget.error.duplicate.year" : "expense.budget.error.duplicate.month");
        }
    }
}
