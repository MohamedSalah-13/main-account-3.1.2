package com.hamza.account.features.employee;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;

/**
 * When the salary on the employee form may be written back, and when it may only be changed by
 * recording a dated one.
 * <p>
 * This is {@code OpeningBalanceGuard} applied to the other figure with no date on it.
 * {@code employees.salary} is what the employee was hired at; the figure in force today is the
 * newest {@link EmployeeCompensation} row. So:
 * <ul>
 *   <li>While the employee has <b>one</b> compensation row - the one written when they were
 *       created, or copied from the old column by V57 - that row and the column still say the
 *       same single thing, and a correction typed the same afternoon is a correction. Both
 *       halves move together.</li>
 *   <li>Once there is a <b>second</b> row, the history has begun. Rewriting the hire figure
 *       then changes what August was calculated from, silently, with nothing recording that it
 *       moved - the exact defect the dated table exists to remove. It is refused, and the
 *       message says to record a change instead.</li>
 * </ul>
 * <b>And the message has to point at a road that exists.</b> {@code opening.correction.customers}
 * told users for months to record a movement on an account that had no writer; the road here is
 * {@code EmployeeService.changeSalary}, and it is reachable from the same screen that shows the
 * refusal.
 */
public final class SalaryChangeGuard {

    private SalaryChangeGuard() {
    }

    /**
     * @param compensationRows how many rows {@code employee_compensation} holds for this
     *                         employee - counted inside the saving transaction, never taken
     *                         from what a dialog is holding
     */
    public static boolean mayCorrectHireRate(int compensationRows) {
        return compensationRows <= 1;
    }

    /** Throws the key the screen shows when a correction has become a change of history. */
    public static void requireCorrectable(int compensationRows) throws UserValidationException {
        if (!mayCorrectHireRate(compensationRows)) {
            throw new UserValidationException("employee.error.salary.locked");
        }
    }

    /**
     * Whether the form is actually asking to move the figure.
     * <p>
     * Compared with {@code compareTo}: {@code new BigDecimal("100")} does not
     * {@code equals} {@code new BigDecimal("100.00")}, and a form that round-trips through a
     * text field produces one or the other depending on how it was typed. An edit that changes
     * nothing must not be refused as though it changed something.
     */
    public static boolean isChanged(SalaryKind storedKind, BigDecimal storedRate,
                                    SalaryKind kind, BigDecimal rate) {
        if (storedKind != kind) {
            return true;
        }
        if (storedRate == null || rate == null) {
            return storedRate != rate;
        }
        return storedRate.compareTo(rate) != 0;
    }
}
