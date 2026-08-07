package com.hamza.controlsfx.database;

import com.hamza.controlsfx.language.Error_Text_Show;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for the DAOs. Every method here borrows a connection from the pool
 * for the length of that one call and returns it afterwards, so DAO objects hold
 * no connection of their own and are safe to use from more than one thread.
 * <p>
 * See {@link ConnectionManager} for how a transaction keeps several statements,
 * possibly spread over several DAO objects, on a single connection.
 */
@SuppressWarnings("SqlSourceToSinkFlow")
@Log4j2
public abstract class AbstractDao<T> implements DaoList<T> {

    protected AbstractDao() {
    }

    /**
     * Generates a detailed error message based on the provided query and SQLException.
     *
     * @param query The SQL query that was being executed when the error occurred.
     * @param e     The SQLException that was thrown.
     * @return A concatenated string containing the error message, query, and exception details.
     */
    private static String getMessage(String query, SQLException e) {
        return Error_Text_Show.UNABLE_TO_LOAD_DATA + query + "! " + e;
    }

    /**
     * Runs {@code action} against a pooled connection, returning the connection
     * afterwards. For the few DAOs that need the connection itself - to call a
     * stored procedure, or to read generated keys - rather than one of the query
     * helpers below.
     */
    protected <R> R withConnection(@NotNull ConnectionCallback<R> action) throws DaoException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return action.run(connection);
        } catch (SQLException e) {
            throw mapSqlExceptionToDaoException(e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    /**
     * Executes an update operation (INSERT, UPDATE, DELETE) using the provided SQL query and parameters.
     *
     * @param query      The SQL query to be executed.
     * @param parameters The parameters to be set in the prepared statement.
     * @return The number of rows affected by the SQL statement.
     * @throws DaoException If there is an error during the execution of the SQL statement,
     *                      including constraint violations and other SQL exceptions.
     */
    protected int executeUpdate(@NotNull String query, @NotNull Object... parameters) throws DaoException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                fillStatement(statement, parameters);
                return statement.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("{}{} !", Error_Text_Show.UNABLE_TO_LOAD_DATA, e.getMessage(), e);
            throw mapSqlExceptionToDaoException(e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    /**
     * Executes a SQL update statement with the given query and parameters, and throws an SQLException if an error occurs.
     *
     * @param query      the SQL query to be executed
     * @param parameters the parameters to be set in the PreparedStatement before execution
     * @return the number of rows affected by the update
     * @throws SQLException if a database access error occurs or the SQL statement is invalid
     */
    protected int executeUpdateWithException(@NotNull String query, @NotNull Object... parameters) throws SQLException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                fillStatement(statement, parameters);
                return statement.executeUpdate();
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new SQLException(Error_Text_Show.DUPLICATE_ENTRY);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    /**
     * Executes a batch update on the provided list of items using the specified query
     * and mapper. The updates are executed in batches of 100 items or less.
     *
     * @param list   the list of items to be updated
     * @param query  the SQL query to be executed for each item
     * @param mapper the mapper used to set the data for each item in the list
     * @return the total number of items updated
     * @throws SQLException if a database access error occurs or the SQL statement is invalid
     */
    public int executeUpdateListWithException(@NotNull final List<T> list, @NotNull String query, final GenericMapperList<T> mapper) throws SQLException, DaoException {
        int count = 0;
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                for (T t : list) {
                    mapper.setData(statement, t);
                    statement.addBatch();
                    count++;
                    // execute every 100 rows or le
                    if (count % 100 == 0 || count == list.size()) {
                        statement.executeBatch();
                    }
                }
            }
        } finally {
            ConnectionManager.release(connection);
        }
        return count;
    }

    /**
     * Runs the supplied statements inside one transaction, committing them together
     * or rolling them all back.
     * <p>
     * The transaction is bound to the calling thread, so every statement it runs -
     * including those issued through other DAO objects - goes to the same
     * connection. A call made while a transaction is already open on this thread
     * joins that transaction rather than starting a second one, and leaves the
     * commit to whoever opened it.
     * <p>
     * The return value is a success flag, not a row count: the individual
     * statements report their own affected-row counts and those are not aggregated
     * here. Any failure throws, so a caller that gets a value back always gets
     * {@code 1}. Comparing the result against 1 therefore proves nothing that
     * reaching the next line has not already proven.
     *
     * @param insertMultiDataInterface the statements to run in the transaction.
     * @return {@code 1}, once the transaction has committed.
     * @throws DaoException if any statement fails; the transaction is rolled back first.
     */
    public int insertMultiData(@NotNull InsertMultiDataInterface insertMultiDataInterface) throws DaoException {
        Connection transaction = null;
        try {
            transaction = ConnectionManager.beginTransaction();

            try {
                insertMultiDataInterface.dataToInsert();
            } catch (DaoException e) {
                rollbackIfOwner(transaction);
                throw e;
            } catch (Exception e) {
                rollbackIfOwner(transaction);
                throw new DaoException(e);
            }

            if (transaction != null) {
                transaction.commit();
            }
        } catch (SQLException e) {
            throw mapSqlExceptionToDaoException(e);
        } finally {
            ConnectionManager.endTransaction(transaction);
        }
        return 1;
    }

    /**
     * Rolls back only a transaction this call opened. A {@code null} connection
     * means an outer transaction is in charge, and rolling it back here would
     * discard work that is not ours to discard; rethrowing lets the owner do it.
     */
    private void rollbackIfOwner(Connection transaction) {
        if (transaction == null) {
            return;
        }
        try {
            transaction.rollback();
        } catch (SQLException e) {
            log.error("Failed to roll back the transaction", e);
        }
    }

    private DaoException mapSqlExceptionToDaoException(SQLException e) {
        log.error(Error_Text_Show.UNABLE_TO_LOAD_DATA, e.getMessage(), e);
        if (e.getMessage() == null) {
            return new DaoException(e);
        }
        if (e.getMessage().contains("Duplicate entry")) {
            return new DaoException(Error_Text_Show.DUPLICATE_ENTRY);
        }
        if (e.getMessage().contains("Cannot delete or update a parent row")) {
            return new DaoException(Error_Text_Show.CANT_DELETE);
        }
        if (e.getMessage().contains("Data truncation: Data too long for column")) {
            return new DaoException("Long Data");
        }
        if (e.getMessage().contains("Data truncation: Incorrect datetime value")) {
            return new DaoException("Date Data");
        }
        if (e.getMessage().contains("Data truncation: Out of range value for column")) {
            return new DaoException("Out of Range Data");
        }
        if (e.getMessage().contains("Data truncation: Value too long for column")) {
            return new DaoException("Value too long for column");
        } else
            return new DaoException(e);
    }

    /**
     * Executes the given SQL query and maps the result to an object of type T using the provided GenericMapper.
     *
     * @param query      The SQL query to be executed.
     * @param mapper     The GenericMapper used to map the result set to the desired object.
     * @param parameters The parameters to be used in the SQL query.
     * @return An object of type T if the query returns exactly one result, otherwise null.
     * @throws DaoException If an error occurs while executing the query or mapping the result.
     */
    public T queryForObject(@NotNull String query, @NotNull final GenericMapper<T> mapper, @NotNull Object... parameters) throws DaoException {
        List<T> list = queryForObjects(query, mapper, parameters);
        if (list.size() == 1) return list.getFirst();
        return null;
    }

    /**
     * Executes a SQL query and maps the result set to a list of objects using the provided mapper.
     *
     * @param query      the SQL query to be executed
     * @param mapper     the GenericMapper used to map the result set into objects
     * @param parameters the parameters to be set in the prepared statement
     * @return a list of objects resulting from the query
     * @throws DaoException if any SQL errors occur during the execution of the query
     */
    public List<T> queryForObjects(@NotNull String query, @NotNull final GenericMapper<T> mapper, @NotNull Object... parameters) throws DaoException {
        List<T> list = new ArrayList<>();
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                fillStatement(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        list.add(mapper.mapItem(resultSet));
                    }
                }
            }
        } catch (SQLException e) {
            log.error(getMessage(query, e), e);
            throw new DaoException(getMessage(query, e));
        } finally {
            ConnectionManager.release(connection);
        }
        return list;
    }

    /**
     * Executes a stored procedure and maps its result set to a list of objects of type T.
     *
     * @param procedureName the name of the stored procedure to execute
     * @param mapper        the GenericMapper used to map each row into an object
     * @return a list of objects of type T containing the result set of the procedure
     * @throws DaoException if there is an error executing the stored procedure
     */
    protected List<T> queryForProcedure(@NotNull String procedureName, @NotNull final GenericMapper<T> mapper) throws DaoException {
        List<T> list = new ArrayList<>();
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (CallableStatement statement = connection.prepareCall("call " + procedureName);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(mapper.mapItem(resultSet));
                }
            }
        } catch (SQLException e) {
            log.error("{}{} !", Error_Text_Show.UNABLE_TO_LOAD_DATA, e.getMessage(), e);
            throw new DaoException(Error_Text_Show.UNABLE_TO_LOAD_DATA + " " + e.getMessage(), e);
        } finally {
            ConnectionManager.release(connection);
        }
        return list;
    }

    /**
     * Fills the given {@code PreparedStatement} with the provided parameters.
     *
     * @param statement  the {@code PreparedStatement} to be filled
     * @param parameters the parameters to set in the {@code PreparedStatement}
     * @throws SQLException if a database access error occurs or the parameters are not set correctly
     */
    private void fillStatement(@NotNull PreparedStatement statement, @NotNull Object... parameters) throws SQLException {
        for (int i = 1; i < parameters.length + 1; i++) {
            statement.setObject(i, parameters[i - 1]);
        }
    }

    public void setData(PreparedStatement statement, Object[] objects) throws SQLException {
        for (int i = 0; i < objects.length; i++) {
            statement.setObject(i + 1, objects[i]);
        }

    }

    /**
     * Reads a single numeric value. Callers that derive business data from the
     * result - the next invoice number, for instance - must not receive a made up
     * number when the query fails, so a failure is reported rather than defaulted.
     * Use {@link #queryForIntOrDefault} where a missing count is genuinely
     * tolerable.
     */
    public int queryForInt(String query) throws DaoException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DaoException(getMessage(query, new SQLException("query returned no rows")));
                }
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            log.error(getMessage(query, e), e);
            throw new DaoException(getMessage(query, e), e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    /**
     * Same as {@link #queryForInt} but falls back to {@code defaultValue} when the
     * query fails, for cases such as pagination totals where an unavailable count
     * degrades the display without corrupting anything.
     */
    public int queryForIntOrDefault(String query, int defaultValue) {
        try {
            return queryForInt(query);
        } catch (DaoException e) {
            log.error("Falling back to {} for query: {}", defaultValue, query, e);
            return defaultValue;
        }
    }

    public List<Integer> queryForIntList(String query) {
        List<Integer> list = new ArrayList<>();
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(resultSet.getInt(1));
                }
            }
        } catch (SQLException e) {
            log.error(getMessage(query, e), e);
        } finally {
            ConnectionManager.release(connection);
        }
        return list;
    }
}
