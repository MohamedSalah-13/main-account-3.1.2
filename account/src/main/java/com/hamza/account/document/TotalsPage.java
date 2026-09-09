package com.hamza.account.document;

import java.util.List;

/**
 * One page of a totals search, and how many documents the search matched in all.
 *
 * <p>The money is deliberately not here. Summing the profit of a whole result means
 * reading every line of every matching document - measured at 3.2 seconds for an
 * unfiltered 101,000-invoice history, against 0.14 for this page and 0.25 for its count.
 * Tying them together would make every search as slow as its slowest part, so
 * {@code summarize} is a separate call the screen makes after the rows are already
 * on it.</p>
 *
 * @param rows      the documents on this page, newest first
 * @param totalRows how many documents matched, of which this page is a slice
 * @param page      zero-based index of this page
 * @param pageSize  how many rows a full page holds
 */
public record TotalsPage<T>(List<T> rows, int totalRows, int page, int pageSize) {

    public TotalsPage {
        rows = List.copyOf(rows);
    }

    public static <T> TotalsPage<T> empty(int pageSize) {
        return new TotalsPage<>(List.of(), 0, 0, pageSize);
    }

    /** At least one - an empty result still has a first page to show. */
    public int pageCount() {
        return Math.max(1, (totalRows + pageSize - 1) / pageSize);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < pageCount();
    }
}
