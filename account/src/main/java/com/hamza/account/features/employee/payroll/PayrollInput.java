package com.hamza.account.features.employee.payroll;

import com.hamza.account.features.employee.SalaryKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Everything one employee's month is calculated from, gathered before any arithmetic starts.
 * <p>
 * It is a plain record with no database behind it so {@link PayrollCalculator} can be tested
 * against a 28-day month, a 31-day month, a fraction of a day, somebody hired mid-month and
 * somebody who left in it - which is the list {@code docs/employees-plan.md} §5 asks for by
 * name.
 *
 * @param employeeId          who
 * @param employeeName        for the payslip; the calculation never reads it
 * @param salaryKind          which of the four ways this employee is paid
 * @param rate                the figure effective in this period - a month's, a day's or an hour's
 * @param hiredOn             hire date, so a mid-month start is paid for the part worked
 * @param endedOn             last working day, or null
 * @param absenceDays         unpaid absence, which only {@link SalaryKind#MONTHLY} reduces by
 * @param workedDays          days present, which {@link SalaryKind#DAILY} multiplies
 * @param workedHours         hours present, which {@link SalaryKind#HOURLY} multiplies
 * @param allowances          fixed allowances effective in this period
 * @param commission          what a commission run approved; the only earning a
 *                            {@link SalaryKind#COMMISSION} employee has
 * @param manualDeductions    anything the accountant deducts by hand
 * @param advancesOutstanding what was already handed over as an advance - <b>informational
 *                            only</b>. It is printed so the accountant can see what to hand
 *                            over, and it never touches the arithmetic: an advance was
 *                            deducted on the day the cash left (rule ق-٥).
 */
public record PayrollInput(int employeeId,
                           String employeeName,
                           SalaryKind salaryKind,
                           BigDecimal rate,
                           LocalDate hiredOn,
                           LocalDate endedOn,
                           BigDecimal absenceDays,
                           BigDecimal workedDays,
                           BigDecimal workedHours,
                           BigDecimal allowances,
                           BigDecimal commission,
                           BigDecimal manualDeductions,
                           BigDecimal advancesOutstanding) {

    public PayrollInput {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("An employee id is required");
        }
        if (salaryKind == null) {
            throw new IllegalArgumentException("A salary kind is required");
        }
        rate = orZero(rate);
        absenceDays = orZero(absenceDays);
        workedDays = orZero(workedDays);
        workedHours = orZero(workedHours);
        allowances = orZero(allowances);
        commission = orZero(commission);
        manualDeductions = orZero(manualDeductions);
        advancesOutstanding = orZero(advancesOutstanding);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** A monthly employee with nothing else going on - the ordinary row, and most tests' start. */
    public static PayrollInput monthly(int employeeId, String name, BigDecimal rate) {
        return new PayrollInput(employeeId, name, SalaryKind.MONTHLY, rate, null, null,
                null, null, null, null, null, null, null);
    }
}
