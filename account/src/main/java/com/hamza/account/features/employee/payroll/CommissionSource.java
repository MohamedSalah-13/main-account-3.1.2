package com.hamza.account.features.employee.payroll;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;

/**
 * Where the payroll gets a delegate's approved commission, and how it says it has paid it.
 * <p>
 * A seam of two methods, for the reasons {@link AttendanceSource} is one: the payroll stays
 * testable without the commission tables, and a shop that approves no commission runs is in
 * exactly the state {@link #NONE} describes - nothing due, nothing to mark - so the day this
 * arrives nobody's payroll changes. The commission box on a draft line is still typed by hand
 * there, as it always was.
 * <p>
 * <b>The two methods are one fact said twice, and must stay in step.</b> What {@link #dueFor}
 * adds up is exactly what {@link #paidBy} marks - the same lines, by the same conditions. The
 * payroll's {@code ENTITLEMENT} already includes the commission, so a line it paid and did not
 * mark would be posted a second time from the commission screen; and a line it marked and did
 * not pay would never reach the delegate at all.
 */
public interface CommissionSource {

    /** No commission runs: nothing is ever due, and there is nothing to mark. */
    CommissionSource NONE = new CommissionSource() {
        @Override
        public BigDecimal dueFor(int employeeId, PayrollPeriod period) {
            return BigDecimal.ZERO;
        }

        @Override
        public void paidBy(int payrollRunId, int employeeId, PayrollPeriod period, int userId) {
        }
    };

    /** Approved commission not yet posted anywhere, of this period or an earlier one. */
    BigDecimal dueFor(int employeeId, PayrollPeriod period) throws DaoException;

    /** Marks what {@link #dueFor} answered as paid by this run. Joins the caller's transaction. */
    void paidBy(int payrollRunId, int employeeId, PayrollPeriod period, int userId) throws DaoException;
}
