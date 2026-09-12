package com.hamza.account.features.employee.payroll;

import com.hamza.account.features.employee.attendance.AttendanceService;
import com.hamza.account.features.employee.attendance.AttendanceSummary;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;

/**
 * Where the payroll gets a month's worked days, absences and hours.
 * <p>
 * A seam of one method, for two reasons. The payroll can be tested without an attendance
 * database - and, more importantly, a shop that has not started keeping attendance is in
 * exactly the state {@link #NONE} describes: every month unrecorded, every line the salary
 * alone. Making that the explicit default rather than an accident is what keeps phase D from
 * changing anybody's payroll the day it is installed.
 */
@FunctionalInterface
public interface AttendanceSource {

    /** No attendance kept: every month is unrecorded, and no line is reduced by it. */
    AttendanceSource NONE = (employeeId, from, to) -> AttendanceSummary.EMPTY;

    AttendanceSummary summaryFor(int employeeId, LocalDate from, LocalDate to) throws DaoException;

    /**
     * The real thing.
     * <p>
     * A reader who may not see attendance gets {@link AttendanceSummary#EMPTY} rather than an
     * exception: the permission governs the grid, and a payroll that refused to build because
     * its author cannot open the attendance screen would be a rule nobody asked for.
     */
    static AttendanceSource fromService(AttendanceService service) {
        return (employeeId, from, to) -> {
            try {
                return service.summaryFor(employeeId, from, to);
            } catch (RuntimeException denied) {
                return AttendanceSummary.EMPTY;
            }
        };
    }
}
