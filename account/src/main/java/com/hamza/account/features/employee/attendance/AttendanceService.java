package com.hamza.account.features.employee.attendance;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Recording what each day was, and answering the payroll with it.
 *
 * <h2>A day outside employment is refused here, not on the screen</h2>
 * {@code docs/employees-plan.md} §6 asks for it by name, and the reason is that a screen is
 * one of several callers: the grid, a future import, an acceptance test. A rule a screen
 * enforces is a rule the next caller does not have.
 *
 * <h2>What a month means is decided once</h2>
 * {@link AttendanceSummary#of} folds the days, and the payroll reads that rather than summing
 * the table itself. The absence rule is "absent, or on a leave whose type is unpaid" - not a
 * column - and two places computing it is how a system ends up with two answers to what a
 * month cost.
 *
 * <h2>A whole month is written in one transaction</h2>
 * The grid saves what a person changed, and either all of it lands or none does: half a saved
 * month is worse than an unsaved one, because nothing on screen says which half.
 */
public final class AttendanceService {

    private final AttendanceRepository repository;

    public AttendanceService() {
        this(new JdbcAttendanceRepository());
    }

    public AttendanceService(AttendanceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    // ---- reading ---------------------------------------------------------------------------

    public List<AttendanceDay> daysBetween(LocalDate from, LocalDate to) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ATTENDANCE_SHOW);
        requireRange(from, to);
        return repository.daysBetween(from, to);
    }

    public List<AttendanceDay> daysOf(int employeeId, LocalDate from, LocalDate to)
            throws DaoException {
        AuthorizationGuard.require(AppPermissions.ATTENDANCE_SHOW);
        requireRange(from, to);
        return repository.daysOf(employeeId, from, to);
    }

    /**
     * What the payroll asks: the three figures for one employee over one period.
     * <p>
     * Guarded by {@code attendance.show} like every other read of this table. A payroll run
     * that cannot see attendance gets {@link AttendanceSummary#EMPTY}, which is the same thing
     * a month with nothing recorded gives - and the payroll treats both the same way, by
     * leaving what it already had alone.
     */
    public AttendanceSummary summaryFor(int employeeId, LocalDate from, LocalDate to)
            throws DaoException {
        return AttendanceSummary.of(daysOf(employeeId, from, to));
    }

    public List<LeaveType> leaveTypes() throws DaoException {
        AuthorizationGuard.require(AppPermissions.ATTENDANCE_SHOW);
        return repository.leaveTypes();
    }

    // ---- writing ---------------------------------------------------------------------------

    /**
     * Records or corrects one day.
     *
     * @throws UserValidationException if the day falls outside the employee's employment, if a
     *                                leave day names no type, or if a day that is not leave
     *                                names one. V60 checks the last two again in the database.
     */
    public int recordDay(AttendanceEntry entry) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ATTENDANCE_RECORD);
        requireWithinEmployment(entry.employeeId(), entry.date(), entry.date());
        return repository.upsertDay(entry.employeeId(), entry.date(), entry.status().name(),
                entry.hours(), entry.leaveTypeId(), entry.notes(), currentUserId());
    }

    /** Saves everything the grid changed, or nothing. */
    public int recordDays(List<AttendanceEntry> entries) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ATTENDANCE_RECORD);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        for (AttendanceEntry entry : entries) {
            requireWithinEmployment(entry.employeeId(), entry.date(), entry.date());
        }
        return TransactionTemplate.execute(() -> {
            int written = 0;
            for (AttendanceEntry entry : entries) {
                written += repository.upsertDay(entry.employeeId(), entry.date(),
                        entry.status().name(), entry.hours(), entry.leaveTypeId(), entry.notes(),
                        currentUserId());
            }
            return written;
        });
    }

    public int clearDay(int employeeId, LocalDate date) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ATTENDANCE_RECORD);
        return repository.deleteDay(employeeId, date);
    }

    // ---- the rules ---------------------------------------------------------------------------

    private static void requireRange(LocalDate from, LocalDate to) throws UserValidationException {
        if (from == null || to == null || to.isBefore(from)) {
            throw new UserValidationException("attendance.error.range");
        }
    }

    /**
     * A day before somebody was hired, or after they left, is not a day they were absent from.
     * <p>
     * Recording one would feed the payroll an absence for a month the employee was not there,
     * and the deduction would look exactly like a real one.
     */
    private void requireWithinEmployment(int employeeId, LocalDate from, LocalDate to)
            throws DaoException {
        LocalDate[] employment = repository.employmentDates(employeeId);
        if (employment == null) {
            throw new UserValidationException("attendance.error.employee");
        }
        LocalDate hired = employment[0];
        LocalDate ended = employment[1];
        if (hired != null && from.isBefore(hired)) {
            throw new UserValidationException("attendance.error.before.hire");
        }
        if (ended != null && to.isAfter(ended)) {
            throw new UserValidationException("attendance.error.after.end");
        }
    }

    private static int currentUserId() {
        return CurrentUser.getOrNull() == null ? 1 : CurrentUser.get().getId();
    }
}
