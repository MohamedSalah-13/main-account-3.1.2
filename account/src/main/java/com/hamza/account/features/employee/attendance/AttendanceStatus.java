package com.hamza.account.features.employee.attendance;

/**
 * What a day was, as a stored code rather than a sentence.
 * <p>
 * Five, because the payroll asks each of them a different question - and because the two
 * easy collapses are both wrong in money:
 * <ul>
 *   <li>calling a weekend {@link #PRESENT} pays a day's wage for a day nobody worked;</li>
 *   <li>calling it {@link #ABSENT} deducts a rest day the employee is entitled to.</li>
 * </ul>
 * {@link #LEAVE} deliberately does not say whether it is paid: that is
 * {@code leave_type.is_paid}, read by joining rather than copied onto the day, so changing a
 * type's rule corrects the days already recorded instead of leaving two answers in the
 * database.
 * <p>
 * The name is the stored value ({@code attendance.status}, pinned by a CHECK in V60) and the
 * screen shows {@link #messageKey()}. <b>Nothing compares a translated label</b> - the
 * {@code MovementLabel} lesson.
 */
public enum AttendanceStatus {

    /** Worked. Its hours feed an hourly wage; its count feeds a daily one. */
    PRESENT("attendance.status.present"),

    /** Away without leave and without pay. This is what a monthly salary is reduced by. */
    ABSENT("attendance.status.absent"),

    /** On leave. Whether it costs anything is the leave type's answer, not this one. */
    LEAVE("attendance.status.leave"),

    /** The shop's weekly rest. Neither worked nor missed. */
    WEEKEND("attendance.status.weekend"),

    /** A public holiday. Neither worked nor missed. */
    HOLIDAY("attendance.status.holiday");

    private final String messageKey;

    AttendanceStatus(String messageKey) {
        this.messageKey = messageKey;
    }

    /** The key the screen translates. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    /** Whether this day requires a leave type - the CHECK in V60 says the same thing. */
    public boolean needsLeaveType() {
        return this == LEAVE;
    }

    /** Whether the day counts towards a daily wage and carries hours. */
    public boolean isWorked() {
        return this == PRESENT;
    }

    /**
     * The status stored under this name.
     *
     * @throws IllegalArgumentException with the value in the message - a bare failure inside a
     *                                 row mapper names nothing findable.
     */
    public static AttendanceStatus of(String stored) {
        if (stored != null) {
            for (AttendanceStatus status : values()) {
                if (status.name().equals(stored)) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Unknown attendance status: " + stored);
    }
}
