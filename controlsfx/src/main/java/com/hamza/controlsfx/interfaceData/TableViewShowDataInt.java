package com.hamza.controlsfx.interfaceData;

import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;

public interface TableViewShowDataInt<T> {

    /**
     * Initializes and configures the given TableView instance.
     *
     * @param tableView the TableView instance to be configured
     */
    default void getTable(TableView<T> tableView) {
    }

    /**
     * Retrieves a list of data from the data table for processing.
     *
     * @return a list of data items of type T
     * @throws DaoException if an error occurs while fetching data from the data source
     */
    default List<T> dataList() throws DaoException {
        return new ArrayList<>();
    }

    /**
     * Build with {@code com.hamza.controlsfx.table.Columns} - see rule ق-ل1 in
     * {@code docs/new-code-rules.md}. Replaced {@code classForColumn()}, which
     * resolved a field by name at run time through {@code PropertyValueFactory}.
     */
    List<TableColumn<T, ?>> columns();

}
