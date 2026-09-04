package com.hamza.account.features.shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Optional close-time handover rule for one cashier till. */
public record ShiftCashHandoverPolicy(
        int sourceTreasuryId,
        String sourceTreasuryName,
        boolean enabled,
        int targetTreasuryId,
        String targetTreasuryName,
        BigDecimal retainedFloat,
        int updatedByUserId,
        String updatedByUsername,
        LocalDateTime updatedAt) {
}
