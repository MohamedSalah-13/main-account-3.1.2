package com.hamza.account.interfaces.api;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.MenuButton;

import java.util.ArrayList;
import java.util.List;

public interface DataTable<T> {

    default void getTable(TableView<T> tableView) {
    }

    /**
     * Whether this table owns widths derived from its data instead of stretching every column
     * to fill the viewport. Most legacy tables retain the application-wide user preference.
     */
    default boolean usesContentSizedColumns() {
        return false;
    }

    /** Called after a new page or search result is placed in the table. */
    default void layoutColumns(TableView<T> tableView) {
    }

    /** Whether the shared table shell should offer named views and column choices. */
    default boolean supportsColumnViews() {
        return false;
    }

    /** Populates the shared shell's view menu after this table has created its columns. */
    default void configureColumnViews(MenuButton viewMenu, TableView<T> tableView) {
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
