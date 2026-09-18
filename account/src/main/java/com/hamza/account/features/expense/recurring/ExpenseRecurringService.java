package com.hamza.account.features.expense.recurring;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.JdbcExpenseHeadingRepository;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The one place a recurring template is read, written and asked whether it is due.
 * <p>
 * <b>Nothing here writes an expense.</b> The template reminds, and the reminder's action opens the entry
 * screen filled in; the save goes through {@code ExpenseService} exactly as a hand-entered expense does,
 * with the same permission, the same period lock, the same shift gate and the same journal row
 * (docs/expenses-plan.md §5.2). Recording from here would be a second writer of {@code expenses_details},
 * which ق-٢ exists to prevent.
 * <p>
 * <b>A template that has recorded expenses is not deleted; it is stopped.</b> The rows carry its id, and
 * {@code V66} makes the key {@code ON DELETE SET NULL}, so a delete would quietly cut the history that
 * says where those expenses came from. Stopping it leaves the history and ends the reminders - the
 * reasoning {@code UsersService.updateActive} and {@code EmployeeScope} follow.
 */
public final class ExpenseRecurringService {

    /** How far back the reminder reads recorded expenses: two years covers a yearly template's grace. */
    private static final int LOOKBACK_YEARS = 2;

    private final ExpenseRecurringRepository repository;
    private final ExpenseHeadingRepository headings;

    public ExpenseRecurringService() {
        this(new JdbcExpenseRecurringRepository(), new JdbcExpenseHeadingRepository());
    }

    public ExpenseRecurringService(ExpenseRecurringRepository repository, ExpenseHeadingRepository headings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.headings = Objects.requireNonNull(headings, "headings");
    }

    // ---- reading ------------------------------------------------------------------------

    /** Every template, stopped ones included - the management screen's list. */
    public List<ExpenseRecurring> all() throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_RECURRING_MANAGE);
        return repository.all();
    }

    public ExpenseRecurring find(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_RECURRING_MANAGE);
        return repository.find(id);
    }

    /**
     * What is due and unrecorded as at {@code today}, soonest first.
     * <p>
     * It asks {@code expenses.show} rather than the management permission: being reminded that the rent
     * is due is the same knowledge as seeing last month's rent in the list, and the person who records it
     * is not the person who decides the template exists.
     */
    public List<ExpenseRecurringDue> due(LocalDate today) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_SHOW);
        List<ExpenseRecurring> active = repository.active();
        if (active.isEmpty()) {
            return List.of();
        }
        return ExpenseRecurringSchedule.due(active,
                repository.recordedPeriods(today.minusYears(LOOKBACK_YEARS)), today);
    }

    /** How many expenses were recorded from a template - what the screen shows before it stops one. */
    public int recordedCount(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_RECURRING_MANAGE);
        return repository.recordedCount(id);
    }

    // ---- writing ------------------------------------------------------------------------

    public int save(ExpenseRecurringDraft draft) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_RECURRING_MANAGE);
        ExpenseHeading heading = headings.find(draft.headingId());
        draft.require(heading);
        if (draft.isNew()) {
            return repository.insert(draft, currentUserId());
        }
        repository.update(draft);
        return draft.id();
    }

    /** Deletes a template that has recorded nothing; one that has is refused, and is stopped instead. */
    public int delete(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_RECURRING_MANAGE);
        if (repository.recordedCount(id) > 0) {
            throw new UserValidationException("expense.recurring.error.recorded");
        }
        return repository.delete(id);
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
