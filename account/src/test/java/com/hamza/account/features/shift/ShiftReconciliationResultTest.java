package com.hamza.account.features.shift;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftReconciliationResultTest {

    @Test
    void completeMatchingSnapshotIsHealthy() {
        assertEquals(ShiftReconciliationStatus.HEALTHY, classify(true, true, "10", "3", "10", "3", 0));
    }

    @Test
    void openAndLegacyShiftsNeedReviewWithoutClaimingCorruption() {
        assertEquals(ShiftReconciliationStatus.WARNING, classify(false, false, "0", "0", "4", "1", 0));
        assertEquals(ShiftReconciliationStatus.WARNING, classify(true, false, "12", "2", "3", "7", 0));
    }

    @Test
    void anyAccountingMismatchIsBroken() {
        assertEquals(ShiftReconciliationStatus.BROKEN, classify(true, true, "10", "3", "9", "3", 0));
        assertEquals(ShiftReconciliationStatus.BROKEN, classify(true, true, "10", "3", "10", "3", 1));
    }

    @Test
    void decimalScaleDoesNotCreateAFalseMismatch() {
        ShiftReconciliationResult result = new ShiftReconciliationResult(1, ShiftReconciliationStatus.HEALTHY,
                true, true, money("10.0000"), money("2.00"), money("10"), money("2"), 0, 0, 0, 0);
        assertTrue(result.snapshotTotalsMatch());
    }

    private static ShiftReconciliationStatus classify(boolean present, boolean complete,
                                                       String snapshotIn, String snapshotOut,
                                                       String ledgerIn, String ledgerOut, int sourceMismatches) {
        return ShiftReconciliationResult.classify(present, complete,
                money(snapshotIn), money(snapshotOut), money(ledgerIn), money(ledgerOut),
                sourceMismatches, 0, 0, 0);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
