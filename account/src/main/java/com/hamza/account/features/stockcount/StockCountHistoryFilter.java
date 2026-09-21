package com.hamza.account.features.stockcount;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Which count sheets the history lists, and which posted ones the variance report reads.
 * <p>
 * A posted count could not be seen again at all: {@code StockCountService.recent} and
 * {@code findById} had no caller, so the one document that corrects a balance was the one
 * document with no list, no paper and no report. Built on {@code StockTransferHistoryFilter}:
 * the checks are in the constructor, so a filter that exists is one the query can run.
 *
 * @param stockId {@code null} for every warehouse
 * @param status  {@code null} for drafts and posted sheets alike
 */
public record StockCountHistoryFilter(LocalDate from, LocalDate to, Integer stockId, StockCountStatus status,
                                      int page, int pageSize) {

    public static final int PAGE_SIZE = 50;
    /** The same boundary the other histories print to. */
    public static final int PRINT_LIMIT = 10_000;

    public StockCountHistoryFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        if (pageSize < 1 || pageSize > PRINT_LIMIT) throw new IllegalArgumentException("invalid page size");
    }

    /**
     * The year so far. A shop counts a warehouse a few times a year, not a few times a month, so
     * "this month" - what the transfer history opens on - would usually open on nothing.
     */
    public static StockCountHistoryFilter thisYear(LocalDate today) {
        return new StockCountHistoryFilter(today.withDayOfYear(1), today, null, null, 0, PAGE_SIZE);
    }

    public int offset() {
        return Math.multiplyExact(page, pageSize);
    }

    /** One more than a page, so "is there a next page" costs no second query. */
    public int queryLimit() {
        return pageSize + 1;
    }

    public StockCountHistoryFilter onPage(int newPage) {
        return new StockCountHistoryFilter(from, to, stockId, status, newPage, pageSize);
    }

    /**
     * The same period and warehouse, posted sheets only - what the variance report reads. A draft
     * has moved nothing, and a variance report that counted one would report a shortage the shop
     * never booked.
     */
    public StockCountHistoryFilter postedOnly() {
        return new StockCountHistoryFilter(from, to, stockId, StockCountStatus.POSTED, 0, PRINT_LIMIT);
    }
}
