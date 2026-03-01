package com.hamza.controlsfx.table.columnEdit;

import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.TableColumn;

@FunctionalInterface
public interface TableColumnEdite<T, S> {

    /**
     * Updates a column's data based on the edit event.
     *
     * @param t the edit event containing the new value for the cell
     * @throws DaoException if there is an error during the update process
     */
    void updateColumn(TableColumn.CellEditEvent<T, S> t) throws DaoException;
}
