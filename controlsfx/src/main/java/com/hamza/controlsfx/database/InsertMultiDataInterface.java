package com.hamza.controlsfx.database;

import java.sql.SQLException;

@FunctionalInterface
public interface InsertMultiDataInterface {

    /**
     * Inserts multiple data entries into the database.
     *
     * @throws DaoException if there is an error related to data access operations.
     * @throws SQLException if there is a database access error or other errors related to SQL execution.
     */
    void dataToInsert() throws DaoException, SQLException;
}
