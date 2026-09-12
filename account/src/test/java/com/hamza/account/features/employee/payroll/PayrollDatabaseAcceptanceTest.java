package com.hamza.account.features.employee.payroll;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The payroll against a real MySQL, which is the only place three of its rules exist.
 * <p>
 * What no unit test can reach, and what this class is for:
 * <ul>
 *   <li><b>The transaction</b> - approval moves the status and writes every ledger row, or it
 *       does neither. A fake repository cannot fail halfway.</li>
 *   <li><b>The freeze trigger</b> - a line of an approved run refuses an update, and the
 *       {@code @app_bulk_wipe} escape covers the delete and <b>not</b> the update. That
 *       distinction lives in {@code R__triggers.sql} and nowhere in Java.</li>
 *   <li><b>The candidate query</b> - who the month is built for, at which rate. Its rules
 *       (a raise dated in the past but not one dated in the future, an allowance whose window
 *       covers the month, somebody who left in it) are SQL, and SQL is the only thing that can
 *       be wrong about them.</li>
 * </ul>
 * It opens one transaction and rolls it back in a {@code finally}, then queries afterwards for
 * its own marks rather than trusting the rollback - the rule {@code CLAUDE.md} records after a
 * run left rows behind in a development database.
 * <p>
 * <b>The session is never user 1</b>: {@code isSystemAdministrator()} bypasses every permission.
 * The user row is seeded, because {@code payroll_run.user_id} is a foreign key - the defect the
 * first run of {@code EmployeeAccountDatabaseAcceptanceTest} found in itself.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PayrollDatabaseAcceptanceTest {

    private static final int OWNER = 1;
    private static final int OPERATOR = 9;
    private static final String STAMP = "PAY-" + System.nanoTime();
    private static final PayrollPeriod SEPTEMBER = new PayrollPeriod(2026, 9);

    private static final PayrollService SERVICE = new PayrollService(new JdbcPayrollRepository());

    private static Connection transaction;
    private static int monthlyId;
    private static int leaverId;
    private static int runId;

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) {
            configFile = new File("../config.xml");
        }
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));

        signIn(AppPermissions.PAYROLL_SHOW, AppPermissions.PAYROLL_CREATE,
                AppPermissions.PAYROLL_APPROVE, AppPermissions.PAYROLL_PAY);

        transaction = ConnectionManager.beginTransaction();
        seedOperator();
        monthlyId = seedEmployee(STAMP + "-M", "2025-01-01", null, "2000");
        leaverId = seedEmployee(STAMP + "-L", "2025-01-01", "2026-09-10", "4000");
        seedEmployee(STAMP + "-F", "2026-12-01", null, "5000");

        // A raise dated in the past and one dated in the future: September must read the first.
        insertCompensation(monthlyId, "2026-06-01", "2600");
        insertCompensation(monthlyId, "2027-01-01", "9999");
        // An allowance covering the month, and one that ended before it.
        insertAllowance(monthlyId, STAMP + "-now", "300", "2026-01-01", null);
        insertAllowance(monthlyId, STAMP + "-old", "500", "2024-01-01", "2025-12-31");
    }

    @AfterAll
    static void leaveNothingBehind() throws Exception {
        if (transaction != null) {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
        Connection connection = ConnectionManager.acquire();
        try {
            assertNoResidue(connection, "employees", "column_name LIKE '" + STAMP + "%'");
            assertNoResidue(connection, "users", "user_name LIKE '" + STAMP + "%'");
            assertNoResidue(connection, "employee_allowance",
                    "allowance_name LIKE '" + STAMP + "%'");
            assertNoResidue(connection, "payroll_run", "notes LIKE '" + STAMP + "%'");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    @Test
    @Order(1)
    @DisplayName("the draft is built for everyone employed in the month, at the rate effective in it")
    void theDraftPicksTheRightPeopleAtTheRightRate() throws Exception {
        runId = SERVICE.createDraft(SEPTEMBER, STAMP + " september");

        List<PayrollLine> lines = SERVICE.linesOf(runId);
        PayrollLine monthly = lineFor(lines, monthlyId);
        PayrollLine leaver = lineFor(lines, leaverId);

        assertEquals(money("2600.00"), monthly.rate(),
                "June's raise, not the original 2000 and not the 9999 dated 2027");
        assertEquals(money("300.00"), monthly.allowances(),
                "the allowance covering the month; the one that ended in 2025 is not counted");
        assertEquals(money("2900.00"), monthly.netPay(), "2600 + 300");

        assertEquals(money("1333.33"), leaver.basic(),
                "somebody who left on the 10th of a 30-day month is owed ten days of 4000");

        assertTrue(lines.stream().noneMatch(l -> l.employeeName().endsWith("-F")),
                "somebody hired in December is not on September's payroll");
    }

    @Test
    @Order(2)
    @DisplayName("a month may not have two runs")
    void oneRunPerMonth() {
        assertEquals("payroll.error.period.exists",
                assertThrows(UserValidationException.class,
                        () -> SERVICE.createDraft(SEPTEMBER, STAMP + " again")).getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("a draft line may still be corrected")
    void aDraftIsEditable() throws Exception {
        assertEquals(1, update("UPDATE payroll_line SET deductions = 100 WHERE payroll_run_id = "
                + runId + " AND employee_id = " + monthlyId));
    }

    @Test
    @Order(4)
    @DisplayName("approval moves the status and writes the ledger rows together")
    void approvalWritesEverything() throws Exception {
        SERVICE.approve(runId);

        assertEquals(PayrollRunStatus.APPROVED,
                SERVICE.findRun(runId).orElseThrow().status());

        // Two rows for the employee whose line carries a deduction, one for everyone else.
        assertEquals(money("2900.00"), ledgerAmount(monthlyId, "ENTITLEMENT"),
                "the whole of what was earned, never the net");
        assertEquals(money("100.00"), ledgerAmount(monthlyId, "DEDUCTION"));
        assertEquals(0, countLedger(leaverId, "DEDUCTION"),
                "a line with nothing deducted writes no zero deduction");

        assertEquals("2026-09-30", ledgerDate(monthlyId),
                "the entitlement is dated the last day - a month is earned once it ends");
    }

    @Test
    @Order(5)
    @DisplayName("an approved run is frozen: the trigger refuses an update, and refuses it under a wipe too")
    void theLineIsFrozen() throws Exception {
        assertThrows(Exception.class,
                () -> update("UPDATE payroll_line SET deductions = 0 WHERE payroll_run_id = " + runId),
                "the freeze is the whole point of approval");

        // The escape covers the delete and NOT the update - the distinction V43 missed.
        assertThrows(Exception.class, () -> update(
                "SET @app_bulk_wipe = 1; UPDATE payroll_line SET deductions = 0 WHERE payroll_run_id = "
                        + runId));
    }

    @Test
    @Order(6)
    @DisplayName("an approved run cannot be cancelled - the rows it wrote are already somebody's balance")
    void anApprovedRunCannotBeCancelled() {
        assertEquals("payroll.error.not.draft",
                assertThrows(UserValidationException.class,
                        () -> SERVICE.cancelDraft(runId)).getMessage());
        assertEquals("payroll.error.not.draft",
                assertThrows(UserValidationException.class,
                        () -> SERVICE.rebuildDraft(runId)).getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("and the database refuses to delete a run that left a trace in a ledger")
    void theForeignKeyHoldsTheRun() {
        assertThrows(Exception.class,
                () -> update("DELETE FROM payroll_run WHERE id = " + runId));
    }

    @Test
    @Order(8)
    @DisplayName("marking it paid closes it, and a second attempt is refused")
    void payingClosesTheRun() throws Exception {
        SERVICE.markPaid(runId);
        assertEquals(PayrollRunStatus.PAID, SERVICE.findRun(runId).orElseThrow().status());

        assertEquals("payroll.error.not.approved",
                assertThrows(UserValidationException.class,
                        () -> SERVICE.markPaid(runId)).getMessage());
    }

    // ---- the fixture ----------------------------------------------------------------------

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static PayrollLine lineFor(List<PayrollLine> lines, int employeeId) {
        return lines.stream().filter(l -> l.employeeId() == employeeId).findFirst()
                .orElseThrow(() -> new AssertionError("no line for employee " + employeeId));
    }

    private static void signIn(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "payroll-operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static void seedOperator() throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO users (id, user_name, user_pass, user_available) VALUES (?, ?, ?, 0) "
                        + "ON DUPLICATE KEY UPDATE user_name = VALUES(user_name)")) {
            insert.setInt(1, OPERATOR);
            insert.setString(2, STAMP);
            insert.setString(3, "not-a-password");
            insert.executeUpdate();
        }
    }

    private static int seedEmployee(String name, String hired, String ended, String salary)
            throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO employees (column_name, job, hire_date, end_date, salary, user_id) "
                        + "VALUES (?, (SELECT MIN(id) FROM jobs), ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setString(2, hired);
            insert.setString(3, ended);
            insert.setBigDecimal(4, new BigDecimal(salary));
            insert.setInt(5, OWNER);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static void insertCompensation(int employeeId, String from, String rate)
            throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO employee_compensation (employee_id, effective_from, salary_kind, "
                        + "rate, user_id) VALUES (?, ?, 'MONTHLY', ?, ?)")) {
            insert.setInt(1, employeeId);
            insert.setString(2, from);
            insert.setBigDecimal(3, new BigDecimal(rate));
            insert.setInt(4, OWNER);
            insert.executeUpdate();
        }
    }

    private static void insertAllowance(int employeeId, String name, String amount, String from,
                                        String to) throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO employee_allowance (employee_id, allowance_name, amount, "
                        + "effective_from, effective_to, user_id) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setInt(1, employeeId);
            insert.setString(2, name);
            insert.setBigDecimal(3, new BigDecimal(amount));
            insert.setString(4, from);
            insert.setString(5, to);
            insert.setInt(6, OWNER);
            insert.executeUpdate();
        }
    }

    private static int update(String sql) throws Exception {
        try (Statement statement = transaction.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static BigDecimal ledgerAmount(int employeeId, String kind) throws Exception {
        try (Statement statement = transaction.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COALESCE(SUM(amount), 0) FROM employee_ledger WHERE employee_id = "
                             + employeeId + " AND kind = '" + kind + "' AND payroll_run_id = "
                             + runId)) {
            assertTrue(rs.next());
            return rs.getBigDecimal(1).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    private static int countLedger(int employeeId, String kind) throws Exception {
        try (Statement statement = transaction.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM employee_ledger WHERE employee_id = " + employeeId
                             + " AND kind = '" + kind + "' AND payroll_run_id = " + runId)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static String ledgerDate(int employeeId) throws Exception {
        try (Statement statement = transaction.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT entry_date FROM employee_ledger WHERE employee_id = " + employeeId
                             + " AND payroll_run_id = " + runId + " LIMIT 1")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private static void assertNoResidue(Connection connection, String table, String where)
            throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1),
                    "this class left rows behind in " + table + " - the rollback did not hold");
        }
    }
}
