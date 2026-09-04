package com.hamza.account.features.shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable cashier declaration, optionally paired with a later receipt. */
public record ShiftCashHandover(
        long id,
        int shiftId,
        int sourceTreasuryId,
        String sourceTreasuryName,
        int targetTreasuryId,
        String targetTreasuryName,
        BigDecimal actualBalance,
        BigDecimal expectedBalance,
        BigDecimal differenceAmount,
        BigDecimal retainedFloat,
        BigDecimal handoverAmount,
        int handedByUserId,
        String handedByUsername,
        LocalDateTime requestedAt,
        Integer receivedByUserId,
        String receivedByUsername,
        LocalDateTime receivedAt,
        Integer treasuryTransferId,
        String receiptNote,
        Integer openingOverrideByUserId,
        String openingOverrideByUsername,
        LocalDateTime openingOverrideAt,
        String openingOverrideReason) {

    public boolean pending() {
        return treasuryTransferId == null;
    }

    public boolean blocksOpening() {
        return pending() && openingOverrideByUserId == null;
    }
}
