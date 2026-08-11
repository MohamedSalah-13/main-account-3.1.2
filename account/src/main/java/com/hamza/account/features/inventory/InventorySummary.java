package com.hamza.account.features.inventory;

/**
 * The totals for <em>every</em> item the filter matches, not for the rows on screen.
 * <p>
 * That distinction is the whole reason this record exists. The screen used to add up
 * {@code tableView.getItems()} - the fifty rows of the current page - and label the
 * result "إجمالي قيمة المخزون". A shop with four thousand items saw a number that
 * changed every time it turned a page and never once meant what the label said.
 * These figures come from a single {@code SELECT SUM(...)} over the same
 * {@code WHERE} clause that produced the rows, so they stay true while paging and
 * narrow correctly when the user searches.
 * <p>
 * {@link #itemCount} doubles as the total row count, which is what the pagination is
 * sized from: counting from the same join and the same filter as the rows is what
 * stops the last page from being empty, and what stops an item with no
 * {@code items_stock} row from being counted but never shown.
 */
public record InventorySummary(
        long itemCount,
        double totalQuantity,
        double valueAtCost,
        double valueAtSale,
        long lowStockCount,
        long outOfStockCount,
        long negativeCount) {

    public static final InventorySummary EMPTY = new InventorySummary(0, 0, 0, 0, 0, 0, 0);
}
