package com.hamza.account.features.employee.payroll;

/**
 * Where a payroll run is in its life, and which move from here is legal.
 * <p>
 * {@code DRAFT -> APPROVED -> PAID}, with {@code CANCELLED} reachable from {@code DRAFT}
 * alone. The order is not decoration: <b>approval is what writes the entitlements into the
 * employees' ledgers</b>, so everything before it is a working document and everything after
 * it is a record. That is why {@code R__triggers.sql} freezes a line the moment its run
 * leaves {@code DRAFT}, and why a run that has been approved can never be cancelled - the
 * rows it wrote are already somebody's balance.
 * <p>
 * The transitions live here rather than in the service so they can be tested without a
 * database, and so the screen and the service read the same answer rather than each writing
 * an {@code if}.
 * <p>
 * The name is the stored value ({@code payroll_run.status}, pinned by a CHECK in V59), and
 * the screen shows {@link #messageKey()}. <b>Nothing compares a translated label</b> - the
 * {@code MovementLabel} lesson.
 */
public enum PayrollRunStatus {

    /** Being worked on: lines may be added, edited and removed, and the run may be dropped. */
    DRAFT("payroll.status.draft"),

    /** Frozen, and written into the ledgers. What is left is to hand over the cash. */
    APPROVED("payroll.status.approved"),

    /** The cash left the till, as one expense row per employee. */
    PAID("payroll.status.paid"),

    /** Abandoned while still a draft. It wrote nothing anywhere. */
    CANCELLED("payroll.status.cancelled");

    private final String messageKey;

    PayrollRunStatus(String messageKey) {
        this.messageKey = messageKey;
    }

    /** The key the screen translates for display. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    /** Whether the lines may still be written. The single answer every disabled control hangs off. */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /** Whether this run may move to {@code next}. */
    public boolean mayMoveTo(PayrollRunStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            case DRAFT -> next == APPROVED || next == CANCELLED;
            case APPROVED -> next == PAID;
            case PAID, CANCELLED -> false;
        };
    }

    /**
     * The status stored under this name.
     *
     * @throws IllegalArgumentException with the value in the message - a bare failure inside a
     *                                 row mapper names nothing findable.
     */
    public static PayrollRunStatus of(String stored) {
        if (stored != null) {
            for (PayrollRunStatus status : values()) {
                if (status.name().equals(stored)) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Unknown payroll run status: " + stored);
    }
}
