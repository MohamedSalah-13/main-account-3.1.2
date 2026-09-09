package com.hamza.account.features.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTableEditModeTest {

    @Test
    void printSelectionRemainsAvailableWhenQuickEditIsOff() {
        ItemTableEditMode mode = new ItemTableEditMode(false, false, true);

        assertTrue(mode.tableEditingGateOpen());
        assertFalse(mode.valueColumnsEditable());
    }

    @Test
    void quickEditEnablesValueColumnsOnlyForAnAuthorizedOrdinaryList() {
        assertTrue(new ItemTableEditMode(false, true, true).valueColumnsEditable());
        assertFalse(new ItemTableEditMode(false, true, false).valueColumnsEditable());
        assertFalse(new ItemTableEditMode(true, true, true).valueColumnsEditable());
    }

    @Test
    void selectionGateNeverClosesWithTheDataEditingModes() {
        assertTrue(new ItemTableEditMode(false, false, false).tableEditingGateOpen());
        assertTrue(new ItemTableEditMode(false, true, true).tableEditingGateOpen());
        assertTrue(new ItemTableEditMode(true, true, true).tableEditingGateOpen());
    }
}
