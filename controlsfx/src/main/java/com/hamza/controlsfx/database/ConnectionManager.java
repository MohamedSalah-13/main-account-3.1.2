package com.hamza.controlsfx.database;

import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hands out pooled connections for the duration of a single operation.
 * <p>
 * The DAOs used to share one {@link Connection} that was borrowed from the pool
 * at startup and never given back. That single object was driven from the JavaFX
 * thread and from every background thread at once, which a JDBC connection does
 * not support, and {@code setAutoCommit(false)} on it put unrelated work into
 * whatever transaction happened to be open.
 * <p>
 * Each operation now borrows its own connection and returns it. A transaction
 * still needs several statements - across several DAO objects - to run on one
 * connection, so {@link #beginTransaction} binds a connection to the calling
 * thread and {@link #acquire} hands that same connection to everything running
 * on that thread until the transaction ends. Work on other threads is unaffected.
 */
@Log4j2
public final class ConnectionManager {

    private static final ThreadLocal<Connection> TRANSACTIONAL = new ThreadLocal<>();

    private ConnectionManager() {
    }

    /**
     * The connection to use for one operation. Inside a transaction this is the
     * thread's transactional connection; otherwise it is a fresh one from the pool.
     * Always pair with {@link #release}.
     */
    public static Connection acquire() throws SQLException {
        Connection transactional = TRANSACTIONAL.get();
        if (transactional != null) {
            return transactional;
        }
        return DataSourceProvider.getDataSource().getConnection();
    }

    /**
     * Returns a connection from {@link #acquire} to the pool. A transactional
     * connection is left alone: it belongs to whoever opened the transaction and
     * is released when that transaction ends.
     */
    public static void release(Connection connection) {
        if (connection == null || connection == TRANSACTIONAL.get()) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.error("Failed to return a connection to the pool", e);
        }
    }

    /** Whether the calling thread is already inside a transaction. */
    public static boolean inTransaction() {
        return TRANSACTIONAL.get() != null;
    }

    /**
     * Borrows a connection, switches off auto-commit and binds it to this thread.
     * The caller must pass the result to {@link #endTransaction} in a finally block.
     *
     * @return the transactional connection, or {@code null} if a transaction was
     * already open on this thread - in that case the outer transaction owns the
     * connection and this call must not commit, roll back or release it.
     */
    public static Connection beginTransaction() throws SQLException {
        if (inTransaction()) {
            return null;
        }
        Connection connection = DataSourceProvider.getDataSource().getConnection();
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            closeQuietly(connection);
            throw e;
        }
        TRANSACTIONAL.set(connection);
        return connection;
    }

    /**
     * Unbinds the transactional connection, restores auto-commit and returns it to
     * the pool. A {@code null} argument means {@link #beginTransaction} joined an
     * outer transaction, so there is nothing to unwind here.
     */
    public static void endTransaction(Connection connection) {
        if (connection == null) {
            return;
        }
        TRANSACTIONAL.remove();
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            log.error("Failed to restore auto-commit before returning the connection", e);
        }
        closeQuietly(connection);
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            log.error("Failed to return a connection to the pool", e);
        }
    }
}
