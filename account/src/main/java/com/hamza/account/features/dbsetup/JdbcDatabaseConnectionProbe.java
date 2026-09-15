package com.hamza.account.features.dbsetup;

import java.sql.Connection;
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
            boolean exists;
            try (var result = statement.executeQuery()) {
                exists = result.next();
            }
            return new DatabaseProbeResult(exists, readStoredProgramLogging(connection));
        }
    }

    /**
     * Reading a global variable needs no privilege. A server that does not answer the
     * query at all is reported as not requiring anything: this is a warning, and a
     * warning the tool cannot stand behind is worse than none.
     */
    static StoredProgramLogging readStoredProgramLogging(Connection connection) {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(StoredProgramLogging.READ_SQL)) {
            if (result.next()) {
                return StoredProgramLogging.of(result.getBoolean(1), result.getBoolean(2));
            }
        } catch (SQLException unanswered) {
            // Not a MySQL that knows these variables; nothing to warn about.
        }
        return StoredProgramLogging.NOT_REQUIRED;
    }
}
