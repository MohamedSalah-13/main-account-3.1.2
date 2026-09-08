package com.hamza.account.features.dbsetup;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Executes the idempotent MySQL server preparation requested by a technician. */
public final class JdbcDatabaseServerProvisioner implements DatabaseServerProvisioner {

    @Override
    public DatabaseServerProvisioningResult provision(DatabaseServerProvisioningRequest request)
            throws SQLException {
        String account = accountLiteral(request.applicationUsername(), request.allowedHost());
        try (Connection connection = DriverManager.getConnection(jdbcUrl(request),
                request.administratorUsername(), request.administratorPassword())) {
            execute(connection, createDatabaseSql(request.database()));
            createOrUpdateAccount(connection, account, request.applicationPassword());
            execute(connection, grantSql(request.database(), account));
        }
        return new DatabaseServerProvisioningResult(request.database(), account);
    }

    static String createDatabaseSql(String database) {
        return "CREATE DATABASE IF NOT EXISTS `" + database
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
    }

    static String grantSql(String database, String account) {
        return "GRANT ALL PRIVILEGES ON `" + database + "`.* TO " + account;
    }

    static String accountLiteral(String username, String allowedHost) {
        return "'" + username + "'@'" + allowedHost + "'";
    }

    private static String jdbcUrl(DatabaseServerProvisioningRequest request) {
        return new DatabaseConnectionSettings(request.serverHost(), request.port(), request.database(),
                request.administratorUsername(), request.administratorPassword()).jdbcUrl(false);
    }

    private static void createOrUpdateAccount(Connection connection, String account, String password)
            throws SQLException {
        try (var create = connection.prepareStatement(
                "CREATE USER IF NOT EXISTS " + account + " IDENTIFIED BY ?")) {
            create.setString(1, password);
            create.executeUpdate();
        }
        try (var alter = connection.prepareStatement("ALTER USER " + account + " IDENTIFIED BY ?")) {
            alter.setString(1, password);
            alter.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

}
