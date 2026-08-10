package com.hamza.account.table;

import com.hamza.account.interfaces.api.DataTable;
import com.hamza.account.openFxml.MainData;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.observer.AppEvent;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.GridPane;

import java.util.List;

public interface TableInterface<T> extends MainData {


    default String titleName() {
        return "";
    }

    default void addToLastPane(GridPane gridPane, ToolBar toolBar) {
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

    /**
     * The publisher whose notifications reload this table. Screens migrated to
     * {@link com.hamza.controlsfx.observer.EventBus} name an event in
     * {@link #refreshOn()} instead and leave this null.
     */
    default Publisher<String> publisherTable() {
        return null;
    }

    /**
     * The event that means this table is out of date, or null where the screen
     * still uses {@link #publisherTable()}. {@code TableController} subscribes and
     * unsubscribes for it.
     */
    default Class<? extends AppEvent> refreshOn() {
        return null;
    }

    /**
     * Whether an event of {@link #refreshOn()} concerns this table. The default
     * accepts every one; a screen showing one side of an event that carries both -
     * customers, where a supplier change arrives too - narrows it here.
     */
    default boolean refreshFor(AppEvent event) {
        return true;
    }

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
