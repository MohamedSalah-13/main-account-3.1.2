package com.hamza.account.features.stocktransfer;

import java.util.List;
import java.util.Objects;

/**
 * One posted transfer as it is stored - its header, who entered it and every line - read again by
 * its id for the slip rather than taken from the list on screen, so the paper says what the
 * database says on the machine that prints it.
 */
public record StockTransferSlip(StockTransferSummary header, List<StockTransferLineRow> lines) {

    public StockTransferSlip {
        Objects.requireNonNull(header, "header");
        lines = List.copyOf(lines);
    }
}
