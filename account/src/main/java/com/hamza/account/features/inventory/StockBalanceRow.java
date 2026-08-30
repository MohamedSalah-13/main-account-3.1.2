package com.hamza.account.features.inventory;

/**
 * One item's balance in one warehouse, for the cross-warehouse comparison report.
 * <p>
 * JasperReports reads its data source by JavaBean getter (Apache Commons BeanUtils,
 * which predates records and does not recognize a record's own bare accessor), so
 * {@code reports/ar/items-across-stocks-A4.jrxml}'s fields need the {@code getXxx()}
 * forms below - the same reason {@code InventoryRow} carries them. Caught by
 * {@code WarehouseReportsFillTest}, which actually fills the template rather than only
 * compiling it.
 */
public record StockBalanceRow(int itemId, String itemName, String barcode, String stockName, double balance) {

    public int getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getStockName() {
        return stockName;
    }

    public double getBalance() {
        return balance;
    }
}
