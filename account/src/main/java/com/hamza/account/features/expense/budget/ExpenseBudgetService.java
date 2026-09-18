package com.hamza.account.features.expense.budget;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.JdbcExpenseHeadingRepository;
import com.hamza.account.features.expense.report.ExpenseReportRows;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The one place a budget is read, written and compared with what was spent.
 * <p>
 * <b>Reading a budget asks the reports permission; setting one asks its own.</b> Seeing that the shop
 * budgeted 5,000 for electricity is the same kind of fact as seeing it spent 4,300 - both belong to
 * whoever may read the expense reports. Deciding the 5,000 is a different act, and {@code V66} grants
 * {@code expenses.budget.manage} to whoever manages the headings rather than to every reader.
 * <p>
 * <b>The actual side is never computed here.</b> It arrives from the report by heading's own rows, so
 * "spent" on this screen and "spent" on that one are one query - the rule that keeps the whole expenses
 * area from growing a second answer to a figure it already has.
 */
public final class ExpenseBudgetService {

    /** What reads the spend: the report by heading's own totals, over the same filter. */
    @FunctionalInterface
    public interface Actuals {
        List<ExpenseReportRows.HeadingTotal> headingTotals(ExpenseFilter filter) throws DaoException;
    }

    private final ExpenseBudgetRepository repository;
    private final ExpenseHeadingRepository headings;
    private final Actuals actuals;

    public ExpenseBudgetService() {
        this(new JdbcExpenseBudgetRepository(), new JdbcExpenseHeadingRepository(),
                new com.hamza.account.features.expense.report.JdbcExpenseReportRepository()::headingTotals);
    }

    public ExpenseBudgetService(ExpenseBudgetRepository repository, ExpenseHeadingRepository headings,
                                Actuals actuals) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.headings = Objects.requireNonNull(headings, "headings");
        this.actuals = Objects.requireNonNull(actuals, "actuals");
    }

    // ---- reading ------------------------------------------------------------------------

    /** Every budget of one year - the management screen's list. */
    public List<ExpenseBudget> byYear(int year) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        return repository.byYear(year);
    }

    /** The years that already have budgets, newest first. */
    public List<Integer> years() throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        return repository.years();
    }

    /**
     * The budget against the spend over the filter's period. A scope with no whole period is refused in
     * words: a budget is for a period, and "everything ever" is not one.
     */
    public ExpenseBudgetReport report(ExpenseFilter scope) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_REPORTS);
        LocalDate from = scope.from();
        LocalDate to = scope.to();
        if (from == null || to == null) {
            throw new UserValidationException("expense.report.error.period");
        }
        return ExpenseBudgetReport.build(scope, headings.all(), repository.forPeriod(from, to),
                actuals.headingTotals(scope));
    }

    // ---- writing ------------------------------------------------------------------------

    /**
     * Saves a budget - new or corrected - and answers its code. The rules refuse before anything is
     * read or written, and the duplicate check is a courtesy the unique index still has the last word on.
     */
    public int save(ExpenseBudgetDraft draft) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_BUDGET_MANAGE);
        ExpenseHeading heading = headings.find(draft.headingId());
        ExpenseBudgetRules.require(draft, heading);
        ExpenseBudgetRules.requireNotTaken(draft,
                repository.taken(draft.headingId(), draft.year(), draft.month(), draft.isNew() ? 0 : draft.id()));
        if (draft.isNew()) {
            return repository.insert(draft, currentUserId());
        }
        repository.update(draft);
        return draft.id();
    }

    /**
     * Removes a budget. There is nothing to protect: a budget is a decision about the future, holds no
     * money, and is referenced by nothing - the expenses themselves never point at it.
     */
    public int delete(int budgetId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_BUDGET_MANAGE);
        return repository.delete(budgetId);
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
