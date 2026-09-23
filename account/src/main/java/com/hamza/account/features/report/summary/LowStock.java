package com.hamza.account.features.report.summary;

import java.util.List;

/** The lowest few items, and how many there are in all - so "5 of 38" is said rather than implied. */
public record LowStock(int count, List<LowStockItem> items) {

    public static final LowStock NONE = new LowStock(0, List.of());

    public LowStock {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
