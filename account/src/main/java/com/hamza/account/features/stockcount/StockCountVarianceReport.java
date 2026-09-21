package com.hamza.account.features.stockcount;

import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.table.Columns;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * The variance report as columns of text - one definition for the paper and for the spreadsheet,
 * over the rows {@link StockCountHistoryQuery#VARIANCE} reads. The shape of {@code StockTransferLog}.
 * <p>
 * No totals line: every figure is in one item's own base unit, and a sum down the column adds
 * pieces of soap to kilograms of sugar.
 */
public final class StockCountVarianceReport {

    /** Code, item, unit, counts, surplus, shortage, net - sized for an upright A4. */
    static final float[] WIDTHS = {90, 210, 60, 60, 70, 70, 70};

    private static final String[] HEADER_KEYS = {
            "stocks.transfer.column.code", "item.stockcount.column.item", "item.column.unit",
            "item.stockcount.variance.column.counts", "item.stockcount.kpi.surplus",
            "item.stockcount.kpi.shortage", "item.stockcount.variance.column.net"};

    private StockCountVarianceReport() {
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

    public static List<String[]> rows(List<StockCountVarianceRow> rows) {
        return rows.stream().map(StockCountVarianceReport::row).toList();
    }

    static String[] row(StockCountVarianceRow row) {
        return new String[]{text(row.code()), text(row.itemName()), text(row.unitName()),
                String.valueOf(row.counts()), quantity(row.surplus()), quantity(row.shortage()), quantity(row.net())};
    }

    /** The same rows as a spreadsheet - the file is the paper's columns, not a second choice of them. */
    public static WriteExcelInterface<String[]> spreadsheet(String sheetName, InvoicePdfLayout.Labels labels,
                                                           List<StockCountVarianceRow> rows) {
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

    private static String quantity(double value) {
        return Columns.quantity(BigDecimal.valueOf(value));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
