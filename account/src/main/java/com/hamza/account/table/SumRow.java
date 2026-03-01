package com.hamza.account.table;

import com.hamza.controlsfx.database.DaoException;

public interface SumRow {

    /**
     * Sums the values in the specified row identified by the index.
     *
     * @param index the index of the row to sum
     * @throws DaoException if there is an error accessing the data
     */
    void sum_row(int index) throws DaoException;

}
