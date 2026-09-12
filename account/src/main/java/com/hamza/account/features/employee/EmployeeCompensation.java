package com.hamza.account.features.employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What an employee is paid, and from when.
 * <p>
 * <b>Why a row rather than a column.</b> {@code employees.salary} is one number with no date
 * on it, so raising a salary in September makes every calculation of August - today, or in a
 * year - read the new figure, and nothing anywhere records that it changed. That is the same
 * defect as {@code custom.first_balance} and {@code treasury.amount}, and it is answered the
 * same way: the figure gets a date, the old column keeps a fixed meaning
 * (<em>the salary at hire</em>, said in its COMMENT), and one place answers "what is he paid
 * now" - the {@code employee_current_compensation} view.
 * <p>
 * A row may be dated in the future: a raise agreed in March to start in April is entered when
 * it is agreed, and the view starts reading it on the day. Nothing else in the program has to
 * remember.
 *
 * @param effectiveFrom the day this rate starts. Unique per employee, so an amendment is an
 *                      edit of that day's row rather than a second row nobody can order
 */
public record EmployeeCompensation(int id, int employeeId, LocalDate effectiveFrom,
                                   SalaryKind salaryKind, BigDecimal rate, String notes) {

    public EmployeeCompensation {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(salaryKind, "salaryKind");
        Objects.requireNonNull(rate, "rate");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("A rate must not be negative");
        }
    }
}
