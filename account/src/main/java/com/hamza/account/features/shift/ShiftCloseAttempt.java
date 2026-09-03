package com.hamza.account.features.shift;

/** Result of a cashier close: final now, or frozen awaiting a second person. */
public record ShiftCloseAttempt(int shiftId, boolean pendingApproval) {
    public static ShiftCloseAttempt closed(int shiftId) {
        return new ShiftCloseAttempt(shiftId, false);
    }

    public static ShiftCloseAttempt pending(int shiftId) {
        return new ShiftCloseAttempt(shiftId, true);
    }
}
