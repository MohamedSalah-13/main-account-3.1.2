package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.payroll.AttendanceSource;
import com.hamza.account.features.employee.payroll.JdbcPayrollRepository;
import com.hamza.account.features.employee.payroll.PayrollLine;
import com.hamza.account.features.employee.payroll.PayrollPeriod;
import com.hamza.account.features.employee.payroll.PayrollService;
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
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase C against a real MySQL - the run, the freeze, and "once", none of which exists anywhere
 * else.
 *
 * <ul>
 *   <li><b>V72 applies</b> to a schema built from nothing, with the repeatable triggers after it.</li>
 *   <li><b>The freeze is the database's</b>: a line refuses an UPDATE always and a DELETE outside
 *       a wipe - and under {@code @app_bulk_wipe} the DELETE passes while the UPDATE <b>still does
 *       not</b>, the distinction V43 missed.</li>
 *   <li><b>One approved run per month</b> is a unique key over a generated column, which lets
 *       cancelled runs repeat. Only MySQL can say whether that works.</li>
 *   <li><b>"Once" is a primary key.</b> A line the payroll paid is not posted to the account, a
 *       line posted to the account is not due to the payroll, and a second posting by hand
 *       meets the key.</li>
 *   <li><b>The real payroll</b>: {@code PayrollService} builds a draft that carries the approved
 *       commission, and approving it writes one entitlement that includes it and marks the line.</li>
 * </ul>
 *
 * One scratch schema, created here and dropped in {@code @AfterAll}; the configured database is a
 * credential carrier and is never opened. <b>The session is never user 1.</b>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommissionRunDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_commrun_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "RUN-" + System.nanoTime();
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate IN_NOVEMBER = LocalDate.of(2026, 11, 3);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    private static CommissionRunService runs;
    private static int onSales;
    private static int onCollected;
    private static int customer;
    private static int firstRun;
    private static int secondRun;

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
            Flyway.configure().dataSource(jdbcUrl(schema), username, password)
                    .locations("classpath:db/migration").validateOnMigrate(false).cleanDisabled(true)
                    .load().migrate();
            DataSourceProvider.initialize(host, port, schema, username, password);
            seed();
            runs = new CommissionRunService(new JdbcCommissionRunRepository(), new JdbcDelegateActivityRepository(),
                    new JdbcCommissionRuleRepository(), () -> IN_NOVEMBER);
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

    // ---- the migration -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("from nothing: three tables, six triggers, three permissions, and no helper left behind")
    void freshInstall() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '72' AND success = 1"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
                + " AND table_name IN ('commission_run', 'commission_line', 'commission_posting')"));
        assertEquals(6, scalar("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = DATABASE()"
                + " AND event_object_table IN ('commission_run', 'commission_line', 'commission_posting')"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM auth_permission WHERE permission_key LIKE 'commission.run.%'"));
        for (String key : List.of("commission.run.create", "commission.run.update", "commission.run.post")) {
            assertEquals(0, scalar(rolesHolding("commission.rule.update") + " AND role_id NOT IN ("
                    + roleIdsHolding(key) + ")"), key);
        }
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines"
                + " WHERE routine_schema = DATABASE() AND routine_name LIKE 'add\\_%'"));
    }

    // ---- approving -----------------------------------------------------------------------------

    /**
     * October by hand: the first delegate sold 900 net of a 1000 target - 90%, the 50% tier, 1% -
     * 9.00. The second collected 500 at a flat 2% - 10.00. "Direct sale" sold 200 and has no rule.
     */
    @Test
    @Order(2)
    @DisplayName("approving a month writes a line for each delegate with a rule, with what produced the figure")
    void approval() throws Exception {
        signIn(AppPermissions.COMMISSION_RUN_CREATE, AppPermissions.COMMISSION_SHOW);
        firstRun = runs.approve(OCTOBER, STAMP);

        List<CommissionLine> lines = runs.linesOf(firstRun);
        assertEquals(2, lines.size(), "no line for a delegate with no rule");
        CommissionLine sales = lineOf(lines, onSales);
        assertMoney("900.00", sales.baseAmount());
        assertMoney("90.00", sales.achievementPercent());
        assertEquals(1, sales.tier());
        assertMoney("9.00", sales.amount());
        assertEquals("50:1|100:2", sales.tiersSnapshot());
        assertEquals(CommissionLine.Posting.NONE, sales.posting());
        assertMoney("10.00", lineOf(lines, onCollected).amount());

        CommissionRun stored = runs.runs().get(0);
        assertEquals(CommissionRun.Status.APPROVED, stored.status());
        assertEquals(2, stored.lines());
        assertMoney("19.00", stored.total());
        assertEquals(OPERATOR, scalar("SELECT user_id FROM commission_run WHERE id = " + firstRun),
                "approved by the operator, not by user 1");
    }

    @Test
    @Order(3)
    @DisplayName("a month has one approved run: the service says so, and the unique key says so without it")
    void oneApprovedRunPerMonth() {
        signIn(AppPermissions.COMMISSION_RUN_CREATE, AppPermissions.COMMISSION_SHOW);
        assertEquals("commission.error.run.exists", assertThrows(UserValidationException.class,
                () -> runs.approve(OCTOBER, STAMP)).getMessage());
        SQLException refusal = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO commission_run (period_year, period_month, user_id) VALUES (2026, 10, " + OPERATOR + ")"));
        assertTrue(refusal.getMessage().contains("commission_run_active_uk"), refusal.getMessage());
    }

    // ---- the freeze ----------------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("a line refuses an UPDATE always, and a DELETE outside a wipe - and the wipe escape covers the DELETE only")
    void theFreeze() throws Exception {
        int line = scalar("SELECT MIN(id) FROM commission_line WHERE run_id = " + firstRun);
        assertThrows(SQLException.class, () -> execute("UPDATE commission_line SET amount = 99 WHERE id = " + line));
        assertThrows(SQLException.class, () -> execute("DELETE FROM commission_line WHERE id = " + line));
        assertThrows(SQLException.class, () -> execute("DELETE FROM commission_run WHERE id = " + firstRun));
        assertThrows(SQLException.class,
                () -> execute("UPDATE commission_run SET period_month = 9 WHERE id = " + firstRun),
                "the run moves in one way only, and its period never");

        try (Connection connection = DriverManager.getConnection(jdbcUrl(schema), username, password);
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SET @app_bulk_wipe = 1");
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate("UPDATE commission_line SET amount = 99 WHERE id = " + line),
                    "nothing holding the wipe flag may change a line either");
            assertEquals(1, statement.executeUpdate("DELETE FROM commission_line WHERE id = " + line));
            connection.rollback();
        }
        assertMoney("19.00", decimal("SELECT SUM(amount) FROM commission_line WHERE run_id = " + firstRun));
    }

    @Test
    @Order(5)
    @DisplayName("a rule a month was computed under is history: not amended in place, not removed")
    void aUsedRuleIsHistory() throws Exception {
        signIn(AppPermissions.COMMISSION_RULE_UPDATE, AppPermissions.COMMISSION_SHOW);
        CommissionRuleService rules = new CommissionRuleService();
        assertEquals("commission.error.rule.used", assertThrows(UserValidationException.class,
                () -> rules.save(onSales, LocalDate.of(2026, 9, 1), CommissionBasis.SALES, TierMode.WHOLE,
                        new BigDecimal("5000"), List.of(tier("0", "9")), STAMP)).getMessage());
        int ruleId = rules.history(onSales).get(0).id();
        assertEquals("commission.error.rule.used", assertThrows(UserValidationException.class,
                () -> rules.remove(onSales, ruleId)).getMessage());
        assertMoney("1000.00", decimal("SELECT target FROM employee_commission_rule WHERE id = " + ruleId));

        // The road that exists: a new rule, in force from a later month.
        assertEquals(1, rules.save(onSales, LocalDate.of(2026, 12, 1), CommissionBasis.SALES, TierMode.WHOLE,
                new BigDecimal("2000"), List.of(tier("50", "1")), STAMP));
    }

    /** The defect this phase exists to end: a figure computed again at every reading. */
    @Test
    @Order(6)
    @DisplayName("an invoice entered late moves the live report and leaves the approved line where it was")
    void theApprovedFigureDoesNotFollowTheData() throws Exception {
        invoice(9010, "2026-10-20", "200", "0", onSales);

        signIn(AppPermissions.COMMISSION_SHOW);
        assertMoney("9.00", lineOf(runs.linesOf(firstRun), onSales).amount());
        assertMoney("22.00", lineOf(runs.preview(OCTOBER), onSales).amount(),
                "1100 of 1000 is the 100% tier at 2% - what a fresh computation says now");
    }

    // ---- cancelling ----------------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("an unposted run is cancelled with a reason, and the month can then be approved again")
    void cancelAndApproveAgain() throws Exception {
        signIn(AppPermissions.COMMISSION_RUN_UPDATE, AppPermissions.COMMISSION_RUN_CREATE, AppPermissions.COMMISSION_SHOW);
        assertEquals(1, runs.cancel(firstRun, STAMP + " a late invoice"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM commission_run WHERE id = " + firstRun
                + " AND status = 'CANCELLED' AND cancelled_by = " + OPERATOR + " AND active_key IS NULL"));

        secondRun = runs.approve(OCTOBER, STAMP + " again");
        assertMoney("22.00", lineOf(runs.linesOf(secondRun), onSales).amount());
        assertEquals(2, scalar("SELECT COUNT(*) FROM commission_run WHERE period_year = 2026 AND period_month = 10"),
                "the cancelled run stays, as the record that it happened");
        assertEquals(2, scalar("SELECT COUNT(*) FROM commission_line WHERE run_id = " + firstRun),
                "and so do its lines");
    }

    // ---- once, by one road -----------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("the payroll is owed an approved commission from its period on, never before it")
    void whatThePayrollIsOwed() throws Exception {
        JdbcPayrollCommissionSource source = new JdbcPayrollCommissionSource();
        assertMoney("0.00", source.dueFor(onSales, new PayrollPeriod(2026, 9)));
        assertMoney("22.00", source.dueFor(onSales, new PayrollPeriod(2026, 10)));
        assertMoney("22.00", source.dueFor(onSales, new PayrollPeriod(2026, 11)),
                "October's commission is approved in November, and November's payroll finds it");
        assertMoney("10.00", source.dueFor(onCollected, new PayrollPeriod(2026, 10)));
    }

    /**
     * The real {@code PayrollService}. The second delegate is taken off October's payroll by an
     * end date, so his line is left for the other road.
     */
    @Test
    @Order(9)
    @DisplayName("the real payroll carries the commission on its draft, pays it inside the entitlement, and marks the line")
    void thePayrollRoad() throws Exception {
        execute("UPDATE employees SET end_date = '2026-09-30' WHERE id = " + onCollected);
        signIn(AppPermissions.PAYROLL_SHOW, AppPermissions.PAYROLL_CREATE, AppPermissions.PAYROLL_APPROVE);
        PayrollService payroll = new PayrollService(new JdbcPayrollRepository(), AttendanceSource.NONE,
                new JdbcPayrollCommissionSource());

        int payrollRun = payroll.createDraft(new PayrollPeriod(2026, 10), STAMP);
        PayrollLine line = payroll.linesOf(payrollRun).stream()
                .filter(each -> each.employeeId() == onSales).findFirst().orElseThrow();
        assertMoney("22.00", line.commission());

        payroll.approve(payrollRun);

        assertMoney("3022.00", decimal("SELECT amount FROM employee_ledger WHERE employee_id = " + onSales
                + " AND kind = 'ENTITLEMENT' AND payroll_run_id = " + payrollRun));
        assertEquals(1, scalar("SELECT COUNT(*) FROM commission_posting p JOIN commission_line l ON l.id = p.line_id"
                + " WHERE l.employee_id = " + onSales + " AND p.payroll_run_id = " + payrollRun
                + " AND p.ledger_entry_id IS NULL"));
        assertMoney("0.00", new JdbcPayrollCommissionSource().dueFor(onSales, new PayrollPeriod(2026, 11)));
        assertEquals(0, scalar("SELECT COUNT(*) FROM employee_ledger WHERE employee_id = " + onSales
                + " AND kind = 'COMMISSION'"), "the entitlement carried it; a COMMISSION row as well would pay it twice");
    }

    @Test
    @Order(10)
    @DisplayName("posting to the accounts takes only what the payroll did not, and a second press takes nothing")
    void theAccountRoad() throws Exception {
        signIn(AppPermissions.COMMISSION_RUN_POST, AppPermissions.COMMISSION_SHOW);
        assertEquals(1, runs.postToAccounts(secondRun), "the first delegate's line went with the payroll");

        assertEquals(1, scalar("SELECT COUNT(*) FROM employee_ledger WHERE employee_id = " + onCollected
                + " AND kind = 'COMMISSION' AND amount = 10.00 AND entry_date = '2026-10-31' AND user_id = " + OPERATOR));
        assertEquals(0, scalar("SELECT COUNT(*) FROM employee_ledger WHERE employee_id = " + onSales
                + " AND kind = 'COMMISSION'"));
        assertEquals(0, runs.postToAccounts(secondRun));

        List<CommissionLine> lines = runs.linesOf(secondRun);
        assertEquals(CommissionLine.Posting.PAYROLL, lineOf(lines, onSales).posting());
        assertEquals(CommissionLine.Posting.ACCOUNT, lineOf(lines, onCollected).posting());
        assertEquals(2, runs.runs().get(0).postedLines());
    }

    @Test
    @Order(11)
    @DisplayName("'once' and 'one road' are the database's: the primary key and the CHECK refuse by themselves")
    void onceIsAKey() throws Exception {
        int posted = scalar("SELECT MIN(line_id) FROM commission_posting");
        SQLException twice = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO commission_posting (line_id, payroll_run_id) VALUES (" + posted
                        + ", (SELECT MIN(id) FROM payroll_run))"));
        assertTrue(twice.getMessage().contains("PRIMARY") || twice.getMessage().contains("Duplicate"), twice.getMessage());

        int unposted = scalar("SELECT MIN(id) FROM commission_line WHERE run_id = " + firstRun);
        SQLException noRoad = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO commission_posting (line_id) VALUES (" + unposted + ")"));
        assertTrue(noRoad.getMessage().contains("commission_posting_one_route_chk"), noRoad.getMessage());
        SQLException bothRoads = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO commission_posting (line_id, payroll_run_id, ledger_entry_id) VALUES (" + unposted
                        + ", (SELECT MIN(id) FROM payroll_run), (SELECT MIN(id) FROM employee_ledger))"));
        assertTrue(bothRoads.getMessage().contains("commission_posting_one_route_chk"), bothRoads.getMessage());
        assertThrows(SQLException.class, () -> execute("UPDATE commission_posting SET posted_at = NOW()"));
    }

    @Test
    @Order(12)
    @DisplayName("a run with a posted line is not cancelled - by the service, nor by SQL behind it")
    void aPostedRunStays() throws Exception {
        signIn(AppPermissions.COMMISSION_RUN_UPDATE);
        assertEquals("commission.error.run.posted", assertThrows(UserValidationException.class,
                () -> runs.cancel(secondRun, "too late")).getMessage());
        SQLException refusal = assertThrows(SQLException.class, () -> execute(
                "UPDATE commission_run SET status = 'CANCELLED', cancelled_at = NOW(), cancelled_by = " + OPERATOR
                        + ", cancel_reason = 'behind the service' WHERE id = " + secondRun));
        assertTrue(refusal.getMessage().contains("posted line"), refusal.getMessage());
        assertEquals(1, scalar("SELECT COUNT(*) FROM commission_run WHERE id = " + secondRun + " AND status = 'APPROVED'"));
    }

    // ---- the statement --------------------------------------------------------------------------

    /**
     * The delegate's statement against the same database: one row for October - the approved run's,
     * not the cancelled one's - saying it went with the payroll, and a second late invoice showing as
     * a difference while the approved 22.00 stays where it was.
     */
    @Test
    @Order(13)
    @DisplayName("the statement lists the approved month once, where it went, and what it comes to today")
    void theStatement() throws Exception {
        signIn(AppPermissions.COMMISSION_SHOW);
        CommissionStatementService statements = new CommissionStatementService();

        List<CommissionStatementService.Row> before = statements.forDelegate(onSales);
        assertEquals(1, before.size(), "the cancelled run's line is not a second October");
        assertEquals(OCTOBER, before.get(0).period());
        assertMoney("22.00", before.get(0).approved().amount());
        assertMoney("22.00", before.get(0).live());
        assertEquals(CommissionLine.Posting.PAYROLL, before.get(0).approved().posting());

        invoice(9011, "2026-10-28", "1000", "0", onSales);
        CommissionStatementService.Row after = statements.forDelegate(onSales).get(0);
        assertMoney("22.00", after.approved().amount());
        assertMoney("42.00", after.live(), "2100 of a 1000 target is the 100% tier at 2%");
        assertMoney("20.00", after.difference());

        assertEquals(CommissionLine.Posting.ACCOUNT,
                statements.forDelegate(onCollected).get(0).approved().posting());
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        onSales = seedDelegate(STAMP + "-D1");
        onCollected = seedDelegate(STAMP + "-D2");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + STAMP + "-C', 0, 0, 1, 1, 1)");
        customer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-C'");

        invoice(9001, "2026-10-05", "1000", "100", onSales);
        invoice(9002, "2026-10-06", "500", "0", onCollected);
        invoice(9003, "2026-10-07", "200", "0", 1);

        signIn(AppPermissions.COMMISSION_RULE_UPDATE);
        CommissionRuleService rules = new CommissionRuleService();
        rules.save(onSales, LocalDate.of(2026, 9, 1), CommissionBasis.SALES, TierMode.WHOLE,
                new BigDecimal("1000"), List.of(tier("50", "1"), tier("100", "2")), STAMP);
        rules.save(onCollected, LocalDate.of(2026, 9, 1), CommissionBasis.COLLECTED, TierMode.WHOLE,
                BigDecimal.ZERO, List.of(tier("0", "2")), STAMP);
    }

    private static int seedDelegate(String name) throws Exception {
        execute("INSERT INTO employees (column_name, job, hire_date, salary, user_id) VALUES ('" + name
                + "', (SELECT MIN(id) FROM jobs WHERE is_delegate = 1), '2025-01-01', 3000, 1)");
        return scalar("SELECT id FROM employees WHERE column_name = '" + name + "'");
    }

    /** A cash invoice: paid in full, so it counts for both bases. */
    private static void invoice(int number, String date, String total, String discount, int delegate) throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + customer + ", 1, '" + date + "', "
                + total + ", " + discount + ", " + total + " - " + discount + ", " + delegate + ", '" + STAMP + "')");
    }

    private static CommissionLine lineOf(List<CommissionLine> lines, int employeeId) {
        return lines.stream().filter(line -> line.employeeId() == employeeId).findFirst()
                .orElseThrow(() -> new AssertionError("no line for employee " + employeeId));
    }

    private static CommissionTiers.Tier tier(String from, String rate) {
        return new CommissionTiers.Tier(new BigDecimal(from), new BigDecimal(rate));
    }

    private static void assertMoney(String expected, BigDecimal actual, String why) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                why + " - expected " + expected + " but was " + actual);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

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

    private static BigDecimal decimal(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getBigDecimal(1);
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
