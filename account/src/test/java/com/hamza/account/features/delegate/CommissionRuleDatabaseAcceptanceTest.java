package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V70 and the commission rules against a real MySQL - the only place four of their claims exist.
 *
 * <ul>
 *   <li><b>That V70 applies at all</b>, to a schema built from nothing. A migration is only
 *       wrong when MySQL reads it; V55, V56, V57 and V65 each passed a green build and failed on
 *       the first database they met.</li>
 *   <li><b>The CHECKs</b>, and in particular that a tier with a threshold and no rate is refused:
 *       a MySQL CHECK passes when it evaluates to NULL, and nothing in Java can show whether the
 *       {@code IS NOT NULL} written for that is enough.</li>
 *   <li><b>The grants</b> - that whoever could see a salary or change one holds the new keys.</li>
 *   <li><b>What is inside the transaction</b>: the delegate check and the write, including that
 *       saving a rule unchanged answers 1 rather than colliding with its own unique key.</li>
 * </ul>
 *
 * <b>One scratch schema and nothing else.</b> It is created here, migrated from empty, and dropped
 * in {@code @AfterAll}; the configured database is a credential carrier and is never opened. When
 * the application account cannot create schemas, supply {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_USER}
 * and {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD}; {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} names the
 * config file outright, which is how this runs from a worktree that deliberately has none.
 *
 * <p><b>The session is never user 1</b>, who bypasses every permission.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommissionRuleDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_commission_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "COM-" + System.nanoTime();

    private static final CommissionRuleService SERVICE = new CommissionRuleService();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int delegateId;
    private static int otherDelegateId;
    private static int clerkId;

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
            delegateId = seedEmployee(STAMP + "-D1", "(SELECT MIN(id) FROM jobs WHERE is_delegate = 1)");
            otherDelegateId = seedEmployee(STAMP + "-D2", "(SELECT MIN(id) FROM jobs WHERE is_delegate = 1)");
            clerkId = seedEmployee(STAMP + "-C", "(SELECT MIN(id) FROM jobs WHERE is_delegate = 0)");
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

    // ---- the migration -------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("from nothing: the table, its seven checks, the two permissions, and no helper left behind")
    void freshInstall() throws Exception {
        // Not "the head is 70": the next migration would fail this test for being written.
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '70' AND success = 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.tables"
                + " WHERE table_schema = DATABASE() AND table_name = 'employee_commission_rule'"));
        assertEquals(7, scalar("SELECT COUNT(*) FROM information_schema.table_constraints"
                + " WHERE table_schema = DATABASE() AND table_name = 'employee_commission_rule'"
                + " AND constraint_type = 'CHECK'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM auth_permission"
                + " WHERE permission_key IN ('commission.show', 'commission.rule.update') AND enabled = 1"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines"
                + " WHERE routine_schema = DATABASE() AND routine_name LIKE 'add\\_%'"),
                "a stray helper is what the next migration calls and finds missing in the field");
    }

    @Test
    @Order(2)
    @DisplayName("whoever could see a salary sees a rate, and whoever could change one sets a rule - nobody else")
    void theGrantsFollowTheSalaryPermissions() throws Exception {
        assertEquals(0, scalar(rolesHolding("employees.show.salary") + " AND role_id NOT IN ("
                + roleIdsHolding("commission.show") + ")"), "a role that lost nothing on upgrade");
        assertEquals(0, scalar(rolesHolding("commission.show") + " AND role_id NOT IN ("
                + roleIdsHolding("employees.show.salary") + ")"), "and none that gained it from nowhere");
        assertEquals(0, scalar(rolesHolding("employee.salary.change") + " AND role_id NOT IN ("
                + roleIdsHolding("commission.rule.update") + ")"));
        assertEquals(0, scalar(rolesHolding("commission.rule.update") + " AND role_id NOT IN ("
                + roleIdsHolding("employee.salary.change") + ")"));
        assertTrue(scalar(rolesHolding("commission.rule.update")) > 0,
                "the default roles hold the salary permission, so the grant had somebody to reach");
    }

    // ---- the service, inside its transaction ----------------------------------------------

    @Test
    @Order(3)
    @DisplayName("a rule is written, read back with its tiers, and entered by the operator - not by user 1")
    void aRuleIsWrittenAndReadBack() throws Exception {
        assertEquals(1, SERVICE.save(delegateId, LocalDate.of(2026, 9, 1), CommissionBasis.COLLECTED,
                TierMode.MARGINAL, new BigDecimal("100000"), tiers("50", "1", "80", "2.5", "100", "3"), STAMP));

        CommissionRule stored = SERVICE.history(delegateId).get(0);
        assertEquals(CommissionBasis.COLLECTED, stored.basis());
        assertEquals(TierMode.MARGINAL, stored.tierMode());
        assertEquals(0, new BigDecimal("100000").compareTo(stored.target()));
        assertEquals(3, stored.tiers().tiers().size());
        assertEquals(0, new BigDecimal("2.5").compareTo(stored.tiers().tiers().get(1).ratePercent()));
        assertEquals(new BigDecimal("1400.00"), stored.calculate(new BigDecimal("120000")).amount(),
                "300 + 500 + 600 - the stored rule computes what the typed one did");
        assertEquals(OPERATOR, scalar("SELECT user_id FROM employee_commission_rule WHERE id = " + stored.id()));
    }

    @Test
    @Order(4)
    @DisplayName("saving the same day again replaces that day's rule, and saving it unchanged still answers 1")
    void theSameDayIsAnAmendment() throws Exception {
        List<CommissionTiers.Tier> flat = tiers("0", "2");
        assertEquals(1, SERVICE.save(delegateId, LocalDate.of(2026, 9, 1), CommissionBasis.SALES,
                TierMode.WHOLE, BigDecimal.ZERO, flat, STAMP));
        // Unchanged: MySQL changes no row. The driver reports rows matched, so this must not
        // fall through to an INSERT and collide with the rule it just wrote.
        assertEquals(1, SERVICE.save(delegateId, LocalDate.of(2026, 9, 1), CommissionBasis.SALES,
                TierMode.WHOLE, BigDecimal.ZERO, flat, STAMP));

        assertEquals(1, SERVICE.history(delegateId).size());
        CommissionRule stored = SERVICE.history(delegateId).get(0);
        assertEquals(1, stored.tiers().tiers().size(), "the second and third tiers went back to NULL");
        assertEquals(0, scalar("SELECT COUNT(*) FROM employee_commission_rule WHERE employee_id = "
                + delegateId + " AND (tier2_from IS NOT NULL OR tier3_rate IS NOT NULL)"));
    }

    @Test
    @Order(5)
    @DisplayName("a month is judged by the rule of its first day: not a later one, and not one dated in the future")
    void theRuleOfAMonth() throws Exception {
        SERVICE.save(delegateId, LocalDate.of(2026, 10, 10), CommissionBasis.SALES, TierMode.WHOLE,
                BigDecimal.ZERO, tiers("0", "5"), STAMP);
        SERVICE.save(delegateId, LocalDate.of(2027, 1, 1), CommissionBasis.SALES, TierMode.WHOLE,
                BigDecimal.ZERO, tiers("0", "9"), STAMP);

        assertTrue(SERVICE.ruleForMonth(delegateId, 2026, 8).isEmpty(), "before his first rule he had none");
        assertEquals(new BigDecimal("2.00"), rateIn(2026, 9));
        assertEquals(new BigDecimal("2.00"), rateIn(2026, 10),
                "the rule of the 10th was not in force on the 1st, so October is still September's");
        assertEquals(new BigDecimal("5.00"), rateIn(2026, 11));
        assertEquals(new BigDecimal("9.00"), rateIn(2027, 1));
    }

    @Test
    @Order(6)
    @DisplayName("an employee whose job is not a delegate's gets no rule, and nothing is written")
    void onlyADelegateHasARule() throws Exception {
        assertEquals("commission.error.not.delegate", assertThrows(UserValidationException.class,
                () -> SERVICE.save(clerkId, LocalDate.of(2026, 9, 1), CommissionBasis.SALES, TierMode.WHOLE,
                        BigDecimal.ZERO, tiers("0", "2"), STAMP)).getMessage());
        assertEquals(0, scalar("SELECT COUNT(*) FROM employee_commission_rule WHERE employee_id = " + clerkId));
    }

    @Test
    @Order(7)
    @DisplayName("a rule id from one delegate's screen removes nothing of another's")
    void aDeleteNamesItsDelegate() throws Exception {
        int ruleId = SERVICE.history(delegateId).get(0).id();
        assertEquals(0, SERVICE.remove(otherDelegateId, ruleId));
        assertEquals(3, SERVICE.history(delegateId).size());
        assertEquals(1, SERVICE.remove(delegateId, ruleId));
        assertEquals(2, SERVICE.history(delegateId).size());
    }

    // ---- what the database refuses by itself -----------------------------------------------

    @Test
    @Order(8)
    @DisplayName("the CHECKs refuse what the record refuses - including the half tier a NULL would let through")
    void theChecksHold() {
        String ok = "50, 1, 80, 2, 100, 3";
        assertAccepted(100000, ok);
        // A MySQL CHECK passes on NULL: without the IS NOT NULL in V70 both of these are accepted.
        assertRefused(100000, "50, 1, 80, NULL, NULL, NULL", "a threshold with no rate");
        assertRefused(100000, "50, 1, NULL, 2, NULL, NULL", "a rate with no threshold");
        assertRefused(100000, "50, 1, NULL, NULL, 100, 3", "a third tier with no second");
        assertRefused(100000, "50, 1, 50, 2, NULL, NULL", "two tiers at one threshold");
        assertRefused(100000, "80, 1, 50, 2, NULL, NULL", "thresholds that fall");
        assertRefused(100000, "0, 101, NULL, NULL, NULL, NULL", "a rate above a hundred");
        assertRefused(100000, "-1, 1, NULL, NULL, NULL, NULL", "a negative threshold");
        assertRefused(-1, "0, 1, NULL, NULL, NULL, NULL", "a negative target");
        assertRefused(0, "50, 1, NULL, NULL, NULL, NULL", "no target and a threshold nobody can reach");
        assertRefused(0, "0, 1, 50, 2, NULL, NULL", "no target and more than a flat rate");
    }

    @Test
    @Order(9)
    @DisplayName("an unknown basis or mode is refused by the database too")
    void theListsAreClosed() {
        assertThrows(SQLException.class, () -> execute("INSERT INTO employee_commission_rule"
                + " (employee_id, effective_from, basis, tier1_from, tier1_rate) VALUES ("
                + otherDelegateId + ", '2031-01-01', 'PROFIT', 0, 1)"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO employee_commission_rule"
                + " (employee_id, effective_from, tier_mode, tier1_from, tier1_rate) VALUES ("
                + otherDelegateId + ", '2031-01-01', 'STEPPED', 0, 1)"));
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static int checkedDay = 1;

    private static String insertRule(int target, String tierColumns) {
        return "INSERT INTO employee_commission_rule (employee_id, effective_from, target,"
                + " tier1_from, tier1_rate, tier2_from, tier2_rate, tier3_from, tier3_rate) VALUES ("
                + otherDelegateId + ", DATE_ADD('2030-01-01', INTERVAL " + (checkedDay++) + " DAY), "
                + target + ", " + tierColumns + ")";
    }

    private static void assertAccepted(int target, String tierColumns) {
        try {
            execute(insertRule(target, tierColumns));
        } catch (Exception refused) {
            throw new AssertionError("a valid rule was refused: " + tierColumns, refused);
        }
    }

    private static void assertRefused(int target, String tierColumns, String what) {
        SQLException refusal = assertThrows(SQLException.class,
                () -> execute(insertRule(target, tierColumns)), what + " was accepted");
        assertTrue(refusal.getMessage().contains("employee_commission_rule_"),
                what + " was refused by something other than a CHECK of this table: " + refusal.getMessage());
    }

    private static BigDecimal rateIn(int year, int month) throws Exception {
        Optional<CommissionRule> rule = SERVICE.ruleForMonth(delegateId, year, month);
        assertTrue(rule.isPresent(), year + "-" + month);
        return rule.get().tiers().tiers().get(0).ratePercent();
    }

    private static List<CommissionTiers.Tier> tiers(String... fromAndRate) {
        List<CommissionTiers.Tier> list = new java.util.ArrayList<>();
        for (int i = 0; i < fromAndRate.length; i += 2) {
            list.add(new CommissionTiers.Tier(new BigDecimal(fromAndRate[i]), new BigDecimal(fromAndRate[i + 1])));
        }
        return list;
    }

    private static String rolesHolding(String key) {
        return "SELECT COUNT(DISTINCT role_id) FROM auth_role_permission rp"
                + " JOIN auth_permission p ON p.id = rp.permission_id WHERE p.permission_key = '" + key + "'";
    }

    private static String roleIdsHolding(String key) {
        return "SELECT rp2.role_id FROM auth_role_permission rp2"
                + " JOIN auth_permission p2 ON p2.id = rp2.permission_id WHERE p2.permission_key = '" + key + "'";
    }

    private static int seedEmployee(String name, String jobSql) throws Exception {
        execute("INSERT INTO employees (column_name, job, hire_date, salary, user_id) VALUES ('"
                + name + "', " + jobSql + ", '2025-01-01', 3000, 1)");
        return scalar("SELECT id FROM employees WHERE column_name = '" + name + "'");
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
