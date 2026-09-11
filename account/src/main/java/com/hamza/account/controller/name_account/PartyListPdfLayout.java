package com.hamza.account.controller.name_account;

import com.hamza.account.table.VisibleColumns;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * A snapshot of a party list exactly as the operator chose to see it.
 *
 * <p>It reads only visible data columns in their current order, so hiding a column from the
 * view menu takes it off the paper as well as off the screen. Selection checkboxes and row
 * buttons are screen controls, not report data, so neither can appear in a PDF.</p>
 *
 * <p>An amount is written the way {@code Columns.money} writes it on screen - a
 * {@code BigDecimal}'s own {@code toString} would print {@code 2905.00} beside a screen that
 * reads {@code 2,905.00}. A totals line is optional and names its columns: summing every
 * amount would add up credit limits, which is a number with no meaning.</p>
 */
record PartyListPdfLayout(String[] headers, float[] columnWidths, List<String[]> rows, String[] totals) {

    private static final String SELECTION_COLUMN = "party-selection";
    private static final String ACTIONS_COLUMN = "party-actions";
    private static final String OPENING_BALANCE_COLUMN = "party-opening-balance";
    private static final double MINIMUM_PDF_COLUMN_WIDTH = 54;

    /** The parties list: its two screen-only columns, and no totals line. */
    static <T> PartyListPdfLayout from(TableView<T> tableView, List<T> data) {
        return from(tableView, data, Set.of(SELECTION_COLUMN, ACTIONS_COLUMN), Set.of(), null);
    }

    /**
     * @param screenOnlyColumnIds columns that are controls rather than data
     * @param totalledColumnIds   the amount columns the last line sums; empty for no totals line
     * @param totalLabel          written in the first column of the totals line when that column
     *                            is not itself summed
     */
    static <T> PartyListPdfLayout from(TableView<T> tableView, List<T> data,
                                       Set<String> screenOnlyColumnIds,
                                       Set<String> totalledColumnIds, String totalLabel) {
        return of(tableView.getVisibleLeafColumns(), data, screenOnlyColumnIds,
                totalledColumnIds, totalLabel);
    }

    /** The same, over a list of columns - which is what lets it be tested without a toolkit. */
    static <T> PartyListPdfLayout of(List<TableColumn<T, ?>> visibleColumns, List<T> data,
                                     Set<String> screenOnlyColumnIds,
                                     Set<String> totalledColumnIds, String totalLabel) {
        List<TableColumn<T, ?>> columns = VisibleColumns.dataColumns(visibleColumns, screenOnlyColumnIds);
        String[] headers = new String[columns.size()];
        float[] widths = new float[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            TableColumn<T, ?> column = columns.get(index);
            headers[index] = column.getText();
            widths[index] = (float) Math.max(MINIMUM_PDF_COLUMN_WIDTH, column.getWidth());
        }

        List<String[]> rows = data.stream()
                .map(row -> row(columns, row))
                .toList();
        return new PartyListPdfLayout(headers, widths, rows,
                totals(columns, data, totalledColumnIds, totalLabel));
    }

    private static <T> String[] totals(List<TableColumn<T, ?>> columns, List<T> data,
                                       Set<String> totalledColumnIds, String totalLabel) {
        if (columns.stream().noneMatch(column -> named(totalledColumnIds, column))) {
            return null;
        }
        String[] cells = new String[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            TableColumn<T, ?> column = columns.get(index);
            if (!named(totalledColumnIds, column)) {
                cells[index] = "";
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (T row : data) {
                if (value(column, row) instanceof BigDecimal amount) {
                    sum = sum.add(amount);
                }
            }
            cells[index] = Columns.money(sum);
        }
        if (!named(totalledColumnIds, columns.getFirst())) {
            cells[0] = totalLabel == null ? "" : totalLabel;
        }
        return cells;
    }

    private static boolean named(Set<String> ids, TableColumn<?, ?> column) {
        return VisibleColumns.named(ids, column);
    }

    private static <T> String[] row(List<TableColumn<T, ?>> columns, T row) {
        String[] values = new String[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            TableColumn<T, ?> column = columns.get(index);
            values[index] = format(column, value(column, row));
        }
        return values;
    }

    private static <T> Object value(TableColumn<T, ?> column, T row) {
        return VisibleColumns.value(column, row);
    }

    private static String format(TableColumn<?, ?> column, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return Columns.money(amount);
        }
        if (OPENING_BALANCE_COLUMN.equals(column.getId()) && value instanceof Number number) {
            return Columns.money(BigDecimal.valueOf(number.doubleValue()));
        }
        return value.toString();
    }
}
