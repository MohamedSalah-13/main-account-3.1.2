package com.hamza.account.features.employee.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * One employee's row on the month's grid, held in memory while it is being edited.
 * <p>
 * It is mutable on purpose and it is not a database row: the grid is edited for a while and
 * saved once, so what the screen holds is a working copy. What makes that safe is that the
 * row records <b>which days changed</b> - so saving writes those and leaves every other day
 * exactly as the database has it, rather than rewriting a month to save one cell.
 */
public final class AttendanceGridRow {

    private final int employeeId;
    private final String employeeName;
    private final String jobName;
    private final LocalDate hiredOn;
    private final LocalDate endedOn;

    private final Map<Integer, AttendanceStatus> statuses = new HashMap<>();
    private final Map<Integer, Integer> leaveTypes = new HashMap<>();
    private final Map<Integer, BigDecimal> hours = new HashMap<>();
    private final Map<Integer, Boolean> changed = new HashMap<>();

    public AttendanceGridRow(int employeeId, String employeeName, String jobName,
                             LocalDate hiredOn, LocalDate endedOn) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.jobName = jobName;
        this.hiredOn = hiredOn;
        this.endedOn = endedOn;
    }

    public int employeeId() {
        return employeeId;
    }

    public String employeeName() {
        return employeeName;
    }

    public String jobName() {
        return jobName;
    }

    /**
     * Whether this employee was on the books that day.
     * <p>
     * The grid greys out the rest, because the service refuses them anyway - and a cell you
     * can click that is then refused teaches nothing.
     */
    public boolean isEmployedOn(LocalDate date) {
        if (hiredOn != null && date.isBefore(hiredOn)) {
            return false;
        }
        return endedOn == null || !date.isAfter(endedOn);
    }

    public AttendanceStatus statusOn(int dayOfMonth) {
        return statuses.get(dayOfMonth);
    }

    public Integer leaveTypeOn(int dayOfMonth) {
        return leaveTypes.get(dayOfMonth);
    }

    public BigDecimal hoursOn(int dayOfMonth) {
        return hours.getOrDefault(dayOfMonth, BigDecimal.ZERO);
    }

    public boolean isChanged(int dayOfMonth) {
        return changed.getOrDefault(dayOfMonth, false);
    }

    public boolean hasChanges() {
        return changed.containsValue(true);
    }

    /** Loads a day as the database has it. Loading is not a change. */
    public void load(int dayOfMonth, AttendanceStatus status, Integer leaveTypeId,
                     BigDecimal dayHours) {
        statuses.put(dayOfMonth, status);
        if (leaveTypeId != null) {
            leaveTypes.put(dayOfMonth, leaveTypeId);
        } else {
            leaveTypes.remove(dayOfMonth);
        }
        hours.put(dayOfMonth, dayHours == null ? BigDecimal.ZERO : dayHours);
        changed.remove(dayOfMonth);
    }

    /** Paints a day. Painting what is already there is not a change either. */
    public void set(int dayOfMonth, AttendanceStatus status, Integer leaveTypeId,
                    BigDecimal dayHours) {
        AttendanceStatus before = statuses.get(dayOfMonth);
        Integer typeBefore = leaveTypes.get(dayOfMonth);
        BigDecimal hoursBefore = hours.get(dayOfMonth);

        statuses.put(dayOfMonth, status);
        if (status != null && status.needsLeaveType()) {
            leaveTypes.put(dayOfMonth, leaveTypeId);
        } else {
            leaveTypes.remove(dayOfMonth);
        }
        BigDecimal newHours = status != null && status.isWorked() && dayHours != null
                ? dayHours : BigDecimal.ZERO;
        hours.put(dayOfMonth, newHours);

        boolean same = before == status
                && java.util.Objects.equals(typeBefore, leaveTypes.get(dayOfMonth))
                && (hoursBefore != null && hoursBefore.compareTo(newHours) == 0);
        if (!same) {
            changed.put(dayOfMonth, true);
        }
    }

    /** The days this row has actually changed, so a save writes those and nothing else. */
    public Map<Integer, AttendanceStatus> changedDays() {
        Map<Integer, AttendanceStatus> result = new HashMap<>();
        for (Map.Entry<Integer, Boolean> entry : changed.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                result.put(entry.getKey(), statuses.get(entry.getKey()));
            }
        }
        return result;
    }

    public void clearChanges() {
        changed.clear();
    }

    /** The three figures the payroll would read, computed from what is on the row right now. */
    public AttendanceSummary summary() {
        java.util.List<AttendanceDay> days = new java.util.ArrayList<>();
        for (Map.Entry<Integer, AttendanceStatus> entry : statuses.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            days.add(new AttendanceDay(0, employeeId, employeeName, null, entry.getValue(),
                    hours.getOrDefault(entry.getKey(), BigDecimal.ZERO),
                    leaveTypes.get(entry.getKey()), null,
                    paidLeave.getOrDefault(leaveTypes.get(entry.getKey()), false), null));
        }
        return AttendanceSummary.of(days);
    }

    /**
     * Which leave types are paid, so the row's own totals agree with what the payroll will
     * read. Supplied by the screen from the loaded types rather than guessed here.
     */
    private final Map<Integer, Boolean> paidLeave = new HashMap<>();

    public void knowPaidLeaveTypes(Map<Integer, Boolean> types) {
        paidLeave.clear();
        if (types != null) {
            paidLeave.putAll(types);
        }
    }
}
