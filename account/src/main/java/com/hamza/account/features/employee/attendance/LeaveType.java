package com.hamza.account.features.employee.attendance;

/**
 * A kind of leave, and the two numbers that decide what it means.
 *
 * @param isPaid      whether a day of it costs the employee anything. <b>Read by joining, never
 *                    copied onto an attendance row</b>: copying it would mean a later
 *                    correction to the type left the days already recorded saying the old
 *                    thing, and the database would hold two answers to one question.
 * @param annualLimit days a year, where zero means "no limit written down" rather than
 *                    "none allowed" - so a migration forces no number on anybody
 */
public record LeaveType(int id, String name, boolean isPaid, int annualLimit, boolean isActive,
                        String notes) {

    /** What the payroll asks of it: does a day of this leave reduce the month? */
    public boolean countsAsAbsence() {
        return !isPaid;
    }
}
