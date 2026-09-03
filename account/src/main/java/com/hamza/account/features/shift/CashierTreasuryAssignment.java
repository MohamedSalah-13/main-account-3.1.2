package com.hamza.account.features.shift;

import java.time.LocalDateTime;

/** Current cashier-to-till permission, retained when disabled for auditability. */
public record CashierTreasuryAssignment(
        int id,
        int userId,
        String username,
        int treasuryId,
        String treasuryName,
        boolean canOpenShift,
        boolean defaultTreasury,
        boolean active,
        int assignedBy,
        String assignedByUsername,
        LocalDateTime assignedAt,
        int updatedBy,
        String updatedByUsername,
        LocalDateTime updatedAt) {
}
