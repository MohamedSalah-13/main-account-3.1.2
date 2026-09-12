package com.hamza.account.features.employee.attendance;

/**
 * Where a leave request stands.
 * <p>
 * {@code PENDING -> APPROVED} or {@code PENDING -> REJECTED}, and nothing after that: a
 * decision that can be taken back is not a decision, and an approved request has already had
 * its days written onto the attendance grid.
 */
public enum LeaveStatus {

    PENDING("leave.status.pending"),
    APPROVED("leave.status.approved"),
    REJECTED("leave.status.rejected");

    private final String messageKey;

    LeaveStatus(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    /** Whether a decision may still be taken on this request. */
    public boolean isOpen() {
        return this == PENDING;
    }

    public static LeaveStatus of(String stored) {
        if (stored != null) {
            for (LeaveStatus status : values()) {
                if (status.name().equals(stored)) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Unknown leave status: " + stored);
    }
}
