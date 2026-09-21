package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;

/**
 * One line of a transfer, for the printed transfer log and its spreadsheet - a level below
 * {@link StockTransferSummary}. One row for each moved item, in the unit it was entered in.
 *
 * @param code  the item's own barcode
 * @param notes the transfer's note, repeated on each of its lines so a filtered spreadsheet row
 *              still says why the goods moved
 */
public record StockTransferReportRow(int transferId, LocalDate transferDate, String fromStockName,
                                     String toStockName, String code, String itemName, String unitName,
                                     double quantity, String notes) {
}
