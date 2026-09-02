package com.hamza.account.features.shift;

/** Server-side filters for one shift's journal. */
public record ShiftCashLedgerFilter(
        int shiftId,
        ShiftLedgerAction action,
        ShiftCashSource source,
        Integer sourceId,
        int limit) {

    public static final int DEFAULT_LIMIT = 2_000;

    public ShiftCashLedgerFilter {
        if (shiftId <= 0) throw new IllegalArgumentException("shiftId must be positive");
        if (sourceId != null && sourceId <= 0) sourceId = null;
        if (limit <= 0 || limit > 10_000) limit = DEFAULT_LIMIT;
    }

    public static ShiftCashLedgerFilter forShift(int shiftId) {
        return new ShiftCashLedgerFilter(shiftId, null, null, null, DEFAULT_LIMIT);
    }
}
