package com.hamza.account.features.inventory;

import java.util.List;

/**
 * What one load of the inventory screen produces: the rows to show, and the totals
 * for the whole filtered set they were taken from.
 * <p>
 * The two travel together because they are answered from the same filter in the same
 * background task. Handing them to the screen separately is how they get out of step -
 * the rows arrive from one call and the totals from another, and a search that lands
 * in between leaves the header describing a set the table is no longer showing.
 */
public record InventoryPage(List<InventoryRow> rows, InventorySummary summary) {

    public static final InventoryPage EMPTY = new InventoryPage(List.of(), InventorySummary.EMPTY);

    /** How many items match the filter, across every page. */
    public long totalRows() {
        return summary.itemCount();
    }

    /** How many pages of {@code pageSize} that comes to - never fewer than one. */
    public int pageCount(int pageSize) {
        if (pageSize < 1) {
            return 1;
        }
        return (int) Math.max(1, (totalRows() + pageSize - 1) / pageSize);
    }
}
