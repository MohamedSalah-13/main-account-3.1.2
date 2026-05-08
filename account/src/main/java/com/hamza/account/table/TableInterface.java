package com.hamza.account.table;

import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.openFxml.MainData;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.List;

public interface TableInterface<T> extends MainData {


    default String titleName() {
        return "";
    }

    default void addToLastPane(GridPane gridPane, HBox hBox, ToolBar toolBar) {
    }

    default void textData(TableView<T> tableView, TextField textField) {
    }

    default ActionButtonToolBar<T> actionButton() {
        return null;
    }

    default DataTable<T> table_data() {
        return null;
    }

    BooleanProperty getColumnSelected(T t);

    Publisher<String> publisherTable();

    default boolean resizeTable() {
        return false;
    }

    UserPermissionType permAdd();

    UserPermissionType permUpdate();

    UserPermissionType permDelete();

    List<T> getProducts(int rowsPerPage, int offset) throws Exception;

    List<T> getFilterItems(String newValue) throws Exception;

    int getCountItems();
}
