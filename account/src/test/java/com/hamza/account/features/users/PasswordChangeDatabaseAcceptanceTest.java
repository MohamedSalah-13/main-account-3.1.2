package com.hamza.account.features.users;

import com.hamza.account.features.audit.AuditSessionInitializer;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.security.PasswordHasher;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-MySQL acceptance for the complete self-service password-change boundary.
 *
 * <p>The configured business schema is never opened. This test creates a uniquely named scratch
 * schema, runs every Flyway migration, exercises the production DAO and audit triggers, then drops
 * that exact schema even when setup or an assertion fails.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class PasswordChangeDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_password_acceptance_";
    private static final String MACHINE_ID = "password-acceptance-machine";
    private static final String MACHINE_NAME = "PASSWORD-TEST";

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    @BeforeAll
    static void migrateScratchSchema() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());

        host = config.get(CryptoDatabaseConfig.HOST);
        port = config.get(CryptoDatabaseConfig.PORT);
        username = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_USER",
                config.get(CryptoDatabaseConfig.USERNAME));
        password = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD",
                config.get(CryptoDatabaseConfig.PASSWORD));
        schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "");

        try {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + schema
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }

            Flyway.configure()
                    .dataSource(jdbcUrl(schema), username, password)
                    .locations("classpath:db/migration")
                    .validateOnMigrate(false)
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            DataSourceProvider.initialize(host, port, schema, username, password);
        } catch (Exception failure) {
            try {
                dropScratchSchema();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @AfterAll
    static void disconnect() throws Exception {
        try {
            DataSourceProvider.shutdown();
        } finally {
            dropScratchSchema();
        }
    }

    @Test
    void forcedChangePersistsTheHashClearsTheFlagAndWritesSafeAudit() throws Exception {
        String currentPassword = "Current-123";
        String newPassword = "Changed-456";
        TestAccount account = createUser(currentPassword, true);
        PasswordChangeService service = serviceFor(account.user());

        Users changed = service.changeOwnPassword(account.user().getId(),
                new PasswordChangeForm(currentPassword, newPassword, newPassword));

        StoredCredential stored = storedCredential(account.user().getId());
        assertTrue(PasswordHasher.matches(newPassword, stored.hash()).matched());
        assertFalse(PasswordHasher.matches(currentPassword, stored.hash()).matched());
        assertFalse(stored.forced());
        assertEquals(stored.hash(), changed.getPasswordHash());

        AuditRow audit = latestPasswordUpdateAudit(account.user().getId());
        assertNotNull(audit);
        assertEquals(account.user().getId(), audit.userId());
        assertEquals(account.user().getUsername(), audit.actorName());
        assertEquals("APP", audit.source());
        assertEquals(MACHINE_ID, audit.workstationId());
        assertEquals(MACHINE_NAME, audit.workstationName());
        assertCredentialIsAbsent(audit.oldData(), currentPassword, newPassword, stored.hash());
        assertCredentialIsAbsent(audit.newData(), currentPassword, newPassword, stored.hash());
    }

    @Test
    void reusingTheCurrentPasswordIsRefusedWithoutAnyDatabaseWrite() throws Exception {
        String currentPassword = "Current-234";
        TestAccount account = createUser(currentPassword, true);
        PasswordChangeService service = serviceFor(account.user());
        int updatesBefore = passwordUpdateAuditCount(account.user().getId());

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> service.changeOwnPassword(account.user().getId(),
                        new PasswordChangeForm(currentPassword, currentPassword, currentPassword)));

        assertEquals("password.change.error.reused", failure.messageKey());
        StoredCredential stored = storedCredential(account.user().getId());
        assertEquals(account.initialHash(), stored.hash());
        assertTrue(stored.forced());
        assertEquals(updatesBefore, passwordUpdateAuditCount(account.user().getId()));
    }

    @Test
    void aSignedInUserCannotChangeAnotherDatabaseAccount() throws Exception {
        TestAccount target = createUser("Target-345", true);
        TestAccount actor = createUser("Actor-456", true);
        PasswordChangeService service = serviceFor(actor.user());
        int updatesBefore = passwordUpdateAuditCount(target.user().getId());

        PasswordChangeException failure = assertThrows(PasswordChangeException.class,
                () -> service.changeOwnPassword(target.user().getId(),
                        new PasswordChangeForm("Target-345", "Changed-567", "Changed-567")));

        assertEquals("password.change.error.session", failure.messageKey());
        StoredCredential stored = storedCredential(target.user().getId());
        assertEquals(target.initialHash(), stored.hash());
        assertTrue(stored.forced());
        assertEquals(updatesBefore, passwordUpdateAuditCount(target.user().getId()));
    }

    @Test
    void aLockedUserRowTimesOutInsteadOfLeavingTheSaveRunningForever() throws Exception {
        String currentPassword = "Current-678";
        TestAccount account = createUser(currentPassword, true);
        PasswordChangeService service = serviceFor(account.user());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            try (Connection blocker = DriverManager.getConnection(jdbcUrl(schema), username, password);
                 PreparedStatement lock = blocker.prepareStatement(
                         "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                blocker.setAutoCommit(false);
                lock.setInt(1, account.user().getId());
                try (ResultSet row = lock.executeQuery()) {
                    assertTrue(row.next());

                    Future<Throwable> attemptedChange = executor.submit(() -> {
                        try {
                            service.changeOwnPassword(account.user().getId(),
                                    new PasswordChangeForm(currentPassword,
                                            "Changed-789", "Changed-789"));
                            return null;
                        } catch (Throwable failure) {
                            return failure;
                        }
                    });

                    Throwable failure = attemptedChange.get(30, TimeUnit.SECONDS);
                    PasswordChangeException timeout = assertInstanceOf(
                            PasswordChangeException.class, failure);
                    assertEquals("password.change.error.timeout", timeout.messageKey());
                } finally {
                    blocker.rollback();
                }
            }

            StoredCredential stored = storedCredential(account.user().getId());
            assertEquals(account.initialHash(), stored.hash());
            assertTrue(stored.forced());

            // A timed-out statement must not leave a dead physical connection for the next request.
            TestAccount nextAccount = createUser("Current-890", true);
            PasswordChangeService nextService = serviceFor(nextAccount.user());
            Future<Throwable> nextRequest = executor.submit(() -> {
                try {
                    nextService.changeOwnPassword(nextAccount.user().getId(),
                            new PasswordChangeForm("Current-890", "Current-890", "Current-890"));
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            PasswordChangeException reused = assertInstanceOf(PasswordChangeException.class,
                    nextRequest.get(15, TimeUnit.SECONDS));
            assertEquals("password.change.error.reused", reused.messageKey());
        } finally {
            executor.shutdownNow();
        }
    }

    private static PasswordChangeService serviceFor(Users user) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(user, Set.of());
        ConnectionManager.installSessionInitializer(
                new AuditSessionInitializer(session, MACHINE_ID, MACHINE_NAME)::initialize);
        return new PasswordChangeService(
                new JdbcPasswordChangeRepository(DaoFactory.INSTANCE), session);
    }

    private static TestAccount createUser(String plaintextPassword, boolean forced) throws Exception {
        String userName = "pw-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String passwordHash = PasswordHasher.hash(plaintextPassword);
        int userId;
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO users (user_name, user_pass, user_available, must_change_password)
                     VALUES (?, ?, 1, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, userName);
            statement.setString(2, passwordHash);
            statement.setBoolean(3, forced);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                userId = keys.getInt(1);
            }
        }

        Users user = new Users(userId, userName);
        user.setPasswordHash(passwordHash);
        return new TestAccount(user, passwordHash);
    }

    private static StoredCredential storedCredential(int userId) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_pass, must_change_password FROM users WHERE id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                return new StoredCredential(row.getString("user_pass"),
                        row.getBoolean("must_change_password"));
            }
        }
    }

    private static AuditRow latestPasswordUpdateAudit(int userId) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT user_id, actor_name, source, workstation_id, workstation_name,
                            old_data, new_data
                     FROM audit_log
                     WHERE table_name = 'USERS' AND record_id = ? AND action_type = 'UPDATE'
                     ORDER BY id DESC LIMIT 1
                     """)) {
            statement.setString(1, String.valueOf(userId));
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                return new AuditRow(row.getInt("user_id"), row.getString("actor_name"),
                        row.getString("source"), row.getString("workstation_id"),
                        row.getString("workstation_name"), row.getString("old_data"),
                        row.getString("new_data"));
            }
        }
    }

    private static int passwordUpdateAuditCount(int userId) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM audit_log
                     WHERE table_name = 'USERS' AND record_id = ? AND action_type = 'UPDATE'
                     """)) {
            statement.setString(1, String.valueOf(userId));
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                return row.getInt(1);
            }
        }
    }

    private static void assertCredentialIsAbsent(String auditJson, String currentPassword,
                                                  String newPassword, String storedHash) {
        assertNotNull(auditJson);
        assertFalse(auditJson.contains("user_pass"));
        assertFalse(auditJson.contains(currentPassword));
        assertFalse(auditJson.contains(newPassword));
        assertFalse(auditJson.contains(storedHash));
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL";
    }

    private static String environmentOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void dropScratchSchema() throws Exception {
        if (schema == null || !schema.startsWith(SCHEMA_PREFIX)) return;
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    private record TestAccount(Users user, String initialHash) {
    }

    private record StoredCredential(String hash, boolean forced) {
    }

    private record AuditRow(int userId, String actorName, String source, String workstationId,
                            String workstationName, String oldData, String newData) {
    }
}
