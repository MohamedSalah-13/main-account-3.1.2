package com.hamza.account.table;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one decision {@link TableSetting} makes about a width, pinned away from the control.
 *
 * <p>It is a direction, and a direction is the easy thing to get backwards: written the other
 * way round this stores the width JavaFX computed from the window and calls it the user's
 * choice, which is exactly the defect it exists to prevent - and the only way to see that on
 * screen is to move a table into a narrower place and watch its columns overflow.
 */
class TableSettingTest {

    @Test
    void aWidthTheLayoutComputedIsNotWorthRemembering() {
        assertFalse(TableSetting.widthIsAPreference(true));
    }

    @Test
    void aWidthTheUserDraggedIs() {
        assertTrue(TableSetting.widthIsAPreference(false));
    }

    @Test
    void aFillingTableOpensOnWhatItsColumnsDeclare() {
        // 1613 points of stored width across seven columns, into a 690-point panel.
        assertEquals(95, TableSetting.widthToApply(225, 95, true));
    }

    @Test
    void anUnconstrainedTableOpensOnWhatWasStored() {
        assertEquals(225, TableSetting.widthToApply(225, 95, false));
    }
}
