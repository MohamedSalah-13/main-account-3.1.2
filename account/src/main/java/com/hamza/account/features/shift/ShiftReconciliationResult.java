package com.hamza.account.features.shift;

import java.math.BigDecimal;

/** Immutable result of reconciling a shift snapshot, journal and live sources. */
public record ShiftReconciliationResult(
        int shiftId,
        ShiftReconciliationStatus status,
        boolean snapshotPresent,
        boolean ledgerComplete,
        BigDecimal snapshotIncome,
        BigDecimal snapshotOutput,
        BigDecimal ledgerIncome,
        BigDecimal ledgerOutput,
        int sourceMismatchCount,
        int duplicateCreateCount,
        int invalidReasonCount,
        int postCloseEntryCount) {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

    public ShiftReconciliationResult {
        snapshotIncome = money(snapshotIncome);
        snapshotOutput = money(snapshotOutput);
        ledgerIncome = money(ledgerIncome);
        ledgerOutput = money(ledgerOutput);
    }

    public boolean snapshotTotalsMatch() {
        return !snapshotPresent || !ledgerComplete
                || closeEnough(snapshotIncome, ledgerIncome) && closeEnough(snapshotOutput, ledgerOutput);
    }

    static ShiftReconciliationStatus classify(boolean snapshotPresent, boolean ledgerComplete,
                                               BigDecimal snapshotIncome, BigDecimal snapshotOutput,
                                               BigDecimal ledgerIncome, BigDecimal ledgerOutput,
                                               int sourceMismatches, int duplicateCreates,
                                               int invalidReasons, int postCloseEntries) {
        boolean totalsMismatch = snapshotPresent && ledgerComplete
                && (!closeEnough(money(snapshotIncome), money(ledgerIncome))
                || !closeEnough(money(snapshotOutput), money(ledgerOutput)));
        if (totalsMismatch || sourceMismatches > 0 || duplicateCreates > 0
                || invalidReasons > 0 || postCloseEntries > 0) {
            return ShiftReconciliationStatus.BROKEN;
        }
        return snapshotPresent && ledgerComplete
                ? ShiftReconciliationStatus.HEALTHY : ShiftReconciliationStatus.WARNING;
    }

    private static boolean closeEnough(BigDecimal first, BigDecimal second) {
        return first.subtract(second).abs().compareTo(TOLERANCE) <= 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
