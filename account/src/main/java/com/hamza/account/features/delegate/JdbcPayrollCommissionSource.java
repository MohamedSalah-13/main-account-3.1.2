package com.hamza.account.features.delegate;

import com.hamza.account.features.employee.payroll.CommissionSource;
import com.hamza.account.features.employee.payroll.PayrollPeriod;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * The payroll's road to a delegate's account. Both answers come from statements that share one
 * WHERE ({@code PAYROLL_DUE_SQL}, {@code INSERT_PAYROLL_POSTINGS_SQL}), so what is summed is what
 * is marked; {@code CommissionRunQueryTest} compares the two conditions as text.
 * <p>
 * No permission is asked here. Whoever approves a payroll is paying what somebody else already
 * approved; requiring {@code commission.show} of them would make the payroll refuse to build for
 * a reason that has nothing to do with the payroll.
 */
public final class JdbcPayrollCommissionSource implements CommissionSource {

    private final CommissionRunRepository runs;

    public JdbcPayrollCommissionSource() {
        this(new JdbcCommissionRunRepository());
    }

    public JdbcPayrollCommissionSource(CommissionRunRepository runs) {
        this.runs = Objects.requireNonNull(runs, "runs");
    }

    @Override
    public BigDecimal dueFor(int employeeId, PayrollPeriod period) throws DaoException {
        return runs.payrollDue(employeeId, YearMonth.of(period.year(), period.month()));
    }

    @Override
    public void paidBy(int payrollRunId, int employeeId, PayrollPeriod period, int userId) throws DaoException {
        runs.insertPayrollPostings(payrollRunId, userId, employeeId, YearMonth.of(period.year(), period.month()));
    }
}
