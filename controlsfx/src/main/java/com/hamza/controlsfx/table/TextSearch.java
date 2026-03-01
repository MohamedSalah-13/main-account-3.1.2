package com.hamza.controlsfx.table;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

public class TextSearch {

    private TextSearch() {
    }

    public static <T> void searchTableFromExitedText(TableView<T> tTableView, String text, List<T> tList) {
        ObservableList<T> list = FXCollections.observableArrayList(tList);
        ObservableList<T> tableList = FXCollections.observableArrayList();
        ObservableList<TableColumn<T, ?>> col = tTableView.getColumns();
        for (T t : list) {
            for (TableColumn<T, ?> column : col) {
                if (column.getCellData(t) != null) {
                    String cellValue = column.getCellData(t).toString().toLowerCase();
                    if (cellValue.isEmpty()) continue;
                    if (cellValue.contains(text.toLowerCase()) || cellValue.startsWith(text.toLowerCase())) {
                        tableList.add(t);
                        break;
                    }
                }
            }
        }
        tTableView.setItems(tableList);
    }
}
