package com.hamza.controlsfx.table;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

public class AddColumnMix<T, T1> {

    public TableColumn<T, T1> getTableColumn(String columnName, ColumnInterface<T, T1> inventoryInterface) {
        TableColumn<T, T1> itemsTableColumn = new TableColumn<>(columnName);

        var stringCallbackHashMap = inventoryInterface.STRING_CALLBACK_HASH_MAP();
        stringCallbackHashMap.forEach((s, cellDataFeaturesObservableValueCallback) -> {
            itemsTableColumn.getColumns().add(createTableColumn(s, cellDataFeaturesObservableValueCallback));
            itemsTableColumn.setCellValueFactory(cellDataFeaturesObservableValueCallback);
        });
        return itemsTableColumn;
    }

    private TableColumn<T, T1> createTableColumn(String columnName, Callback<TableColumn.CellDataFeatures<T, T1>, ObservableValue<T1>> cellValueFactory) {
        TableColumn<T, T1> column = new TableColumn<>(columnName);
        column.setCellValueFactory(cellValueFactory);
        return column;
    }

}
