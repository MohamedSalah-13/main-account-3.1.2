package com.hamza.controlsfx.database;

import com.hamza.controlsfx.language.Error_Text_Show;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

@Log4j2
public record DBConnection(String host, String port, String dbName, String username, String pass) {

    public DBConnection(@NotNull String host, @NotNull String port, @NotNull String dbName, @NotNull String username, @NotNull String pass) {
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        this.pass = pass;
    }

    public Connection getConnection() throws DaoException {
        try {
            return DataSourceProvider.getDataSource().getConnection();
        } catch (SQLException | IllegalStateException e) {
            log.error("DB connection failed for db={}", dbName, e);
            throw new DaoException(Error_Text_Show.UNABLE_CONNECT + ": " + dbName, e);
        }
    }
}