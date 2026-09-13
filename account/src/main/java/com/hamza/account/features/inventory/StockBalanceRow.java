package com.hamza.account.features.inventory;

/**
 * One item's balance in one warehouse, for the cross-warehouse comparison report.
 * <p>
 * The PDF report reads this record directly.
 */
public record StockBalanceRow(int itemId, String itemName, String barcode, String stockName, double balance) {

}
