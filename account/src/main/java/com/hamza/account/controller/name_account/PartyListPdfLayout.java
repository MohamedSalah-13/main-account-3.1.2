package com.hamza.account.controller.name_account;

import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.util.List;

/**
 * A snapshot of the party list exactly as the operator chose to see it.
 *
 * <p>It reads only visible data columns in their current order. Selection checkboxes and row
 * buttons are screen controls, not report data, so neither can appear in a PDF.</p>
 */
record PartyListPdfLayout(String[] headers, float[] columnWidths, List<String[]> rows) {

    private static final String SELECTION_COLUMN = "party-selection";
    private static final String ACTIONS_COLUMN = "party-actions";
    private static final String OPENING_BALANCE_COLUMN = "party-opening-balance";
    private static final double MINIMUM_PDF_COLUMN_WIDTH = 54;

    static <T> PartyListPdfLayout from(TableView<T> tableView, List<T> data) {
        List<TableColumn<T, ?>> columns = tableView.getVisibleLeafColumns().stream()
                .filter(PartyListPdfLayout::isPrintable)
                .toList();
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
        return new PartyListPdfLayout(headers, widths, rows);
    }

    private static boolean isPrintable(TableColumn<?, ?> column) {
        return !SELECTION_COLUMN.equals(column.getId()) && !ACTIONS_COLUMN.equals(column.getId());
    }

    private static <T> String[] row(List<TableColumn<T, ?>> columns, T row) {
        String[] values = new String[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            TableColumn<T, ?> column = columns.get(index);
            var observable = column.getCellObservableValue(row);
            Object value = observable == null ? null : observable.getValue();
            values[index] = format(column, value);
        }
        return values;
    }

    private static String format(TableColumn<?, ?> column, Object value) {
        if (value == null) {
            return "";
        }
        if (OPENING_BALANCE_COLUMN.equals(column.getId()) && value instanceof Number number) {
            return Columns.money(BigDecimal.valueOf(number.doubleValue()));
        }
        return value.toString();
    }
}
