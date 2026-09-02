package com.hamza.account.features.shift;

import java.math.BigDecimal;

/** One source's current cash effect; amounts are unsigned sides of the ledger. */
public record ShiftCashEffect(
        ShiftCashSource source,
        int sourceId,
        int treasuryId,
        Integer originalShiftId,
        BigDecimal income,
        BigDecimal output) {

    public ShiftCashEffect {
        if (source == null) throw new IllegalArgumentException("source is required");
        income = income == null ? BigDecimal.ZERO : income;
        output = output == null ? BigDecimal.ZERO : output;
    }

    public static ShiftCashEffect incoming(ShiftCashSource source, int sourceId, int treasuryId,
                                           Integer originalShiftId, BigDecimal amount) {
        return new ShiftCashEffect(source, sourceId, treasuryId, originalShiftId,
                amount, BigDecimal.ZERO);
    }

    public static ShiftCashEffect outgoing(ShiftCashSource source, int sourceId, int treasuryId,
                                           Integer originalShiftId, BigDecimal amount) {
        return new ShiftCashEffect(source, sourceId, treasuryId, originalShiftId,
                BigDecimal.ZERO, amount);
    }
}
