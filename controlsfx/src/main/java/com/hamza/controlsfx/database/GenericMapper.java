package com.hamza.controlsfx.database;

import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface GenericMapper<T> {

    /**
     * Maps the current row of the given ResultSet to an instance of the type T.
     *
     * @param resultSet the ResultSet to be mapped
     * @return the mapped instance of the type T
     * @throws SQLException if a database access error occurs or the ResultSet is closed
     * @throws DaoException if any error specific to the data access layer occurs during the mapping
     */
    T mapItem(@NotNull final ResultSet resultSet) throws SQLException, DaoException;

}
