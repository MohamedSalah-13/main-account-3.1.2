package com.hamza.controlsfx.database;

import com.hamza.controlsfx.language.Error_Text_Show;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("SqlSourceToSinkFlow")
@Log4j2
public abstract class AbstractDao<T> implements DaoList<T> {

    protected Connection connection;

    protected AbstractDao() {
    }

    /**
     * Constructs an AbstractDao with the specified database connection.
     *
     * @param connection the database connection to be used by this DAO
     */
    protected AbstractDao(Connection connection) {
        this.connection = connection;
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
     * Executes an update operation (INSERT, UPDATE, DELETE) using the provided SQL query and parameters.
     *
     * @param query      The SQL query to be executed.
     * @param parameters The parameters to be set in the prepared statement.
     * @return The number of rows affected by the SQL statement.
     * @throws DaoException If there is an error during the execution of the SQL statement,
     *                      including constraint violations and other SQL exceptions.
     */
    protected int executeUpdate(@NotNull String query, @NotNull Object... parameters) throws DaoException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            fillStatement(statement, parameters);
            return statement.executeUpdate();
        } catch (SQLException e) {
//            log.error("SQL integrity constraint violation: {}", e.getMessage(), e);
            log.error("{}{} !", Error_Text_Show.UNABLE_TO_LOAD_DATA, e.getMessage(), e);
            if (e.getMessage().contains("Duplicate entry")) {
                throw new DaoException(Error_Text_Show.DUPLICATE_ENTRY);
            }
            if (e.getMessage().contains("Data truncation: Data too long for column")) {
                throw new DaoException("Long Data");
            }
            if (e.getMessage().contains("Cannot delete or update a parent row")) {
                throw new DaoException(Error_Text_Show.CANT_DELETE);
            } else
                throw new DaoException(Error_Text_Show.UNABLE_TO_LOAD_DATA, e);
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
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            fillStatement(statement, parameters);
            return statement.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new SQLException(Error_Text_Show.DUPLICATE_ENTRY);
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
        PreparedStatement statement = connection.prepareStatement(query);
//        System.out.println(statement.toString());
        for (T t : list) {
            mapper.setData(statement, t);
            statement.addBatch();
            count++;
            // execute every 100 rows or le
            if (count % 100 == 0 || count == list.size()) {
                statement.executeBatch();
            }
        }
        return count;
    }

    /**
     * Inserts multiple data entries into the database using the provided interface.
     *
     * @param insertMultiDataInterface An interface that provides the data to be inserted.
     * @return The count of data entries successfully inserted, typically 1 if the transaction commits successfully.
     * @throws DaoException If any SQL or data access error occurs during the insertion process.
     */
    public int insertMultiData(@NotNull InsertMultiDataInterface insertMultiDataInterface) throws DaoException {
        int insertedRows = 1;
        try {
            connection.setAutoCommit(false);

            try {
                insertMultiDataInterface.dataToInsert();
            } catch (Exception e) {
                connection.rollback();
                throw new DaoException(e);
            }

            connection.commit();
        } catch (SQLException e) {
            throw mapSqlExceptionToDaoException(e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                log.error(Error_Text_Show.UNABLE_TO_SET_CONNECT_TO_AUTO_SAVE, e.getMessage(), e);
            }
        }
        return insertedRows;
    }

    private DaoException mapSqlExceptionToDaoException(SQLException e) {
        log.error(Error_Text_Show.UNABLE_TO_LOAD_DATA, e.getMessage(), e);
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
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.prepareStatement(query);
            fillStatement(statement, parameters);
            resultSet = statement.executeQuery();
//            CallableStatement cs=connection.prepareCall("{call get_student(?)}");
            while (resultSet.next()) {
                T item = mapper.mapItem(resultSet);
                list.add(item);
            }
        } catch (SQLException e) {
            log.error(getMessage(query, e));
            throw new DaoException(getMessage(query, e));
        } finally {
            try {
                if (resultSet != null)
                    resultSet.close();
            } catch (SQLException e) {
                log.error("{}{}", Error_Text_Show.UNABLE_CLOSED, e.getMessage(), e);
            }

            try {
                if (statement != null)
                    statement.close();
            } catch (SQLException e) {
                log.error("{}{}", Error_Text_Show.UNABLE_CLOSED, e.getMessage(), e);
            }
        }
        return list;
    }

    /**
     * Executes a stored procedure and retrieves the result set as a list of objects of type T.
     *
     * @param procedureName the name of the stored procedure to execute
     * @return a list of objects of type T containing the result set of the procedure
     * @throws DaoException if there is an error executing the stored procedure
     */
    protected List<T> queryForProcedure(@NotNull String procedureName) throws DaoException {
        List<T> list = new ArrayList<>();
        ResultSet resultSet = null;
        try (CallableStatement statement = connection.prepareCall("call " + procedureName)) {
            resultSet = statement.executeQuery();
        } catch (SQLException e) {
            log.error("{}{} !", Error_Text_Show.UNABLE_TO_LOAD_DATA, e.getMessage(), e);
            throw new DaoException(Error_Text_Show.UNABLE_TO_LOAD_DATA + " " + e.getMessage(), e);
        } finally {
            try {
                if (resultSet != null)
                    resultSet.close();
            } catch (SQLException e) {
                log.error("{}{}", Error_Text_Show.UNABLE_CLOSED, e.getMessage(), e);
            }
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

    public int queryForInt(String query) {
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException e) {
            log.error(e.getMessage(), e.getCause());
            return 0;
        }
    }
}
