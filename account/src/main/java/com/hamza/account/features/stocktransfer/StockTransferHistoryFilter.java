package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Which warehouse transfers the history lists.
 * <p>
 * The history used to be "the last two hundred" and nothing else, so a transfer from March could
 * not be found in September except by scrolling a list that did not reach it - and could not be
 * reversed either, since the reversal is a button on a row of that list. Built on
 * {@code TreasuryHistoryFilter}, which ended the same defect on the treasury side: the checks are
 * in the constructor, so a filter that exists is one the query can run.
 *
 * @param stockId {@code null} for every warehouse; otherwise either end of a transfer - a
 *                storekeeper asking "what moved through my warehouse" means in and out alike
 * @param text    {@code null} or blank for no text; otherwise matched against the note and
 *                against every line's item, by name or by an exact code - "where did this item
 *                go" is the question the history is most often opened to answer
 */
public record StockTransferHistoryFilter(LocalDate from, LocalDate to, Integer stockId, String text,
                                         int page, int pageSize) {

    public static final int PAGE_SIZE = 50;
    /** The same boundary the treasury history and statement print to. */
    public static final int PRINT_LIMIT = 10_000;

    public StockTransferHistoryFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        if (pageSize < 1 || pageSize > PRINT_LIMIT) throw new IllegalArgumentException("invalid page size");
        text = text == null || text.isBlank() ? null : text.strip();
    }

    /** The month so far - what a person opening the history is most likely looking for. */
    public static StockTransferHistoryFilter thisMonth(LocalDate today) {
        return new StockTransferHistoryFilter(today.withDayOfMonth(1), today, null, null, 0, PAGE_SIZE);
    }

    public int offset() {
        return Math.multiplyExact(page, pageSize);
    }

    /** One more than a page, so "is there a next page" costs no second query. */
    public int queryLimit() {
        return pageSize + 1;
    }

    public StockTransferHistoryFilter onPage(int newPage) {
        return new StockTransferHistoryFilter(from, to, stockId, text, newPage, pageSize);
    }

    /** The whole filtered set, for paper and for a file - never the page on screen. */
    public StockTransferHistoryFilter forPrint() {
        return new StockTransferHistoryFilter(from, to, stockId, text, 0, PRINT_LIMIT);
    }
}
