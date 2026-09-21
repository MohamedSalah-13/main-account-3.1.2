package com.hamza.account.features.stockopening;

import java.util.List;

/**
 * One page of a warehouse's items, and how many the whole filter matches - counted in SQL over the
 * same {@code WHERE} as the rows ({@link WarehouseOpeningQuery}).
 */
public record WarehouseOpeningPage(List<WarehouseOpeningRow> rows, long items, int page, boolean hasPrevious,
                                   boolean hasNext) {

    public WarehouseOpeningPage {
        rows = List.copyOf(rows);
    }

    /** Cuts the one extra row the query read, and says whether it was there. */
    public static WarehouseOpeningPage of(List<WarehouseOpeningRow> fetched, long items, WarehouseOpeningFilter filter) {
        boolean more = fetched.size() > filter.pageSize();
        List<WarehouseOpeningRow> rows = more ? fetched.subList(0, filter.pageSize()) : fetched;
        return new WarehouseOpeningPage(rows, items, filter.page(), filter.page() > 0, more);
    }
}
