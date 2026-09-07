package com.hamza.account.features.audit;

import com.hamza.account.features.rbac.UserSessionContext;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real-MySQL acceptance for the audit migration, trigger attribution and repository.
 *
 * <p>The test never points Flyway at the configured business schema. It creates a uniquely named
 * scratch schema, migrates it from nothing, and drops that exact schema in {@link #disconnect()}.
 * If the configured application account cannot create databases, supply a short-lived server
 * administrator through {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_USER} and
 * {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD}.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class AuditLogDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_audit_acceptance_";
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
        DataSourceProvider.shutdown();
        dropScratchSchema();
    }

    @Test
    void freshMigrationContainsTheAuditIntegrityContract() throws Exception {
        assertEquals(3, scalarInt("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'audit_log'
                  AND column_name IN ('actor_name', 'workstation_id', 'workstation_name')
                """));
        assertEquals(2, scalarInt("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'audit_log'
                  AND index_name IN ('idx_audit_time', 'idx_audit_table_time')
                """));
        assertEquals(1, scalarInt("""
                SELECT COUNT(*) FROM auth_permission
                WHERE permission_key = 'audit.view' AND risk_level = 'HIGH'
                """));
        assertEquals(1, scalarInt("""
                SELECT COUNT(*) FROM information_schema.routines
                WHERE routine_schema = DATABASE() AND routine_name = 'write_audit_log'
                """));
        assertEquals(1, scalarInt("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'audit_admin_event'
                """));
        assertEquals(2, scalarInt("""
                SELECT COUNT(*) FROM auth_permission
                WHERE permission_key IN ('audit.export', 'audit.retention.manage')
                """));
        assertEquals(1, scalarInt("""
                SELECT COUNT(*) FROM information_schema.routines
                WHERE routine_schema = DATABASE() AND routine_name = 'write_audit_admin_event'
                """));
        assertEquals(1, scalarInt("""
                SELECT COUNT(*) FROM app_setting
                WHERE setting_key = 'audit.retention.enabled' AND setting_value = 'false'
                """));
        assertEquals(1, scalarInt("""
                SELECT COUNT(*) FROM auth_permission
                WHERE permission_key = 'audit.admin.view' AND risk_level = 'HIGH'
                """));
        assertEquals(2, scalarInt("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'audit_admin_event'
                  AND index_name IN ('idx_audit_admin_source_time', 'idx_audit_admin_actor_time')
                """));
        assertEquals(12, scalarInt("""
                SELECT COUNT(*) FROM information_schema.triggers
                WHERE trigger_schema = DATABASE() AND trigger_name LIKE 'audit_auth_%'
                """));
    }

    @Test
    void administrativeJournalKeepsItsActorAndCannotBeChangedOrDeleted() throws Exception {
        UserSessionContext session = new UserSessionContext();
        session.signIn(1, "audit-admin-acceptance", Set.of());
        ConnectionManager.installSessionInitializer(
                new AuditSessionInitializer(session, "acceptance-machine", "AUDIT-TEST")::initialize);

        long eventId;
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "CALL write_audit_admin_event('EXPORT', NULL, 3, JSON_OBJECT('format', 'XLSX'))")) {
            statement.execute();
        }
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, actor_user_id, actor_name, source, workstation_id
                     FROM audit_admin_event ORDER BY id DESC LIMIT 1
                     """); ResultSet row = statement.executeQuery()) {
            assertTrue(row.next());
            eventId = row.getLong("id");
            assertEquals(1, row.getInt("actor_user_id"));
            assertEquals("audit-admin-acceptance", row.getString("actor_name"));
            assertEquals("APP", row.getString("source"));
            assertEquals("acceptance-machine", row.getString("workstation_id"));
        }

        long immutableId = eventId;
        assertThrows(Exception.class, () -> updateAdminEvent(immutableId));
        assertThrows(Exception.class, () -> deleteAdminEvent(immutableId));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM audit_admin_event WHERE id = " + eventId));
    }

    @Test
    void pooledWritesKeepTheApplicationActorAndWorkstation() throws Exception {
        UserSessionContext session = new UserSessionContext();
        session.signIn(1, "audit-acceptance", Set.of());
        ConnectionManager.installSessionInitializer(
                new AuditSessionInitializer(session, "acceptance-machine", "AUDIT-TEST")::initialize);

        int customerId = insertCustomer("audit-app-" + UUID.randomUUID());
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE custom SET notes = 'changed' WHERE id = ?")) {
            statement.setInt(1, customerId);
            assertEquals(1, statement.executeUpdate());
        }
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM custom WHERE id = ?")) {
            statement.setInt(1, customerId);
            assertEquals(1, statement.executeUpdate());
        }

        AuditLogPage page = new JdbcAuditLogRepository().load(queryFor("custom", customerId));
        List<AuditLogEntry> rows = page.rows().stream()
                .filter(row -> row.recordId().equals(String.valueOf(customerId)))
                .toList();

        assertEquals(3, rows.size());
        assertEquals(Set.of(AuditAction.INSERT, AuditAction.UPDATE, AuditAction.DELETE),
                rows.stream().map(AuditLogEntry::action).collect(java.util.stream.Collectors.toSet()));
        rows.forEach(row -> {
            assertEquals(1, row.actorUserId());
            assertEquals("audit-acceptance", row.actorName());
            assertEquals("APP", row.source());
            assertEquals("acceptance-machine", row.workstationId());
            assertEquals("AUDIT-TEST", row.workstationName());
        });
        assertTrue(new JdbcAuditLogRepository().options().tables().contains("CUSTOM"));
    }

    @Test
    void directDatabaseWritesAreNotMisreportedAsApplicationWork() throws Exception {
        int customerId;
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO custom (name, limit_num, user_id) VALUES (?, 0, 1)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "audit-database-" + UUID.randomUUID());
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                customerId = keys.getInt(1);
            }
        }

        AuditLogEntry row = new JdbcAuditLogRepository().load(queryFor("custom", customerId)).rows().stream()
                .filter(entry -> entry.recordId().equals(String.valueOf(customerId)))
                .filter(entry -> entry.action() == AuditAction.INSERT)
                .findFirst()
                .orElseThrow();

        assertEquals("DATABASE", row.source());
        assertNull(row.actorUserId());
        assertFalse(row.actorName().isBlank());
        assertTrue(row.workstationId().isBlank());
        assertTrue(row.workstationName().isBlank());
        assertNotNull(row.actionTime());
    }

    @Test
    void directAuthorizationAssignmentsAreCoveredByDatabaseAuditTriggers() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int roleId;
        int permissionId;
        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password)) {
            try (PreparedStatement permission = connection.prepareStatement(
                    "SELECT id FROM auth_permission WHERE permission_key = 'audit.view'");
                 ResultSet row = permission.executeQuery()) {
                assertTrue(row.next());
                permissionId = row.getInt(1);
            }
            try (PreparedStatement role = connection.prepareStatement("""
                    INSERT INTO auth_role(role_code, role_name, description, system_role, active, created_by)
                    VALUES (?, ?, 'acceptance', 0, 1, NULL)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                role.setString(1, "AUDIT_ACCEPTANCE_" + suffix);
                role.setString(2, "Audit acceptance " + suffix);
                assertEquals(1, role.executeUpdate());
                try (ResultSet keys = role.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    roleId = keys.getInt(1);
                }
            }
            try (PreparedStatement grant = connection.prepareStatement("""
                    INSERT INTO auth_role_permission(role_id, permission_id, granted_by)
                    VALUES (?, ?, NULL)
                    """)) {
                grant.setInt(1, roleId);
                grant.setInt(2, permissionId);
                assertEquals(1, grant.executeUpdate());
            }
        }

        String recordId = roleId + ":" + permissionId;
        LocalDate today = LocalDate.now();
        AuditLogEntry grant = new JdbcAuditLogRepository().load(new AuditLogQuery(recordId,
                        today.minusDays(1), today.plusDays(1), null, AuditActionFilter.ALL,
                        "auth_role_permission", AuditSourceFilter.ALL, AuditLogSort.NEWEST, 0, 50))
                .rows().stream()
                .filter(row -> recordId.equals(row.recordId()))
                .filter(row -> row.action() == AuditAction.INSERT)
                .findFirst().orElseThrow();

        assertEquals("DATABASE", grant.source());
        assertNull(grant.actorUserId());
        assertTrue(grant.newData().contains("permission_id"));

        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password);
             PreparedStatement cleanup = connection.prepareStatement("DELETE FROM auth_role WHERE id = ?")) {
            cleanup.setInt(1, roleId);
            assertEquals(1, cleanup.executeUpdate());
        }
    }

    private static int insertCustomer(String name) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO custom (name, limit_num, user_id) VALUES (?, 0, 1)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static AuditLogQuery queryFor(String table, int recordId) {
        LocalDate today = LocalDate.now();
        return new AuditLogQuery(String.valueOf(recordId), today.minusDays(1), today.plusDays(1),
                null, AuditActionFilter.ALL, table, AuditSourceFilter.ALL,
                AuditLogSort.NEWEST, 0, 50);
    }

    private static int scalarInt(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static void updateAdminEvent(long id) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE audit_admin_event SET reason = 'changed' WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static void deleteAdminEvent(long id) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM audit_admin_event WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
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
}
