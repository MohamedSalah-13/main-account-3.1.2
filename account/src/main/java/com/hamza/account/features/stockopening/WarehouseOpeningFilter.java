package com.hamza.account.features.stockopening;

/**
 * Which of one warehouse's items the opening-balances screen lists.
 * <p>
 * The checks are in the constructor, so a filter that exists is one the query can run - the shape
 * of {@code StockTransferHistoryFilter} and {@code StockCountHistoryFilter}.
 *
 * @param stockId     the warehouse; the screen is always about one
 * @param text        part of a name, or a whole code (the item's, an extra one or a unit's); {@code null} for every item
 * @param unmovedOnly only the items nothing has moved in this warehouse yet - the ones whose opening can still be entered
 */
public record WarehouseOpeningFilter(int stockId, String text, boolean unmovedOnly, int page, int pageSize) {

    public static final int PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 10_000;

    public WarehouseOpeningFilter {
        if (stockId <= 0) throw new IllegalArgumentException("a warehouse is required");
        text = text == null || text.isBlank() ? null : text.trim();
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new IllegalArgumentException("invalid page size");
    }

    public static WarehouseOpeningFilter firstPage(int stockId, String text, boolean unmovedOnly) {
        return new WarehouseOpeningFilter(stockId, text, unmovedOnly, 0, PAGE_SIZE);
    }

    public int offset() {
        return Math.multiplyExact(page, pageSize);
    }

    /** One more than a page, so "is there a next page" costs no second query. */
    public int queryLimit() {
        return pageSize + 1;
    }

    public WarehouseOpeningFilter onPage(int newPage) {
        return new WarehouseOpeningFilter(stockId, text, unmovedOnly, newPage, pageSize);
    }
}
