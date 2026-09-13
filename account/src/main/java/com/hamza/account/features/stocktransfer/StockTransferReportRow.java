package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;

/**
 * One line of a transfer, for the printed transfer log - a level below {@link StockTransferSummary}.
 * <p>
 * The PDF report reads this record directly, preserving one row for each moved item.
 */
public record StockTransferReportRow(int transferId, LocalDate transferDate, String fromStockName,
                                     String toStockName, String itemName, String unitName, double quantity) {
}
