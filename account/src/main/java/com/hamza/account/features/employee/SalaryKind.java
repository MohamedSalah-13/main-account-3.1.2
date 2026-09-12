package com.hamza.account.features.employee;

/**
 * How an employee is paid, as a stored code rather than as a sentence.
 * <p>
 * The four are what {@code employee_compensation.salary_kind} accepts (V57 pins them with a
 * CHECK), and each answers a different question when a month is calculated:
 * {@link #MONTHLY} is a figure the month reduces by absence, {@link #DAILY} and
 * {@link #HOURLY} are figures attendance multiplies, and {@link #COMMISSION} has no basic
 * at all - it is whatever a commission run has approved.
 * <p>
 * The name is the stored value, so the column is readable in a SQL client without a lookup
 * and cannot be broken by a translation. The screen shows {@link #messageKey()};
 * <b>nothing ever compares a translated label</b> - the lesson {@code MovementLabel} records.
 */
public enum SalaryKind {

    /** A monthly figure. The ordinary case, and the default for every row V57 migrates. */
    MONTHLY("employee.salary.kind.monthly"),

    /** A daily wage, multiplied by the days actually worked. */
    DAILY("employee.salary.kind.daily"),

    /** An hourly wage. Needs real hours, so it only means anything once attendance exists. */
    HOURLY("employee.salary.kind.hourly"),

    /** No basic: a delegate paid out of what a commission run approves. */
    COMMISSION("employee.salary.kind.commission");

    private final String messageKey;

    SalaryKind(String messageKey) {
        this.messageKey = messageKey;
    }

    /** The key the screen translates for display. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    /**
     * The kind stored under this name.
     *
     * @throws IllegalArgumentException with the value in the message, because a bare failure
     *                                 inside a row mapper names nothing findable. Same
     *                                 reasoning as {@code TableName.requireById}.
     */
    public static SalaryKind of(String stored) {
        if (stored != null) {
            for (SalaryKind kind : values()) {
                if (kind.name().equals(stored)) {
                    return kind;
                }
            }
        }
        throw new IllegalArgumentException("Unknown salary kind: " + stored);
    }

    /** What a row with no compensation of its own is read as, so a list never shows a blank. */
    public static SalaryKind orDefault(String stored) {
        return stored == null || stored.isBlank() ? MONTHLY : of(stored);
    }
}
