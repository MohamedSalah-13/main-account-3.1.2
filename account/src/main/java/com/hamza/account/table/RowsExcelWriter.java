package com.hamza.account.table;

import com.hamza.controlsfx.excel.WriteExcelInterface;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A spreadsheet of rows a screen has already written - for a report that is more than one table, such as
 * a statement above its rows or reasons above items, put on one sheet with a blank row between them.
 * {@link VisibleColumnsExcelWriter} is the writer for a single table.
 *
 * @param headers the first row
 * @param rows    every row after it, each as long as it needs to be
 */
public record RowsExcelWriter(String sheetName, Object[] headers, List<Object[]> rows)
        implements WriteExcelInterface<Object[]> {

    public RowsExcelWriter {
        headers = headers.clone();
        rows = List.copyOf(rows);
    }

    /**
     * A table's visible columns as rows for such a sheet: their titles, then each row's values - read the
     * way {@link VisibleColumnsExcelWriter} reads them, so a table exported alone or beside another is
     * written the same. Call it on the JavaFX thread: it reads the table's columns.
     */
    public static <T> List<Object[]> tableRows(TableView<T> tableView, Set<String> screenOnlyColumnIds,
                                               List<T> rows) {
        List<TableColumn<T, ?>> columns =
                VisibleColumns.dataColumns(tableView.getVisibleLeafColumns(), screenOnlyColumnIds);
        List<Object[]> sheet = new ArrayList<>();
        sheet.add(columns.stream().map(TableColumn::getText).toArray());
        for (T row : rows) {
            Object[] cells = new Object[columns.size()];
            for (int index = 0; index < columns.size(); index++) {
                Object value = VisibleColumns.value(columns.get(index), row);
                cells[index] = value == null ? "" : value;
            }
            sheet.add(cells);
        }
        return sheet;
    }

    @Override
    public @NotNull Object[] columnHeader() {
        return headers.clone();
    }

    @Override
    public @NotNull Object[] dataRow(Object[] row) {
        return row;
    }

    @Override
    public @NotNull List<Object[]> itemsList() {
        return rows;
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }

    @Override
    public @NotNull String sheetName() {
        return sheetName;
    }
}
