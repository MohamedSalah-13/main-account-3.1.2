package com.hamza.account.features.shift;

import java.time.LocalDateTime;

/** Immutable evidence of a cashier-to-till access change. */
public record CashierTreasuryAssignmentEvent(
        long id,
        int assignmentId,
        int userId,
        String username,
        int treasuryId,
        String treasuryName,
        Action action,
        Boolean beforeCanOpenShift,
        boolean afterCanOpenShift,
        Boolean beforeDefaultTreasury,
        boolean afterDefaultTreasury,
        Boolean beforeActive,
        boolean afterActive,
        int actorUserId,
        String actorUsername,
        LocalDateTime occurredAt) {

    public enum Action {
        MIGRATED,
        ASSIGNED,
        REACTIVATED,
        DEFAULT_CHANGED,
        DEACTIVATED,
        UPDATED
    }
}
