package com.hamza.account.features.employee;

import java.math.BigDecimal;

/**
 * The figures above the employees list, over the whole filtered set rather than the page.
 *
 * @param monthlyPayroll the monthly rates of the employees still working, and nothing else.
 *                       <b>{@code null} means the reader may not see salaries</b> - which is a
 *                       different statement from zero, and printing zero there would be a lie
 *                       about the wage bill rather than a refusal to say what it is
 */
public record EmployeeSummary(int employees, int active, int delegates, BigDecimal monthlyPayroll) {

    public static final EmployeeSummary EMPTY = new EmployeeSummary(0, 0, 0, null);

    /** How many have left, which is the other half of {@link #active()} and never a query of its own. */
    public int inactive() {
        return employees - active;
    }
}
