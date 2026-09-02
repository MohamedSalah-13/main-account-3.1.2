package com.hamza.account.features.shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One immutable row shown by the shift audit screen. */
public record ShiftCashLedgerEntry(
        long id,
        int shiftId,
        Integer originShiftId,
        int treasuryId,
        String treasuryName,
        int actorUserId,
        String actorUsername,
        ShiftCashSource source,
        int sourceId,
        ShiftLedgerAction action,
        BigDecimal incomeDelta,
        BigDecimal outputDelta,
        String reason,
        LocalDateTime occurredAt) {

    public ShiftCashLedgerEntry {
        treasuryName = treasuryName == null ? "" : treasuryName;
        actorUsername = actorUsername == null ? "" : actorUsername;
        incomeDelta = incomeDelta == null ? BigDecimal.ZERO : incomeDelta;
        outputDelta = outputDelta == null ? BigDecimal.ZERO : outputDelta;
        reason = reason == null ? "" : reason;
    }

    /** Signed net effect on the treasury: incoming minus outgoing. */
    public BigDecimal netDelta() {
        return incomeDelta.subtract(outputDelta);
    }
}
