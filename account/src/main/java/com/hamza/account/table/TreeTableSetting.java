package com.hamza.account.table;

import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.util.function.Predicate;

public class TreeTableSetting {


    public static <T> void initializeColumnCellFactoryInteger(int index, TreeTableView<T> treeView) {
        setColumnCellFactory(index, treeView, (Integer item) -> item == 0);
    }

    public static <T> void initializeColumnCellFactory(int index, TreeTableView<T> treeView) {
        setColumnCellFactory(index, treeView, (Double item) -> item == 0);
    }

    private static <T, N extends Number> void setColumnCellFactory(int index, TreeTableView<T> treeView,
                                                                   Predicate<N> shouldHideValue) {
        TreeTableColumn<T, N> column = (TreeTableColumn<T, N>) treeView.getColumns().get(index);
        column.setCellFactory(column1 -> new TreeTableCell<>() {
            @Override
            protected void updateItem(N item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null || shouldHideValue.test(item) ? "" : String.valueOf(item));
            }
        });
    }
}
