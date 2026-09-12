package com.hamza.account.features.employee.attendance;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day as somebody wants it recorded, refused with message keys before it reaches a database.
 * <p>
 * The leave-type rule is checked here <b>and</b> by V60's CHECK, in both directions. That is
 * not belt and braces: the constraint protects against a caller that is not this service, and
 * this class is what produces a sentence a person can act on instead of a constraint violation.
 */
public record AttendanceEntry(int employeeId, LocalDate date, AttendanceStatus status,
                              BigDecimal hours, Integer leaveTypeId, String notes) {

    private static final BigDecimal MAX_HOURS = new BigDecimal("24");

    public static AttendanceEntry parse(int employeeId, LocalDate date, AttendanceStatus status,
                                        BigDecimal hours, Integer leaveTypeId, String notes)
            throws UserValidationException {
        if (employeeId <= 0) {
            throw new UserValidationException("attendance.error.employee");
        }
        if (date == null) {
            throw new UserValidationException("attendance.error.date");
        }
        if (status == null) {
            throw new UserValidationException("attendance.error.status");
        }
        BigDecimal cleanHours = hours == null ? BigDecimal.ZERO : hours;
        if (cleanHours.signum() < 0 || cleanHours.compareTo(MAX_HOURS) > 0) {
            throw new UserValidationException("attendance.error.hours");
        }
        // A day nobody worked carries no hours, whatever a grid may have left in the cell.
        if (!status.isWorked()) {
            cleanHours = BigDecimal.ZERO;
        }
        if (status.needsLeaveType() && (leaveTypeId == null || leaveTypeId <= 0)) {
            throw new UserValidationException("attendance.error.leave.type.required");
        }
        if (!status.needsLeaveType() && leaveTypeId != null) {
            throw new UserValidationException("attendance.error.leave.type.unexpected");
        }
        String cleanNotes = notes == null ? null : notes.trim();
        if (cleanNotes != null && cleanNotes.length() > 255) {
            throw new UserValidationException("attendance.error.notes");
        }
        return new AttendanceEntry(employeeId, date, status, cleanHours,
                status.needsLeaveType() ? leaveTypeId : null,
                cleanNotes == null || cleanNotes.isEmpty() ? null : cleanNotes);
    }
}
