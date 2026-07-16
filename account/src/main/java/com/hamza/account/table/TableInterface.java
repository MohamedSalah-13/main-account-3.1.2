package com.hamza.account.table;

import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.openFxml.MainData;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;

public interface TableInterface<T> extends MainData {


    default String titleName() {
        return "";
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

    List<T> getProducts(int rowsPerPage, int offset) throws Exception;

    List<T> getFilterItems(String newValue) throws Exception;

    int getCountItems();
}
