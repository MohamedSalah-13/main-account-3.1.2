package com.hamza.account.features.shift;

import org.junit.jupiter.api.Test;

import static com.hamza.account.features.shift.CloseBalanceEntry.textAfterReload;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CloseBalanceEntryTest {

    @Test
    void aBlindCountTheCashierTypedSurvivesAReload() {
        assertEquals("1234.5", textAfterReload(true, true, "100.0", "1234.5", null, true));
        assertEquals("1234.5", textAfterReload(true, true, "100.0", "1234.5", "", true));
    }

    @Test
    void aCountTypedAgainstAnotherShiftIsDropped() {
        assertEquals("", textAfterReload(true, true, "100.0", "1234.5", null, false));
        assertEquals("100.0", textAfterReload(true, false, "100.0", "1234.5", null, false));
    }

    @Test
    void blindCloseNeverSuggestsAFigure() {
        assertEquals("", textAfterReload(true, true, "100.0", "", null, true));
        assertEquals("", textAfterReload(true, true, "100.0", "100.0", "100.0", true));
    }

    @Test
    void anOpenCloseSuggestsTheOpeningBalanceUntilTheCashierTypes() {
        assertEquals("100.0", textAfterReload(true, false, "100.0", "", null, true));
        assertEquals("150.0", textAfterReload(true, false, "150.0", "100.0", "100.0", true));
        assertEquals("90.0", textAfterReload(true, false, "100.0", "90.0", "100.0", true));
    }

    @Test
    void aTillThatDoesNotReconcileAlwaysClosesAtItsOpeningBalance() {
        assertEquals("100.0", textAfterReload(false, true, "100.0", "1234.5", null, true));
    }
}
