package com.hamza.controlsfx.database;

import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface GenericMapperList<T> {

    /**
     * Sets the data for the given PreparedStatement using the provided value of type T.
     *
     * @param statement the PreparedStatement to set data into
     * @param t the value of type T to be set in the PreparedStatement
     * @throws SQLException if a database access error occurs
     */
    void setData(@NotNull final PreparedStatement statement, final T t) throws SQLException, DaoException;
}
