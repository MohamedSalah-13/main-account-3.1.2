package com.hamza.account.database;

import java.sql.SQLException;

@FunctionalInterface
public interface InsertMultiDataInterface {

    void dataToInsert() throws DaoException, SQLException;
}
