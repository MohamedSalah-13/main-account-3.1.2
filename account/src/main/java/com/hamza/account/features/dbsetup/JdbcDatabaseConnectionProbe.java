package com.hamza.account.features.dbsetup;

import java.sql.DriverManager;
import java.sql.SQLException;

/** A short, one-shot connection used only by the installation utility. */
public final class JdbcDatabaseConnectionProbe implements DatabaseConnectionProbe {

    @Override
    public DatabaseProbeResult test(DatabaseConnectionSettings settings) throws SQLException {
        try (var connection = DriverManager.getConnection(settings.jdbcUrl(false),
                settings.username(), settings.password());
             var statement = connection.prepareStatement(
                     "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            statement.setString(1, settings.database());
            try (var result = statement.executeQuery()) {
                return new DatabaseProbeResult(result.next());
            }
        }
    }
}
