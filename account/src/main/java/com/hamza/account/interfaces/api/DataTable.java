package com.hamza.account.interfaces.api;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;

public interface DataTable<T> {

    default void getTable(TableView<T> tableView) {
    }

    default List<T> dataList() throws Exception {
        return new ArrayList<>();
    }

    /**
     * The columns {@code TableController} adds before {@link #getTable} runs.
     * Build them with {@code com.hamza.controlsfx.table.Columns} - see rule ق-ل1
     * in {@code docs/new-code-rules.md}. This replaced {@code classForColumn()},
     * which resolved a field by name at run time through {@code PropertyValueFactory};
     * a method reference here is checked by the compiler instead.
     */
    List<TableColumn<T, ?>> columns();

}
