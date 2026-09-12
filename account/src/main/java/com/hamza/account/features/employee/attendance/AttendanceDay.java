package com.hamza.account.features.employee.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One employee on one day. There is never a second row for the same pair - V60's
 * {@code UNIQUE(employee_id, work_date)} - because two rows would make the month's absence
 * count depend on which the query read first.
 *
 * @param leaveTypeId  set when and only when the status is {@code LEAVE}; V60 checks both ways
 * @param leaveIsPaid  resolved by joining the type, not stored here
 */
public record AttendanceDay(int id, int employeeId, String employeeName, LocalDate date,
                            AttendanceStatus status, BigDecimal hours, Integer leaveTypeId,
                            String leaveTypeName, boolean leaveIsPaid, String notes) {

    /** Whether this day is deducted from a monthly salary. */
    public boolean countsAsAbsence() {
        return status == AttendanceStatus.ABSENT
                || (status == AttendanceStatus.LEAVE && !leaveIsPaid);
    }
}
