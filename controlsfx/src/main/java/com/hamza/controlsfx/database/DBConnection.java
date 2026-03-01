package com.hamza.controlsfx.database;

import com.hamza.controlsfx.language.Error_Text_Show;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Log4j2
public class DBConnection {

    /**
     * The host address of the database server.
     * This field stores the hostname or IP address used to establish a connection
     * to the database server. It is provided during the instantiation of the
     * DBConnection object and is used as part of the URL for the JDBC connection.
     */
    private final String host;
    /**
     * Represents the port number where the database server is listening.
     */
    private final String port;
    /**
     * Represents the name of the database to which the connection will be established.
     */
    private final String dbName;
    /**
     * The username used to connect to the database.
     */
    private final String username;
    /**
     * Stores the password required to connect to the database.
     */
    private final String pass;

    /**
     * Creates a new instance of the DBConnection class.
     *
     * @param host the hostname or IP address of the database server
     * @param port the port number on which the database server is listening
     * @param dbName the name of the database to connect to
     * @param username the username to use for authenticating with the database
     * @param pass the password to use for authenticating with the database
     */
    public DBConnection(@NotNull String host, @NotNull String port, @NotNull String dbName, @NotNull String username, @NotNull String pass) {
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        this.pass = pass;
    }

    /**
     * Establishes a connection to the database using the provided credentials and database information.
     *
     * @return A {@link Connection} object for interacting with the database.
     * @throws DaoException If there is an issue connecting to the database.
     */
    public Connection getConnection() throws DaoException {

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String format = String.format("jdbc:mysql://%s:%s/%s", host, port, dbName);
            return DriverManager.getConnection(format, username, pass);
        } catch (SQLException | ClassNotFoundException e) {
            log.error(e.getMessage(), e.getCause());
            throw new DaoException(Error_Text_Show.UNABLE_CONNECT + ": " + dbName + e);
        }
    }

}
