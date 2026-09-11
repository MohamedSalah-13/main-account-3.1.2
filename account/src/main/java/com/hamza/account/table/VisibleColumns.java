package com.hamza.account.table;

import javafx.scene.control.TableColumn;

import java.util.List;
import java.util.Set;

/**
 * What a report of a table carries: the columns on screen, in their order, minus the controls.
 *
 * <p>The PDF ({@code PartyListPdfLayout}) and the spreadsheet ({@link VisibleColumnsExcelWriter})
 * both read a table through here, so hiding a column from the view menu takes it off the screen,
 * the paper and the file at once - three outputs, one set of columns.</p>
 */
public final class VisibleColumns {

    private VisibleColumns() {
    }

    /**
     * @param visibleColumns      the table's visible leaf columns, in display order
     * @param screenOnlyColumnIds columns that are controls rather than data - row buttons, the
     *                            selection box
     */
    public static <T> List<TableColumn<T, ?>> dataColumns(List<TableColumn<T, ?>> visibleColumns,
                                                           Set<String> screenOnlyColumnIds) {
        return visibleColumns.stream()
                .filter(column -> !named(screenOnlyColumnIds, column))
                .toList();
    }

    /** Set.of refuses contains(null) with an exception, and a column need not carry an id. */
    public static boolean named(Set<String> ids, TableColumn<?, ?> column) {
        return column.getId() != null && ids.contains(column.getId());
    }

    /**
     * A row's value in a column, through the column's own value factory - not
     * {@code getCellObservableValue}, which answers null for every row of a column not yet placed
     * in a table: a silent empty report rather than an error.
     */
    public static <T, V> Object value(TableColumn<T, V> column, T row) {
        var factory = column.getCellValueFactory();
        if (factory == null) {
            return null;
        }
        var observable = factory.call(new TableColumn.CellDataFeatures<>(column.getTableView(), column, row));
        return observable == null ? null : observable.getValue();
    }
}
