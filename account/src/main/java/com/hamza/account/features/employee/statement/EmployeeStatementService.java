package com.hamza.account.features.employee.statement;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The one place an employee's account is read.
 * <p>
 * It answers one question — what has happened on this employee's account, and what is owed — and
 * it answers it from {@code employee_account_table}, which unions the ledger with the cash. The
 * other question, what every employee is owed, is a different one with its own view
 * ({@code employee_balance}); keeping them apart is what lets the running balance be seeded with
 * one scalar read instead of a correlated subquery per row.
 * <p>
 * <b>Everything here asks {@code employee.account.show} first.</b> A statement is a list of what
 * somebody is paid, one line at a time, so the permission that governs seeing a salary governs
 * seeing this — and V58 grants it to exactly the roles that already held that one.
 */
public final class EmployeeStatementService {

    /** As elsewhere: an extract wide enough to print, narrow enough to hold. */
    public static final int PRINT_LIMIT = 10_000;

    private final EmployeeStatementRepository repository;

    public EmployeeStatementService() {
        this(new JdbcEmployeeStatementRepository());
    }

    public EmployeeStatementService(EmployeeStatementRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * One page, with the figures for the whole period.
     * <p>
     * One row more than the page holds is fetched, and that row answers "is there another page" —
     * so there is no second count to fall out of step with the conditions of the page itself.
     */
    public EmployeeStatementPage search(EmployeeStatementFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_SHOW);
        List<EmployeeStatementRow> fetched = repository.page(filter);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<EmployeeStatementRow> rows =
                hasNext ? fetched.subList(0, filter.pageSize()) : fetched;
        return new EmployeeStatementPage(rows, repository.summarize(filter), filter.page(),
                filter.page() > 0, hasNext, false);
    }

    /**
     * Every matching movement, for printing and for export.
     * <p>
     * A query of its own with the same filter, so the printed statement is the statement on
     * screen — all of it, rather than the page that happens to be showing.
     */
    public EmployeeStatementPage forPrint(EmployeeStatementFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_SHOW);
        EmployeeStatementFilter printable = filter.firstPageWithSize(PRINT_LIMIT);
        List<EmployeeStatementRow> fetched = repository.page(printable);
        boolean truncated = fetched.size() > PRINT_LIMIT;
        List<EmployeeStatementRow> rows = truncated ? fetched.subList(0, PRINT_LIMIT) : fetched;
        return new EmployeeStatementPage(rows, repository.summarize(printable), 0, false, false,
                truncated);
    }

    /**
     * What this employee is owed right now.
     * <p>
     * Read from {@code employee_balance}, which is derived from the same view the statement reads,
     * so the figure beside a name and the figure under a statement cannot drift. A second
     * computation of a balance is the defect {@code view_customer_receivables} carried for years.
     */
    public BigDecimal currentBalance(int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_SHOW);
        return repository.currentBalance(employeeId);
    }

    /**
     * The day this employee's account starts, which is where a statement opens.
     * {@code null} when nothing has happened to them yet.
     */
    public LocalDate earliestMovement(int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_SHOW);
        return repository.earliestMovement(employeeId);
    }

    /** The users the statement's "entered by" filter can offer - those who actually entered one. */
    public List<EmployeeStatementUserOption> usersWhoEntered(int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_SHOW);
        return repository.usersWhoEntered(employeeId);
    }
}
