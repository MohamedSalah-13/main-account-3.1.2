package com.hamza.controlsfx.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Work that needs the {@link Connection} itself rather than one of the query
 * helpers on {@link AbstractDao}. Run it through
 * {@link AbstractDao#withConnection} so the connection is returned to the pool.
 */
@FunctionalInterface
public interface ConnectionCallback<R> {

    R run(Connection connection) throws SQLException, DaoException;
}
