package com.hamza.account.features.stockcount;

import java.util.List;

/**
 * One page of the count history, with the totals of the <b>whole</b> filtered set beside it - counted
 * in SQL over the same {@code WHERE} as the rows ({@link StockCountHistoryQuery}). Counts, never
 * quantities: a sheet's lines are different items, and a total of their differences is a number
 * about nothing.
 *
 * @param sheets      how many sheets the filter matches
 * @param lines       how many lines those sheets carry
 * @param differences how many of those lines found a difference
 */
public record StockCountHistoryPage(List<StockCountSummary> rows, long sheets, long lines, long differences,
                                    int page, boolean hasPrevious, boolean hasNext) {

    public StockCountHistoryPage {
        rows = List.copyOf(rows);
    }

    /** Cuts the one extra row the query read, and says whether it was there. */
    public static StockCountHistoryPage of(List<StockCountSummary> fetched, long sheets, long lines,
                                           long differences, StockCountHistoryFilter filter) {
        boolean more = fetched.size() > filter.pageSize();
        List<StockCountSummary> rows = more ? fetched.subList(0, filter.pageSize()) : fetched;
        return new StockCountHistoryPage(rows, sheets, lines, differences, filter.page(), filter.page() > 0, more);
    }
}
