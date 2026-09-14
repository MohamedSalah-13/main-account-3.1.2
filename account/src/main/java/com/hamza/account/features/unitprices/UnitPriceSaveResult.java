package com.hamza.account.features.unitprices;

/** How many item rows and unit rows a save wrote. */
public record UnitPriceSaveResult(int itemsWritten, int unitsWritten) {

    public int total() {
        return itemsWritten + unitsWritten;
    }
}
