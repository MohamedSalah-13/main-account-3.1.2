package com.hamza.account.features.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.employee.statement.EmployeeStatementRepository;
import com.hamza.account.features.employee.statement.JdbcEmployeeStatementRepository;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.Objects;

/**
 * Recording what happened to an employee's account without cash moving.
 * <p>
 * <b>It does not pass through {@code ShiftGate}, and that is the rule rather than an omission.</b>
 * A deduction moves no money out of a till, so requiring an open shift for one would stop a
 * correction being made outside trading hours — which is when corrections are made. It is the same
 * line {@code CLAUDE.md} draws for a party's debit and credit notes, for the same reason, and it
 * is why a note is written through the ledger and never through the cash column.
 * <p>
 * <b>There is no update.</b> A recorded movement is corrected with an opposing entry, not edited:
 * {@code docs/employees-plan.md} §11. The party screens arrived at the same answer by never
 * offering an edit while their code still assumed one — here it is decided at the start, so
 * nothing has to agree with anything by silence.
 */
public final class EmployeeLedgerService {

    private final EmployeeStatementRepository repository;

    public EmployeeLedgerService() {
        this(new JdbcEmployeeStatementRepository());
    }

    public EmployeeLedgerService(EmployeeStatementRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Records a deduction, a bonus, an entitlement or a carried-in balance.
     *
     * @return the generated id
     */
    public int record(EmployeeLedgerEntry entry) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);
        return repository.insertLedgerEntry(entry.employeeId(), entry.date(), entry.kind().name(),
                entry.amount(), entry.notes(), currentUserId());
    }

    /**
     * Removes a movement somebody recorded by hand.
     * <p>
     * A row a payroll run wrote is refused: the run computed it, the run is frozen when it is
     * approved, and taking one line out from underneath it would leave the run's own total
     * describing rows that are no longer there. Reversing a run is the run's own operation
     * (phase C), not a delete of one of its rows.
     */
    public int remove(int employeeId, int entryId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);
        Integer run = repository.ledgerRunOf(employeeId, entryId);
        if (run != null) {
            throw new UserValidationException("employee.error.account.payroll.row");
        }
        return repository.deleteLedgerEntry(employeeId, entryId);
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
