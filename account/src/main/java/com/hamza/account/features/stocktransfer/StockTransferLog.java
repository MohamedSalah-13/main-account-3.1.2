package com.hamza.account.features.stocktransfer;

import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.table.Columns;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * The transfer log as columns of text - one definition for the paper and for the spreadsheet.
 * <p>
 * The screen built the PDF's headers, widths and cells inline, beside a history that printed a date
 * range of its own rather than the list above it. Both now come from here, over the rows
 * {@link StockTransferHistoryQuery#LOG} reads with the list's own {@code WHERE}, so the two files and
 * the table are one set of transfers, and a column added to one is added to both.
 * <p>
 * A quantity is written as it was entered, in the unit beside it, and never summed: a total of
 * cartons and pieces of different items is not a quantity of anything.
 */
public final class StockTransferLog {

    /** Transfer number, date, from, to, code, item, unit, quantity, note - sized for a landscape A4. */
    static final float[] WIDTHS = {55, 80, 120, 120, 100, 200, 70, 70, 170};

    private static final String[] HEADER_KEYS = {
            "stocks.transfer.history.column.id", "stocks.transfer.history.column.date",
            "stocks.transfer.history.column.from", "stocks.transfer.history.column.to",
            "stocks.transfer.column.code", "stocks.transfer.item", "item.column.unit", "quantity",
            "stocks.transfer.notes"};

    private StockTransferLog() {
    }

    public static String[] headers(InvoicePdfLayout.Labels labels) {
        String[] headers = new String[HEADER_KEYS.length];
        for (int index = 0; index < HEADER_KEYS.length; index++) {
            headers[index] = labels.text(HEADER_KEYS[index]);
        }
        return headers;
    }

    public static float[] widths() {
        return WIDTHS.clone();
    }

    public static List<String[]> rows(List<StockTransferReportRow> rows) {
        return rows.stream().map(StockTransferLog::row).toList();
    }

    static String[] row(StockTransferReportRow row) {
        return new String[]{
                String.valueOf(row.transferId()),
                row.transferDate() == null ? "" : row.transferDate().toString(),
                text(row.fromStockName()), text(row.toStockName()), text(row.code()),
                text(row.itemName()), text(row.unitName()),
                Columns.quantity(BigDecimal.valueOf(row.quantity())),
                text(row.notes())};
    }

    /** The same rows as a spreadsheet - the file is the paper's columns, not a second choice of them. */
    public static WriteExcelInterface<String[]> spreadsheet(String sheetName, InvoicePdfLayout.Labels labels,
                                                           List<StockTransferReportRow> rows) {
        String[] headers = headers(labels);
        List<String[]> cells = rows(rows);
        return new WriteExcelInterface<>() {
            @Override
            public @NotNull Object[] columnHeader() {
                return headers.clone();
            }

            @Override
            public @NotNull Object[] dataRow(String[] row) {
                return row.clone();
            }

            @Override
            public @NotNull List<String[]> itemsList() {
                return cells;
            }

            @Override
            public boolean addDataToFile() {
                return true;
            }

            @Override
            public @NotNull String sheetName() {
                return sheetName;
            }
        };
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
