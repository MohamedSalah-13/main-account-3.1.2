package com.hamza.account.features.employee.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A request for leave, and what was decided about it.
 *
 * @param days both ends inclusive: asking off for Sunday to Sunday is one day, not zero
 */
public record LeaveRequest(int id, int employeeId, String employeeName, int leaveTypeId,
                           String leaveTypeName, boolean leaveIsPaid, LocalDate from,
                           LocalDate to, LeaveStatus status, String reason, String decisionNote,
                           LocalDateTime decidedAt, Integer decidedBy, String decidedByName) {

    public int days() {
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }
}
