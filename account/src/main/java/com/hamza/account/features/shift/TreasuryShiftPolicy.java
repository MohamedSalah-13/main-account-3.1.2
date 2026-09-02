package com.hamza.account.features.shift;

/** Shift behavior for a single treasury. Missing rows are treated as NONE. */
public record TreasuryShiftPolicy(int treasuryId, String treasuryName, ShiftTrackingMode trackingMode) {
    public TreasuryShiftPolicy {
        if (treasuryId <= 0) throw new IllegalArgumentException("treasuryId must be positive");
        treasuryName = treasuryName == null ? "" : treasuryName;
        trackingMode = trackingMode == null ? ShiftTrackingMode.NONE : trackingMode;
    }
}
