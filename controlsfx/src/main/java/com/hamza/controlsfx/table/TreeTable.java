package com.hamza.controlsfx.table;

import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;

import java.util.List;

public class TreeTable {

    public static <E> TreeTableView<E> createTable(TreeTableView<E> table, List<Column<?>> columns) {
        for (Column<?> value : columns) {
            table.getColumns().add(createColumn(value));
        }
        return table;
    }

    public static <E, C> TreeTableColumn<E, C> createColumn(Column<?> column, C type) {
        TreeTableColumn<E, C> tableColumn = new TreeTableColumn<>(column.getTitle());
        tableColumn.setCellValueFactory(new TreeItemPropertyValueFactory<>(column.getFieldName()));
        return tableColumn;
    }

    public static <E> TreeTableColumn<E, ?> createColumn(Column<?> column) {
        return switch (column.getType().getCanonicalName()) {
            case "java.lang.Integer" -> createColumn(column, Integer.class);
            case "java.lang.Double" -> createColumn(column, Double.class);
            default -> createColumn(column, String.class);
        };
    }
}
