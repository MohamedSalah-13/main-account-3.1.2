package com.hamza.account.features.stocktransfer;

import java.util.List;

/**
 * One page of the transfer history, with the totals of the <b>whole</b> filtered set beside it.
 * <p>
 * The totals are counted in SQL over the same {@code WHERE} the rows were read with
 * ({@link StockTransferHistoryQuery}), so the footer can never describe other transfers than the
 * table, and never only the fifty on screen. They are counts and not quantities on purpose: a
 * transfer's lines are in different items and different units, and a sum of cartons of juice and
 * pieces of soap is a number that means nothing.
 *
 * @param transfers how many transfers the filter matches
 * @param lines     how many lines those transfers carry
 */
public record StockTransferHistoryPage(List<StockTransferSummary> rows, long transfers, long lines,
                                       int page, boolean hasPrevious, boolean hasNext) {

    public StockTransferHistoryPage {
        rows = List.copyOf(rows);
    }

    /** Cuts the one extra row the query read, and says whether it was there. */
    public static StockTransferHistoryPage of(List<StockTransferSummary> fetched, long transfers, long lines,
                                              StockTransferHistoryFilter filter) {
        boolean more = fetched.size() > filter.pageSize();
        List<StockTransferSummary> rows = more ? fetched.subList(0, filter.pageSize()) : fetched;
        return new StockTransferHistoryPage(rows, transfers, lines, filter.page(), filter.page() > 0, more);
    }
}
