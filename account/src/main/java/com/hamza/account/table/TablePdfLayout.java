package com.hamza.account.table;

import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * A snapshot of a list exactly as the operator chose to see it, ready to be printed.
 *
 * <p>It reads only the visible data columns in their current order, so hiding a column from the
 * view menu takes it off the paper as well as off the screen. Selection boxes and row buttons are
 * screen controls, not report data, so a caller names them and neither can appear in a PDF.</p>
 *
 * <p>Written for the parties list as {@code PartyListPdfLayout} and lifted out of it when the items
 * list asked for the same print, so the two cannot come to print differently. {@link TablePdfReport}
 * is the other half - choosing the file and writing it.</p>
 *
 * <p>An amount is written the way {@code Columns.money} writes it on screen - a {@code BigDecimal}'s
 * own {@code toString} would print {@code 2905.00} beside a screen that reads {@code 2,905.00}. A
 * column holding a plain {@code Number} is printed as it is unless {@link NumberFormats} names it: a
 * price held as a {@code double} is money and a balance is a quantity, and nothing in the value says
 * which. A totals line is optional and names its columns: summing every amount would add up credit
 * limits, which is a number with no meaning.</p>
 */
public record TablePdfLayout(String[] headers, float[] columnWidths, List<String[]> rows, String[] totals) {

    private static final double MINIMUM_PDF_COLUMN_WIDTH = 54;

    /**
     * Which {@code Number} columns are money and which are quantities.
     * <p>
     * An integer code in neither set stays {@code 164} - written as a quantity it would come out
     * {@code 1,640} for a code of four digits, which is not how anybody writes an id.
     */
    public record NumberFormats(Set<String> moneyColumnIds, Set<String> quantityColumnIds) {

        public static final NumberFormats AS_IS = new NumberFormats(Set.of(), Set.of());

        public NumberFormats {
            moneyColumnIds = Set.copyOf(moneyColumnIds);
            quantityColumnIds = Set.copyOf(quantityColumnIds);
        }
    }

    /** A list with its screen-only columns left out, and no totals line. */
    public static <T> TablePdfLayout from(TableView<T> tableView, List<T> data, Set<String> screenOnlyColumnIds) {
        return from(tableView, data, screenOnlyColumnIds, Set.of(), null, NumberFormats.AS_IS);
    }

    /**
     * @param screenOnlyColumnIds columns that are controls rather than data
     * @param totalledColumnIds   the amount columns the last line sums; empty for no totals line
     * @param totalLabel          written in the first column of the totals line when that column
     *                            is not itself summed
     */
    public static <T> TablePdfLayout from(TableView<T> tableView, List<T> data,
                                          Set<String> screenOnlyColumnIds,
                                          Set<String> totalledColumnIds, String totalLabel) {
        return from(tableView, data, screenOnlyColumnIds, totalledColumnIds, totalLabel, NumberFormats.AS_IS);
    }

    /** As above, writing the named {@code Number} columns as money or as quantities. */
    public static <T> TablePdfLayout from(TableView<T> tableView, List<T> data,
                                          Set<String> screenOnlyColumnIds,
                                          Set<String> totalledColumnIds, String totalLabel,
                                          NumberFormats formats) {
        return of(tableView.getVisibleLeafColumns(), data, screenOnlyColumnIds,
                totalledColumnIds, totalLabel, formats);
    }

    /** The same, over a list of columns - which is what lets it be tested without a toolkit. */
    public static <T> TablePdfLayout of(List<TableColumn<T, ?>> visibleColumns, List<T> data,
                                        Set<String> screenOnlyColumnIds,
                                        Set<String> totalledColumnIds, String totalLabel) {
        return of(visibleColumns, data, screenOnlyColumnIds, totalledColumnIds, totalLabel, NumberFormats.AS_IS);
    }

    public static <T> TablePdfLayout of(List<TableColumn<T, ?>> visibleColumns, List<T> data,
                                        Set<String> screenOnlyColumnIds,
                                        Set<String> totalledColumnIds, String totalLabel,
                                        NumberFormats formats) {
        List<TableColumn<T, ?>> columns = VisibleColumns.dataColumns(visibleColumns, screenOnlyColumnIds);
        String[] headers = new String[columns.size()];
        float[] widths = new float[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            TableColumn<T, ?> column = columns.get(index);
            headers[index] = column.getText();
            widths[index] = (float) Math.max(MINIMUM_PDF_COLUMN_WIDTH, column.getWidth());
        }

        List<String[]> rows = data.stream()
                .map(row -> row(columns, row, formats))
                .toList();
        return new TablePdfLayout(headers, widths, rows,
                totals(columns, data, totalledColumnIds, totalLabel));
    }

    private static <T> String[] totals(List<TableColumn<T, ?>> columns, List<T> data,
                                       Set<String> totalledColumnIds, String totalLabel) {
        if (columns.stream().noneMatch(column -> VisibleColumns.named(totalledColumnIds, column))) {
            return null;
        }
        String[] cells = new String[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            TableColumn<T, ?> column = columns.get(index);
            if (!VisibleColumns.named(totalledColumnIds, column)) {
                cells[index] = "";
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (T row : data) {
                if (VisibleColumns.value(column, row) instanceof BigDecimal amount) {
                    sum = sum.add(amount);
                }
            }
            cells[index] = Columns.money(sum);
        }
        if (!VisibleColumns.named(totalledColumnIds, columns.getFirst())) {
            cells[0] = totalLabel == null ? "" : totalLabel;
        }
        return cells;
    }

    private static <T> String[] row(List<TableColumn<T, ?>> columns, T row, NumberFormats formats) {
        String[] values = new String[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            TableColumn<T, ?> column = columns.get(index);
            values[index] = format(column, VisibleColumns.value(column, row), formats);
        }
        return values;
    }

    private static String format(TableColumn<?, ?> column, Object value, NumberFormats formats) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return Columns.money(amount);
        }
        if (value instanceof Number number) {
            if (VisibleColumns.named(formats.moneyColumnIds(), column)) {
                return Columns.money(BigDecimal.valueOf(number.doubleValue()));
            }
            if (VisibleColumns.named(formats.quantityColumnIds(), column)) {
                return Columns.quantity(BigDecimal.valueOf(number.doubleValue()));
            }
        }
        return value.toString();
    }
}
