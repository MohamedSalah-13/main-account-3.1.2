package com.hamza.account.features.employee.statement;

import java.util.List;

/**
 * One page of an employee's statement with the figures for the whole period.
 *
 * @param truncated only for a print extract that hit its ceiling - the screen says so rather than
 *                  handing over a file that silently stops
 */
public record EmployeeStatementPage(List<EmployeeStatementRow> rows,
                                    EmployeeStatementSummary summary,
                                    int page,
                                    boolean hasPrevious,
                                    boolean hasNext,
                                    boolean truncated) {

    public static final EmployeeStatementPage EMPTY = new EmployeeStatementPage(
            List.of(), EmployeeStatementSummary.EMPTY, 0, false, false, false);

    public EmployeeStatementPage {
        rows = List.copyOf(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
