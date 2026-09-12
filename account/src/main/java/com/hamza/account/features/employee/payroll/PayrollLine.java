package com.hamza.account.features.employee.payroll;

import com.hamza.account.features.employee.SalaryKind;

import java.math.BigDecimal;

/**
 * One employee's line in a run, as stored and as shown.
 * <p>
 * It carries the components rather than a net figure, for the reason
 * {@link PayrollCalculation} gives: approval writes an entitlement and a deduction with their
 * real kinds, and a payslip has to print what each part was.
 *
 * @param advancesOutstanding what was already handed over as an advance. <b>Informational,
 *                            printed, and in no arithmetic anywhere</b> - the advance was
 *                            deducted on the day the cash left (rule ق-٥).
 */
public record PayrollLine(int id,
                          int payrollRunId,
                          int employeeId,
                          String employeeName,
                          String jobName,
                          SalaryKind salaryKind,
                          BigDecimal rate,
                          BigDecimal workedDays,
                          BigDecimal absenceDays,
                          BigDecimal workedHours,
                          BigDecimal basic,
                          BigDecimal allowances,
                          BigDecimal commission,
                          BigDecimal deductions,
                          BigDecimal absenceDeduction,
                          BigDecimal advancesOutstanding,
                          BigDecimal netPay,
                          String notes) {

    /** What the employee earned - the figure approval writes as an entitlement. */
    public BigDecimal earned() {
        return basic.add(allowances).add(commission);
    }

    /** What was taken off - the figure approval writes as a deduction. */
    public BigDecimal totalDeductions() {
        return absenceDeduction.add(deductions);
    }
}
