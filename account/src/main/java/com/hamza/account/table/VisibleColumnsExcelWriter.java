package com.hamza.account.table;

import com.hamza.controlsfx.excel.WriteExcelInterface;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * A spreadsheet in the columns the table is showing, in their order.
 *
 * <p>A writer with its own fixed list of columns exports what somebody decided once, whatever
 * the user has since hidden or shown; this one is built from the table as it stands, the way
 * the PDF is. The rows are whatever the caller passes - the whole filtered set, never the page -
 * and are read through {@link VisibleColumns#value} so a cell holds the column's own value.</p>
 */
public record VisibleColumnsExcelWriter<T>(String sheetName, List<TableColumn<T, ?>> columns,
                                           List<T> itemsList) implements WriteExcelInterface<T> {

    public VisibleColumnsExcelWriter {
        columns = List.copyOf(columns);
        itemsList = List.copyOf(itemsList);
    }

    /** Captures the table's visible columns now, on the JavaFX thread. */
    public static <T> VisibleColumnsExcelWriter<T> of(String sheetName, TableView<T> tableView,
                                                      Set<String> screenOnlyColumnIds, List<T> rows) {
        return new VisibleColumnsExcelWriter<>(sheetName,
                VisibleColumns.dataColumns(tableView.getVisibleLeafColumns(), screenOnlyColumnIds), rows);
    }

    @Override
    public @NotNull Object[] columnHeader() {
        return columns.stream().map(TableColumn::getText).toArray();
    }

    @Override
    public @NotNull Object[] dataRow(T row) {
        Object[] cells = new Object[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            Object value = VisibleColumns.value(columns.get(index), row);
            cells[index] = value == null ? "" : value;
        }
        return cells;
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }
}
