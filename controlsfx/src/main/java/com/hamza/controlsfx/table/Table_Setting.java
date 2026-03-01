package com.hamza.controlsfx.table;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.util.List;

public class Table_Setting {

    private Table_Setting() {
    }

    public static <E> TableView<E> createTable(List<Column<?>> columns, List<E> data) {
        TableView<E> table = new TableView<>();
        return createTable(table, columns, data);
    }

    public static <E> TableView<E> createTable(TableView<E> table, List<Column<?>> columns, List<E> data) {
        ObservableList<E> addresses = FXCollections.observableArrayList(data);
        for (Column<?> column : columns) {
            table.getColumns().add(createColumn(column));
        }
        table.setItems(addresses);
        return table;
    }

    public static <E, C> TableColumn<E, C> createColumn(Column<?> column, C type) {
        TableColumn<E, C> tableColumn = new TableColumn<E, C>(column.getTitle());
        tableColumn.setCellValueFactory(new PropertyValueFactory<>(column.getFieldName()));
        return tableColumn;
    }

    public static <E> TableColumn<E, ?> createColumn(Column<?> column) {
        return switch (column.getType().getCanonicalName()) {
            case "java.lang.Integer" -> createColumn(column, Integer.class);
            case "java.lang.Double" -> createColumn(column, Double.class);
            default -> createColumn(column, String.class);
        };
    }


    public static <T> TableColumn<T, T> column_number() {
        TableColumn<T, T> numberCol = new TableColumn<>("#");
        numberCol.setMinWidth(20);
        numberCol.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue()));

        numberCol.setCellFactory(new Callback<>() {
            @Override
            public TableCell<T, T> call(TableColumn<T, T> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(T item, boolean empty) {
                        super.updateItem(item, empty);
                        setAlignment(Pos.CENTER);
                        if (this.getTableRow() != null && item != null) {
                            setText(String.valueOf(this.getTableRow().getIndex() + 1));
                        } else {
                            setText("");
                        }
                    }
                };
            }
        });
        numberCol.setSortable(false);
        return numberCol;
    }
}
