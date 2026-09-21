package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;

/**
 * One transfer as the history lists it: its header, how many lines it carries, and who entered it.
 *
 * @param notes     what was written on it ({@code V76}), or {@code null}
 * @param enteredBy the user who posted it, or {@code null} for a row whose user has gone
 */
public record StockTransferSummary(int id, LocalDate transferDate, int fromStockId, String fromStockName,
                                   int toStockId, String toStockName, int lineCount, String notes,
                                   String enteredBy) {
}
