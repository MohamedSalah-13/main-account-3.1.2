package com.hamza.controlsfx.database;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class SqlStatements {

    /**
     * A constant placeholder used for SQL query parameter binding.
     * Represented by the string "=?", which is appended to column names
     * in SQL statements to indicate where parameter values will be substituted.
     */
    private static final String PLACEHOLDER = "=?";

    /**
     * Generates a SQL INSERT statement for a specified table with the provided column names.
     *
     * @param tableName   the name of the table into which the data will be inserted
     * @param columnNames the names of the columns into which the data will be inserted
     * @return a SQL INSERT statement as a String
     */
    public static String insertStatement(@NotNull String tableName, @NotNull String... columnNames) {
        String columnList = String.join(",", columnNames);
        String placeholders = String.join(",", createPlaceholders(columnNames.length));

        return "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";
    }

    /**
     * Generates a SQL UPDATE statement for the specified table and columns.
     *
     * @param tableName   the name of the table to update
     * @param whereColumn the column to use in the WHERE clause
     * @param columnNames the columns to be included in the SET clause
     * @return a SQL UPDATE statement as a String
     */
    public static String updateStatement(@NotNull String tableName, @NotNull String whereColumn, @NotNull String... columnNames) {
        String setClause = createSetClause(columnNames);

        return "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereColumn + PLACEHOLDER;
    }

    /**
     * Generates a SQL DELETE statement for a specific table and column condition.
     *
     * @param tableName   the name of the table from which to delete records
     * @param whereColumn the column that specifies the condition for deletion
     * @return a SQL DELETE statement as a String
     */
    public static String deleteStatement(@NotNull String tableName, @NotNull String whereColumn) {
        return "DELETE FROM " + tableName + " WHERE " + whereColumn + PLACEHOLDER;
    }

    /**
     * Generates a SQL SELECT statement to retrieve all records from the specified table.
     *
     * @param tableName the name of the table from which to select records
     * @return a SQL SELECT statement as a String
     */
    public static String selectStatement(@NotNull String tableName) {
        return "SELECT * FROM " + tableName;
    }


    /**
     * Generates a SQL SELECT statement for a specified table with a WHERE clause.
     *
     * @param tableName   the name of the table from which to select records
     * @param whereColumn the column to use in the WHERE clause
     * @return a SQL SELECT statement with a WHERE clause as a String
     */
    public static String selectStatementByColumnWhere(@NotNull String tableName, @NotNull String whereColumn) {
        return selectStatement(tableName) + " WHERE " + whereColumn + PLACEHOLDER;
    }

    /**
     * Creates a SQL SELECT statement with a JOIN clause including specified columns.
     *
     * @param tableName   the name of the main table
     * @param joinTable   the name of the table to join
     * @param columnNames the columns to select in the query
     * @return a SQL SELECT statement as a String
     */
    public static String selectStatementsWithJoin(@NotNull String tableName, @NotNull String joinTable, @NotNull String... columnNames) {
        String columns = String.join(",", columnNames);

        return "SELECT " + columns +
                " FROM " + tableName +
                " JOIN " + joinTable;
    }

    /**
     * Generates a SQL DELETE statement to remove records in a specified table where
     * the values of a specific column fall within a given range of IDs.
     *
     * @param tableName   the name of the table from which to delete records
     * @param whereColumn the column on which to apply the range condition
     * @param range       the range of integer IDs to delete
     * @return a SQL DELETE statement as a String
     */
    public static String deleteInRangeId(@NotNull String tableName, @NotNull String whereColumn, @NotNull Integer... range) {
        String rangeList = String.join(",", createRangeList(range));

        return "DELETE FROM " + tableName + " WHERE " + whereColumn + " IN (" + rangeList + ")";
    }

    /**
     * Creates an array of placeholders for SQL statements.
     *
     * @param length the number of placeholders to create
     * @return an array of placeholder strings, each containing a question mark
     */
    private static String[] createPlaceholders(int length) {
        String[] placeholders = new String[length];
        Arrays.fill(placeholders, "?");
        return placeholders;
    }

    /**
     * Constructs a SQL SET clause for an UPDATE statement from the given column names.
     *
     * @param columnNames Array of column names to be included in the SET clause.
     * @return A string representing the SQL SET clause.
     */
    private static String createSetClause(@NotNull String[] columnNames) {
        StringBuilder setClause = new StringBuilder();
        for (int i = 0; i < columnNames.length; i++) {
            setClause.append(columnNames[i]).append(PLACEHOLDER);
            if (i < columnNames.length - 1) {
                setClause.append(",");
            }
        }
        return setClause.toString();
    }

    /**
     * Converts an array of integers to an array of strings.
     *
     * @param range an array of integers to be converted
     * @return an array of strings corresponding to the input array of integers
     */
    private static String[] createRangeList(@NotNull Integer[] range) {
        String[] rangeList = new String[range.length];
        for (int i = 0; i < range.length; i++) {
            rangeList[i] = String.valueOf(range[i]);
        }
        return rangeList;
    }
}
