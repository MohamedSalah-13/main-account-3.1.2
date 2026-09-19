package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V73 and the delegate's discount ceiling against a real MySQL.
 *
 * <ul>
 *   <li><b>That V73 applies</b> to a schema built from nothing - an inline CHECK inside a
 *       prepared {@code ALTER TABLE} is exactly the kind of text only MySQL can judge.</li>
 *   <li><b>That nobody is given a ceiling</b> by the column arriving, and that the override went
 *       to whoever sets a ceiling and to no role that merely sells.</li>
 *   <li><b>The write</b>: a ceiling stored, rounded, read back, set to zero, and removed - NULL
 *       bound through {@code executeUpdate} - and the CHECK behind the service.</li>
 *   <li><b>The guard over JDBC and the real session</b>: refused without the override, allowed
 *       with it.</li>
 * </ul>
 *
 * <b>Not claimed:</b> a whole invoice save refused end to end on MySQL. The guard's place in the
 * save - before a number is taken, before any write - is {@code InvoiceSaveServiceTest}'s, against
 * mocks; here the guard is driven directly.
 *
 * <p>One scratch schema, created here, migrated from empty and dropped in {@code @AfterAll}; the
 * configured database is a credential carrier and is never opened.
 * {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} names the config file, which is how this runs from a
 * worktree. <b>The session is never user 1</b>, who bypasses every permission.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DiscountCeilingDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_ceiling_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "CEIL-" + System.nanoTime();

    private static final DiscountCeilingService SERVICE = new DiscountCeilingService();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int delegateId;

    @BeforeAll
    static void migrateAScratchSchemaFromNothing() throws Exception {
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configSource().getAbsolutePath());
        host = config.get(CryptoDatabaseConfig.HOST);
        port = config.get(CryptoDatabaseConfig.PORT);
        username = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_USER", config.get(CryptoDatabaseConfig.USERNAME));
        password = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD", config.get(CryptoDatabaseConfig.PASSWORD));
        schema = SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        try {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            }
            Flyway.configure()
                    .dataSource(jdbcUrl(schema), username, password)
                    .locations("classpath:db/migration")
                    .validateOnMigrate(false)
                    .cleanDisabled(true)
                    .load()
                    .migrate();

            DataSourceProvider.initialize(host, port, schema, username, password);
            execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                    + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
            execute("INSERT INTO employees (column_name, job, hire_date, salary, user_id) VALUES ('"
                    + STAMP + "', (SELECT MIN(id) FROM jobs WHERE is_delegate = 1), '2025-01-01', 3000, 1)");
            delegateId = scalar("SELECT id FROM employees WHERE column_name = '" + STAMP + "'");
            signIn(AppPermissions.COMMISSION_SHOW, AppPermissions.COMMISSION_RULE_UPDATE);
        } catch (Exception failure) {
            try {
                dropTheScratchSchema();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @AfterAll
    static void dropTheScratchSchema() throws Exception {
        DataSourceProvider.shutdown();
        if (schema == null || !schema.startsWith(SCHEMA_PREFIX)) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    @Test
    @Order(1)
    @DisplayName("from nothing: a nullable column with its check, the permission at HIGH, no helper left behind")
    void freshInstall() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '73' AND success = 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = 'employees' AND column_name = 'max_discount_percent'"
                + " AND is_nullable = 'YES' AND column_default IS NULL"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM auth_permission WHERE permission_key = 'sales.discount.override'"
                + " AND risk_level = 'HIGH' AND enabled = 1"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines"
                + " WHERE routine_schema = DATABASE() AND routine_name LIKE 'add\\_%'"));
    }

    @Test
    @Order(2)
    @DisplayName("the column arriving gives nobody a ceiling")
    void nobodyHasACeilingAfterTheMigration() throws Exception {
        assertTrue(scalar("SELECT COUNT(*) FROM employees") > 0);
        assertEquals(0, scalar("SELECT COUNT(*) FROM employees WHERE max_discount_percent IS NOT NULL"));
    }

    @Test
    @Order(3)
    @DisplayName("the override went to whoever sets a ceiling, and to no role for merely selling")
    void theGrantFollowsTheRulePermission() throws Exception {
        assertTrue(scalar(rolesHolding("sales.discount.override")) > 0, "the grant had somebody to reach");
        assertEquals(0, scalar(rolesHolding("commission.rule.update") + " AND role_id NOT IN ("
                + roleIdsHolding("sales.discount.override") + ")"));
        assertEquals(0, scalar(rolesHolding("sales.discount.override") + " AND role_id NOT IN ("
                + roleIdsHolding("commission.rule.update") + ")"));
        assertTrue(scalar(rolesHolding("sales.create") + " AND role_id NOT IN ("
                        + roleIdsHolding("sales.discount.override") + ")") > 0,
                "at least one default role sells without the override, or a ceiling stops nobody");
    }

    @Test
    @Order(4)
    @DisplayName("a ceiling is stored rounded, read back, and zero is a ceiling rather than none")
    void aCeilingIsWrittenAndReadBack() throws Exception {
        assertTrue(SERVICE.ceilingOf(delegateId).isEmpty());

        assertEquals(1, SERVICE.update(delegateId, new BigDecimal("12.345")));
        assertEquals(0, new BigDecimal("12.35").compareTo(SERVICE.ceilingOf(delegateId).orElseThrow().maxPercent()));

        assertEquals(1, SERVICE.update(delegateId, new BigDecimal("12.345")), "saved unchanged answers 1");

        assertEquals(1, SERVICE.update(delegateId, BigDecimal.ZERO));
        assertEquals(0, SERVICE.ceilingOf(delegateId).orElseThrow().maxPercent().signum());
    }

    @Test
    @Order(5)
    @DisplayName("an empty box removes the ceiling: NULL, not zero")
    void aCeilingIsRemoved() throws Exception {
        SERVICE.update(delegateId, new BigDecimal("10"));
        assertEquals(1, SERVICE.update(delegateId, null));
        assertTrue(SERVICE.ceilingOf(delegateId).isEmpty());
        assertEquals(1, scalar("SELECT COUNT(*) FROM employees WHERE id = " + delegateId
                + " AND max_discount_percent IS NULL"));
    }

    @Test
    @Order(6)
    @DisplayName("an employee that does not exist is a refusal with a sentence, and SQL behind the service is checked too")
    void theRefusals() throws Exception {
        UserValidationException refusal = assertThrows(UserValidationException.class,
                () -> SERVICE.update(987_654, new BigDecimal("10")));
        assertEquals("delegate.ceiling.error.employee", refusal.getMessage());

        for (String bad : new String[]{"100.01", "-0.01"}) {
            SQLException refused = assertThrows(SQLException.class, () -> execute(
                    "UPDATE employees SET max_discount_percent = " + bad + " WHERE id = " + delegateId));
            assertTrue(refused.getMessage().toLowerCase().contains("check"), refused.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("over JDBC and the real session: refused without the override, allowed with it, never for a return")
    void theGuard() throws Exception {
        SERVICE.update(delegateId, new BigDecimal("10"));
        DelegateDiscountGuard guard = DelegateDiscountGuard.jdbc();
        BigDecimal gross = new BigDecimal("1000");
        try {
            signIn(AppPermissions.SALES_CREATE);
            assertTrue(guard.refuses(DocumentType.SALES, delegateId, gross, new BigDecimal("60"), new BigDecimal("41")));
            assertFalse(guard.refuses(DocumentType.SALES, delegateId, gross, new BigDecimal("60"), new BigDecimal("40")));
            assertFalse(guard.refuses(DocumentType.SALES_RETURN, delegateId, gross, gross, BigDecimal.ZERO));

            signIn(AppPermissions.SALES_CREATE, AppPermissions.SALES_DISCOUNT_OVERRIDE);
            assertFalse(guard.refuses(DocumentType.SALES, delegateId, gross, new BigDecimal("60"), new BigDecimal("41")));
        } finally {
            signIn(AppPermissions.COMMISSION_SHOW, AppPermissions.COMMISSION_RULE_UPDATE);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static String rolesHolding(String key) {
        return "SELECT COUNT(DISTINCT role_id) FROM auth_role_permission rp"
                + " JOIN auth_permission p ON p.id = rp.permission_id WHERE p.permission_key = '" + key + "'";
    }

    private static String roleIdsHolding(String key) {
        return "SELECT rp2.role_id FROM auth_role_permission rp2"
                + " JOIN auth_permission p2 ON p2.id = rp2.permission_id WHERE p2.permission_key = '" + key + "'";
    }

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static int scalar(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getInt(1);
        }
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=LOCAL";
    }

    private static File configSource() {
        String named = System.getenv("ACCOUNT_DB_ACCEPTANCE_CONFIG");
        if (named != null && !named.isBlank()) {
            File explicit = new File(named);
            assertTrue(explicit.isFile(), "ACCOUNT_DB_ACCEPTANCE_CONFIG names no file: " + named);
            return explicit;
        }
        File beside = new File("config.xml");
        return beside.isFile() ? beside : new File("../config.xml");
    }

    private static String environmentOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
