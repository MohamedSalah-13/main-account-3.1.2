package com.hamza.account.features.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one place expense headings are read and written.
 * <p>
 * <b>The pickers are unguarded, and on purpose.</b> A cashier recording an electricity bill picks a
 * heading by its name; that is not a request to read what anybody spent. The guard belongs on what
 * hands over figures - {@link #usage} - and on every write.
 */
public final class ExpenseHeadingService {

    private final ExpenseHeadingRepository repository;

    public ExpenseHeadingService() {
        this(new JdbcExpenseHeadingRepository());
    }

    public ExpenseHeadingService(ExpenseHeadingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    // ---- reading ------------------------------------------------------------------------

    /** Every heading, stopped ones included - the headings screen, which is where one is restarted. */
    public List<ExpenseHeading> all() throws DaoException {
        return repository.all();
    }

    /** What the expenses screen offers: active, and not a heading employees are paid under. */
    public List<ExpenseHeading> forExpenses() throws DaoException {
        return repository.all().stream()
                .filter(heading -> heading.active() && !heading.employeePayment())
                .toList();
    }

    /**
     * What the employee payment screen offers. It used to offer every heading, "مرتبات" beside
     * "كهرباء", with nothing to say which a salary belongs under.
     */
    public List<ExpenseHeading> forEmployeePayments() throws DaoException {
        return repository.all().stream()
                .filter(heading -> heading.active() && heading.employeePayment())
                .toList();
    }

    /** The main headings, for the parent picker. A sub-heading cannot hold one - two levels, no more. */
    public List<ExpenseHeading> mainHeadings() throws DaoException {
        return repository.all().stream().filter(ExpenseHeading::isMain).toList();
    }

    public ExpenseHeading find(int id) throws DaoException {
        return repository.find(id);
    }

    /**
     * The count and the total beside every heading. Figures, so they ask the permission the expenses
     * list asks.
     */
    public Map<Integer, ExpenseHeadingUsage> usage(LocalDate since) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_SHOW);
        return repository.usage(since);
    }

    // ---- writing ------------------------------------------------------------------------

    /**
     * Adds or edits a heading.
     *
     * @return the heading's code - generated for a new one
     */
    public int save(ExpenseHeadingDraft draft) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        ExpenseHeadingRules.requireValid(draft, repository.all());
        // A courtesy that makes the ordinary case readable. Two people saving one name both pass it,
        // and expenses_pk refuses the second - which is translated below into the same sentence.
        if (repository.nameTaken(draft.name(), draft.id())) {
            throw new UserValidationException("expense.heading.error.name.taken");
        }
        try {
            if (draft.isNew()) {
                return repository.insert(draft, currentUserId());
            }
            repository.update(draft);
            return draft.id();
        } catch (DaoException failure) {
            if (isDuplicateKey(failure)) {
                throw new UserValidationException("expense.heading.error.name.taken", failure);
            }
            throw failure;
        }
    }

    /**
     * Deletes a heading nothing holds. One with expenses or sub-headings is refused by
     * {@code DeletionService} through {@code DeleteRegistry.EXPENSE_HEADINGS}, with the count in the
     * message - and a heading merely out of use is stopped instead, which is what {@code is_active} is
     * for.
     */
    public int delete(int headingId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        ExpenseHeadingRules.requireDeletable(repository.find(headingId));
        return DeletionService.shared()
                .delete(DeleteRegistry.EXPENSE_HEADINGS, headingId, repository::delete)
                .rowsOrThrow();
    }

    private static boolean isDuplicateKey(Throwable failure) {
        for (Throwable link = failure; link != null; link = link.getCause()) {
            if (link instanceof SQLIntegrityConstraintViolationException
                    && link.getMessage() != null && link.getMessage().contains("Duplicate entry")) {
                return true;
            }
        }
        return false;
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
