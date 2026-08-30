package com.hamza.account.features.stocktransfer;

import java.time.LocalDate;

/**
 * One line of a transfer, for the printed transfer log - a level below {@link StockTransferSummary}.
 * <p>
 * JasperReports reads its data source by JavaBean getter (Apache Commons BeanUtils,
 * which predates records and does not recognize a record's own bare accessor), so
 * {@code reports/ar/stock-transfer-history-A4.jrxml}'s fields need the {@code getXxx()}
 * forms below - the same reason {@code InventoryRow} carries them. Caught by
 * {@code WarehouseReportsFillTest}, which actually fills the template rather than only
 * compiling it.
 */
public record StockTransferReportRow(int transferId, LocalDate transferDate, String fromStockName,
                                     String toStockName, String itemName, String unitName, double quantity) {

    public int getTransferId() {
        return transferId;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public String getFromStockName() {
        return fromStockName;
    }

    public String getToStockName() {
        return toStockName;
    }

    public String getItemName() {
        return itemName;
    }

    public String getUnitName() {
        return unitName;
    }

    public double getQuantity() {
        return quantity;
    }
}
