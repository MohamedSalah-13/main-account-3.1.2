package com.hamza.account.features.stockcount;

import java.util.List;
import java.util.Objects;

/**
 * One count sheet as it is stored - its header, who entered it and every line - read again by its
 * id rather than taken from a list on screen, so its paper says what the database says.
 */
public record StockCountDocument(StockCountSummary header, List<StockCountLine> lines) {

    public StockCountDocument {
        Objects.requireNonNull(header, "header");
        lines = List.copyOf(lines);
    }
}
