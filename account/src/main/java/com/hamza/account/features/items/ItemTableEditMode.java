package com.hamza.account.features.items;

/**
 * Separates selecting catalog rows from editing the values stored in those rows.
 * JavaFX uses the table's editable flag as a master gate for both operations.
 */
public record ItemTableEditMode(boolean grouped, boolean quickEditRequested, boolean updateAllowed) {

    /** Must stay open so the print-selection checkboxes remain interactive. */
    public boolean tableEditingGateOpen() {
        return true;
    }

    /** Item values are editable only in the ordinary list with quick edit authorized and requested. */
    public boolean valueColumnsEditable() {
        return !grouped && quickEditRequested && updateAllowed;
    }
}
