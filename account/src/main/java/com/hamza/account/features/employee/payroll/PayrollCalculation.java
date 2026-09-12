package com.hamza.account.features.employee.payroll;

import com.hamza.account.features.employee.SalaryKind;

import java.math.BigDecimal;

/**
 * What one employee's month came to, with every component kept separately.
 * <p>
 * Nothing here is a net figure standing in for its parts. The reason is the ledger: approval
 * writes an {@code ENTITLEMENT} for what was earned and a {@code DEDUCTION} for what was
 * taken off, each with its real kind - so a statement can say what happened rather than
 * showing one line whose meaning changes with the month. A single netted row would make
 * "entitlement" mean the entitlement sometimes and the remainder other times, which is the
 * defect V58 removed when it put the direction in the kind rather than in a sign.
 *
 * @param employeeId       who
 * @param salaryKind       how they are paid, carried through for the payslip
 * @param rate             the rate used, carried through so the line records what it was told
 * @param basic            what the salary kind produced before anything was added or taken off
 * @param allowances       fixed allowances
 * @param commission       what a commission run approved
 * @param absenceDeduction what unpaid absence cost - separate from manual deductions so a
 *                         payslip can show it as its own line
 * @param deductions       manual deductions
 * @param netPay           {@link #earned()} less {@link #totalDeductions()}. <b>May be
 *                         negative</b>: a month whose deductions exceed its earnings is a real
 *                         case, and the employee's balance then says so.
 */
public record PayrollCalculation(int employeeId,
                                 SalaryKind salaryKind,
                                 BigDecimal rate,
                                 BigDecimal basic,
                                 BigDecimal allowances,
                                 BigDecimal commission,
                                 BigDecimal absenceDeduction,
                                 BigDecimal deductions,
                                 BigDecimal netPay) {

    /** What the employee earned: the figure approval writes as an {@code ENTITLEMENT}. */
    public BigDecimal earned() {
        return basic.add(allowances).add(commission);
    }

    /** What was taken off: the figure approval writes as a {@code DEDUCTION}, when it is not zero. */
    public BigDecimal totalDeductions() {
        return absenceDeduction.add(deductions);
    }

    /** Whether this line writes anything at all - a row of zeroes is not an entitlement. */
    public boolean isEmpty() {
        return earned().signum() == 0 && totalDeductions().signum() == 0;
    }
}
