package com.hamza.account.features.employee.statement;

import java.math.BigDecimal;

/**
 * The figures under an employee's statement.
 * <p>
 * <b>The two balances answer the dates and nothing else; the two totals answer every filter.</b>
 * A balance is the sum of everything up to a day - narrowed by kind or by who entered it, it is a
 * number nobody is owed, and it is the figure an employee signs for. The totals describe the rows
 * actually shown, which is what a reader adding up the column in front of them expects to get.
 *
 * @param openingBalance what the business owed before the first day of the period. Positive means
 *                       the business owes the employee
 * @param shownCount     how many movements the filters matched over the whole period, not on
 *                       the page - it is what the pager clamps a typed page number to, and it is
 *                       counted in SQL beside the totals rather than guessed from whether another
 *                       page happened to come back
 * @param closingBalance what it owes at the end of the last day
 */
public record EmployeeStatementSummary(BigDecimal openingBalance,
                                       BigDecimal totalDebit,
                                       BigDecimal totalCredit,
                                       int shownCount,
                                       BigDecimal closingBalance) {

    public static final EmployeeStatementSummary EMPTY = new EmployeeStatementSummary(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);

    public EmployeeStatementSummary {
        openingBalance = openingBalance == null ? BigDecimal.ZERO : openingBalance;
        totalDebit = totalDebit == null ? BigDecimal.ZERO : totalDebit;
        totalCredit = totalCredit == null ? BigDecimal.ZERO : totalCredit;
        closingBalance = closingBalance == null ? BigDecimal.ZERO : closingBalance;
    }

    /** What the shown rows changed by. Not the closing balance: a filter can hide movements. */
    public BigDecimal shownChange() {
        return totalCredit.subtract(totalDebit);
    }

    /** True when the employee owes the business rather than the other way round. */
    public boolean employeeOwes() {
        return closingBalance.signum() < 0;
    }
}
