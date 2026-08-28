package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;

/** One row of transfer history, for the screen that lists and reverses them. */
public record StockTransferSummary(int id, LocalDate transferDate, String fromStockName, String toStockName,
                                   int lineCount) {
}
