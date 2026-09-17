package com.hamza.account.features.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.employee.statement.EmployeeStatementRepository;
import com.hamza.account.features.employee.statement.JdbcEmployeeStatementRepository;
import com.hamza.account.features.expense.ExpenseEntry;
import com.hamza.account.features.expense.ExpenseService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

import java.util.Objects;

/**
 * Paying an employee — which is writing an expense, and saying what it was for.
 * <p>
 * <b>It writes no cash table of its own, and that is the decision the whole module rests on.</b>
 * The pound that leaves the till is an {@code expenses_details} row, so this service inherits four
 * rules already built and tested rather than rebuilding them beside four chances to forget one:
 * {@code ShiftGate} attributes it to the open shift and writes the shift cash journal,
 * {@code PeriodLock} refuses a closed month, {@code AuthorizationGuard} asks about expenses, and
 * {@code treasury_balance} already counts it. And it means the years of wage payments sitting in
 * customers' databases appear on the new statement with no data migration at all — ق-١.
 * <p>
 * <b>An advance is a debit on the day it leaves, and is never deducted again.</b> The payroll run
 * of phase C enters the whole entitlement into the ledger and hands over the difference; the
 * balance squares because each side is recorded exactly once, in its own place. Deducting the
 * advance a second time would charge the employee twice for one payment — ق-٥, the line the plan
 * calls its most important.
 * <p>
 * Both rows go in <b>one</b> transaction: a payment whose purpose was lost would read as a salary
 * on every statement afterwards, which for an advance is the difference between an employee owing
 * a thousand and owing nothing. Since the expenses rework this is also the <b>only</b> road an
 * employee is paid by: the expenses screen no longer names an employee at all
 * (docs/expenses-plan.md ق-٥), and {@link ExpenseService#recordForEmployee} refuses a heading that
 * is not marked for employees.
 */
public final class EmployeePaymentService {

    private final ExpenseService expenses;
    private final EmployeeStatementRepository repository;

    public EmployeePaymentService(ExpenseService expenses) {
        this(expenses, new JdbcEmployeeStatementRepository());
    }

    public EmployeePaymentService(ExpenseService expenses, EmployeeStatementRepository repository) {
        this.expenses = Objects.requireNonNull(expenses, "expenses");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Records the payment and files it under its purpose.
     * <p>
     * {@code employee.pay} is asked here and {@code expenses.create} inside
     * {@link ExpenseService#recordForEmployee} — <b>both are needed</b>. That is not an oversight:
     * this is an expense with an employee on it, so the base act is creating an expense and this key
     * is the extra permission to direct one at a person. V58 grants it to whoever already holds
     * {@code expenses.create}, so nobody loses an ability on upgrade.
     *
     * @return the generated expense id
     */
    public int pay(EmployeePayment payment) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_PAY);
        ExpenseEntry entry = ExpenseEntry.parse(0, payment.date(), payment.expenseTypeCode(),
                payment.treasuryId(), payment.amount(), null, null, payment.notes());
        return TransactionTemplate.execute(() -> {
            int expenseId = expenses.recordForEmployee(entry, payment.employeeId());
            repository.insertPurpose(expenseId, payment.purpose().name(), null, currentUserId());
            return expenseId;
        });
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
