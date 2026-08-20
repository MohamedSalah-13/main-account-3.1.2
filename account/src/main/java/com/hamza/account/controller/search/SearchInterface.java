package com.hamza.account.controller.search;

import javafx.scene.control.TableColumn;

import java.util.List;

public interface SearchInterface<T> {

    /**
     * Build with {@code com.hamza.controlsfx.table.Columns} - see rule ق-ل1 in
     * {@code docs/new-code-rules.md}. Replaced {@code getSearchClass()}, which
     * resolved a field by name at run time through {@code PropertyValueFactory}.
     */
    List<TableColumn<T, ?>> columns();

    String getName(T t);

    default boolean selectMultiple() {
        return false;
    }

    List<T> getFilterItems(String filter) throws Exception;

}
