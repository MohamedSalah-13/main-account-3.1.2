package com.hamza.account.features.employee;

import java.util.List;

/**
 * One page of employees with the figures for the whole filtered set.
 *
 * @param truncated only ever true for a print extract that hit its ceiling - the screen says so
 *                  rather than handing over a file that silently stops
 */
public record EmployeePage(List<Employee> rows, EmployeeSummary summary, int page,
                           boolean hasPrevious, boolean hasNext, boolean truncated) {

    public static final EmployeePage EMPTY =
            new EmployeePage(List.of(), EmployeeSummary.EMPTY, 0, false, false, false);

    public EmployeePage {
        rows = List.copyOf(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
