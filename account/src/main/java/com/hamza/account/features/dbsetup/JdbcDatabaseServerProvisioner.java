package com.hamza.account.features.dbsetup;

import com.hamza.account.features.dbsetup.DatabaseServerProvisioningResult.PasswordOutcome;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
            PasswordOutcome password = createOrKeepAccount(connection, request, account);
            execute(connection, grantSql(request.database(), account));
            return new DatabaseServerProvisioningResult(request.database(), account, password);
        }
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

    /**
     * Creates the account, and leaves an existing one alone unless asked otherwise.
     *
     * <p>This account is shared by every till in the shop, so its password is not this
     * run's to change. The utility is meant to be run again - to authorize a second
     * workstation, or to re-create a schema - and it used to end with an unconditional
     * {@code ALTER USER}, which silently replaced the password every already-configured
     * machine holds in its own {@code config.xml}. The first symptom was the other tills
     * refusing to start, with nothing on this screen having reported a change.
     *
     * <p>{@code CREATE USER IF NOT EXISTS ... IDENTIFIED BY} sets the password only in the
     * run that creates the account, which is exactly the wanted rule; replacing it later
     * is a decision the technician makes deliberately.
     */
    private static PasswordOutcome createOrKeepAccount(
            Connection connection, DatabaseServerProvisioningRequest request, String account)
            throws SQLException {
        boolean existed = accountExists(connection, request.applicationUsername(), request.allowedHost());
        try (PreparedStatement create = connection.prepareStatement(
                "CREATE USER IF NOT EXISTS " + account + " IDENTIFIED BY ?")) {
            create.setString(1, request.applicationPassword());
            create.executeUpdate();
        }
        if (!existed) {
            return PasswordOutcome.CREATED;
        }
        if (!request.resetExistingPassword()) {
            return PasswordOutcome.UNCHANGED;
        }
        try (PreparedStatement alter = connection.prepareStatement(
                "ALTER USER " + account + " IDENTIFIED BY ?")) {
            alter.setString(1, request.applicationPassword());
            alter.executeUpdate();
        }
        return PasswordOutcome.RESET;
    }

    /**
     * Whether that account is already on this server. An administrator allowed to create
     * accounts cannot always read {@code mysql.user}, so an unreadable answer counts as
     * "it exists": the report that follows then says the password was left as it was,
     * which is the answer that cannot mislead whoever is about to type it into a till.
     */
    private static boolean accountExists(Connection connection, String username, String host) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM mysql.user WHERE user = ? AND host = ?")) {
            statement.setString(1, username);
            statement.setString(2, host);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException unreadable) {
            return true;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

}
