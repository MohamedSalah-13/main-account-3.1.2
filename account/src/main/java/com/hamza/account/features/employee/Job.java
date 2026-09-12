package com.hamza.account.features.employee;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A job, as a row.
 * <p>
 * <b>What this replaces is the point.</b> {@code jobs} has been a real table with a unique
 * name since V1, and against it stood {@code UsersType} - four constants whose ids were
 * matched to those rows by hand. {@code UsersType.getUserTypeById} answered {@code null} for
 * any other id, so {@code EmployeesDao.map} produced a model whose job was null and the
 * employees screen fell over both when drawing it and when saving it. A table nobody could
 * add a row to without breaking the screen is not a table.
 * <p>
 * {@link #delegate()} is the other half: the delegate was {@code job == 4}, written into
 * {@code EmployeesDao.DELEGATE_JOB} and into two queries. A shop with a delivery delegate and
 * a collections delegate could not have both. V57 puts the flag on the row and sets it on the
 * seeded row 4 alone, so nothing changes on upgrade and everything is possible after it.
 *
 * @param defaultSalary what the employee screen offers when this job is picked, or
 *                      {@code null}. An offer, never a rule - the figure that counts is the
 *                      employee's own {@link EmployeeCompensation}
 */
public record Job(int id, String name, boolean delegate, boolean active,
                  BigDecimal defaultSalary, String notes) {

    public Job {
        Objects.requireNonNull(name, "name");
    }

    /** A job named but not yet read in full - what a list row carries. */
    public static Job of(int id, String name) {
        return new Job(id, name, false, true, null, null);
    }
}
