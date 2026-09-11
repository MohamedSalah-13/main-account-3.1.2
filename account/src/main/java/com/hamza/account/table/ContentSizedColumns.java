package com.hamza.account.table;

import com.hamza.account.config.TableAppearance;
import javafx.collections.ListChangeListener;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Columns as wide as what they hold, rather than stretched to fill the window.
 *
 * <p>Stretching gives a two-digit code the same share of a wide screen as a customer's name,
 * and pushes the figures somebody came to read away from the name they belong to. Here each
 * column is measured against the rows actually loaded, and the unused width stays unused.
 * Written for the parties list and shared with the accounts screen; CLAUDE.md, "Column views
 * and widths", has the adoption steps.</p>
 *
 * <p>A column whose minimum and maximum width are equal is fixed - the row actions and the
 * selection box declare themselves that way - and is left alone. A column this class has
 * sized once is fixed by the same test afterwards, which is why it keeps its own record of
 * which columns it manages.</p>
 */
public final class ContentSizedColumns<T> {

    static final double EMPTY_COLUMN_WIDTH = 10;
    static final double MIN_CONTENT_WIDTH = 62;
    static final double MAX_CONTENT_WIDTH = 260;
    static final double CELL_PADDING = 28;

    private final Set<TableColumn<T, ?>> managed = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Turns the fill-the-width preference off for this one table, whatever the user chose for
     * the rest, and sizes a column again when the view menu shows it - otherwise a column
     * hidden during the first layout would come back at the platform's default width.
     */
    public void install(TableView<T> tableView) {
        TableAppearance.setFillAvailableWidthOverride(tableView, false);
        TableAppearance.apply(tableView);
        tableView.getVisibleLeafColumns().addListener(
                (ListChangeListener<TableColumn<T, ?>>) change -> layout(tableView));
    }

    /** Call after every change of rows: the widths are measured from what is loaded. */
    public void layout(TableView<T> tableView) {
        boolean hasRows = !tableView.getItems().isEmpty();
        for (TableColumn<T, ?> column : tableView.getVisibleLeafColumns()) {
            if (!managed.contains(column) && column.getMinWidth() == column.getMaxWidth()) {
                continue;
            }
            managed.add(column);
            double width = width(textWidth(column.getText()), widestValue(tableView, column),
                    hasRows, hasContent(tableView, column));
            column.setMinWidth(width);
            column.setPrefWidth(width);
            column.setMaxWidth(width);
        }
    }

    /**
     * The width of one column.
     * <p>
     * A column blank on every loaded row becomes a thin divider rather than a share of the
     * spare width. An empty result is different: there is nothing to measure, and collapsing
     * every column to a divider would leave headings nobody can read over a table that says
     * "nothing matched" - so the headings decide.
     */
    static double width(double headerWidth, double widestValue, boolean hasRows, boolean hasContent) {
        if (hasRows && !hasContent) {
            return EMPTY_COLUMN_WIDTH;
        }
        double wanted = Math.max(headerWidth, widestValue) + CELL_PADDING;
        return Math.min(MAX_CONTENT_WIDTH, Math.max(MIN_CONTENT_WIDTH, wanted));
    }

    private double widestValue(TableView<T> tableView, TableColumn<T, ?> column) {
        double widest = 0;
        for (T row : tableView.getItems()) {
            String value = valueOf(column, row);
            if (value != null) {
                widest = Math.max(widest, textWidth(value));
            }
        }
        return widest;
    }

    private boolean hasContent(TableView<T> tableView, TableColumn<T, ?> column) {
        for (T row : tableView.getItems()) {
            if (valueOf(column, row) != null) {
                return true;
            }
        }
        return false;
    }

    private String valueOf(TableColumn<T, ?> column, T row) {
        var observable = column.getCellObservableValue(row);
        Object value = observable == null ? null : observable.getValue();
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static double textWidth(String value) {
        return value == null ? 0 : new Text(value).getLayoutBounds().getWidth();
    }
}
