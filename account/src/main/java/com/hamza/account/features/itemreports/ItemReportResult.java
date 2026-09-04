package com.hamza.account.features.itemreports;

import java.util.List;

/**
 * The answer any item report gives, in the one shape the screen knows how to draw.
 * <p>
 * This is what makes a new report a single class rather than a new screen: a report says
 * what its columns are and hands back rows, and the table, the totals strip, the printing
 * and the Excel export are written once against this record. There is no per-report FXML,
 * no per-report model and no per-report column wiring to forget.
 *
 * @param columns what the table shows, left to right
 * @param rows    the rows in the order they are to be displayed; already sorted by the report
 * @param totals  the strip under the table - a label and a figure each, in display order.
 *                Empty when a report has nothing meaningful to total, which is a real
 *                answer: summing a list of prices produces a number no one can use.
 */
public record ItemReportResult(List<ItemReportColumn> columns,
                               List<ItemReportRow> rows,
                               List<Total> totals) {

    /** A figure under the table, already formatted by the report that knows its unit. */
    public record Total(String labelKey, String value) {
    }

    public static ItemReportResult of(List<ItemReportColumn> columns, List<ItemReportRow> rows) {
        return new ItemReportResult(columns, rows, List.of());
    }

    public static ItemReportResult of(List<ItemReportColumn> columns, List<ItemReportRow> rows,
                                      List<Total> totals) {
        return new ItemReportResult(columns, rows, totals);
    }

    /** How many rows carry an item, as opposed to a heading or a subtotal. */
    public long itemRowCount() {
        return rows.stream().filter(row -> row.kind() == ItemReportRow.Kind.ITEM).count();
    }
}
