package com.hamza.account.features.employee.attendance;

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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attendance against a real MySQL, and the one thing this phase exists for: that what is
 * recorded on the grid reaches the payroll.
 * <p>
 * What no unit test can reach:
 * <ul>
 *   <li><b>The approval transaction</b> - a leave decision and the days it writes land
 *       together or not at all.</li>
 *   <li><b>The upsert</b> - correcting a day is the same operation as recording it, over a
 *       real unique key.</li>
 *   <li><b>The join to the leave type</b> - whether a leave day is deducted is the type's
 *       current answer, so changing the type corrects the days already recorded.</li>
 *   <li><b>The payroll reading it</b> - the end-to-end that is the whole point of phase D.</li>
 * </ul>
 * One transaction, rolled back in a {@code finally}, then both databases queried for this
 * class's own marks rather than the rollback trusted.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttendanceDatabaseAcceptanceTest {

    private static final int OWNER = 1;
    private static final int OPERATOR = 9;
    private static final String STAMP = "ATT-" + System.nanoTime();
    private static final PayrollPeriod SEPTEMBER = new PayrollPeriod(2026, 9);

    private static final AttendanceService ATTENDANCE =
            new AttendanceService(new JdbcAttendanceRepository());
    private static final LeaveService LEAVE = new LeaveService(new JdbcAttendanceRepository());
    private static final PayrollService PAYROLL = new PayrollService(new JdbcPayrollRepository(),
            AttendanceSource.fromService(ATTENDANCE));

    private static Connection transaction;
    private static int employeeId;
    private static int unpaidTypeId;
    private static int paidTypeId;

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

        signIn(AppPermissions.ATTENDANCE_SHOW, AppPermissions.ATTENDANCE_RECORD,
                AppPermissions.LEAVE_REQUEST, AppPermissions.LEAVE_APPROVE,
                AppPermissions.PAYROLL_SHOW, AppPermissions.PAYROLL_CREATE,
                AppPermissions.PAYROLL_APPROVE);

        transaction = ConnectionManager.beginTransaction();
        seedOperator();
        employeeId = seedEmployee(STAMP + "-E", "3000");
        paidTypeId = seedLeaveType(STAMP + "-paid", true);
        unpaidTypeId = seedLeaveType(STAMP + "-unpaid", false);
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
            assertNoResidue(connection, "leave_type", "type_name LIKE '" + STAMP + "%'");
            assertNoResidue(connection, "payroll_run", "notes LIKE '" + STAMP + "%'");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    @Test
    @Order(1)
    @DisplayName("a day is recorded, and correcting it is the same operation rather than a second row")
    void recordingAndCorrecting() throws Exception {
        ATTENDANCE.recordDay(AttendanceEntry.parse(employeeId, day(1),
                AttendanceStatus.PRESENT, new BigDecimal("8"), null, null));
        ATTENDANCE.recordDay(AttendanceEntry.parse(employeeId, day(1),
                AttendanceStatus.ABSENT, null, null, null));

        List<AttendanceDay> days = ATTENDANCE.daysOf(employeeId, day(1), day(1));
        assertEquals(1, days.size(), "the unique key is what keeps a day from having two answers");
        assertEquals(AttendanceStatus.ABSENT, days.get(0).status());
    }

    @Test
    @Order(2)
    @DisplayName("a day outside employment is refused")
    void outsideEmployment() {
        assertEquals("attendance.error.before.hire",
                assertThrows(UserValidationException.class,
                        () -> ATTENDANCE.recordDay(AttendanceEntry.parse(employeeId,
                                LocalDate.of(2024, 1, 1), AttendanceStatus.ABSENT, null, null,
                                null))).getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("approving leave writes its days, and rejecting writes none")
    void approvingWritesTheDays() throws Exception {
        int rejected = LEAVE.request(employeeId, paidTypeId, day(5), day(6), STAMP);
        LEAVE.decide(rejected, LeaveStatus.REJECTED, STAMP);
        assertEquals(0, ATTENDANCE.daysOf(employeeId, day(5), day(6)).size(),
                "a rejected request marks nothing");

        int approved = LEAVE.request(employeeId, paidTypeId, day(10), day(12), STAMP);
        LEAVE.decide(approved, LeaveStatus.APPROVED, STAMP);

        List<AttendanceDay> days = ATTENDANCE.daysOf(employeeId, day(10), day(12));
        assertEquals(3, days.size(), "three days asked for, three days marked");
        assertTrue(days.stream().allMatch(d -> d.status() == AttendanceStatus.LEAVE));
        assertTrue(days.stream().noneMatch(AttendanceDay::countsAsAbsence),
                "the type is paid, so none of them is absence");
    }

    @Test
    @Order(4)
    @DisplayName("a request already decided is refused rather than decided twice")
    void decidingTwice() throws Exception {
        int request = LEAVE.request(employeeId, paidTypeId, day(20), day(20), STAMP);
        LEAVE.decide(request, LeaveStatus.APPROVED, STAMP);

        assertEquals("leave.error.decided",
                assertThrows(UserValidationException.class,
                        () -> LEAVE.decide(request, LeaveStatus.REJECTED, STAMP)).getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("whether a leave day is absence is the type's current answer, not a copy on the day")
    void theTypeDecides() throws Exception {
        int request = LEAVE.request(employeeId, unpaidTypeId, day(15), day(15), STAMP);
        LEAVE.decide(request, LeaveStatus.APPROVED, STAMP);

        assertTrue(ATTENDANCE.daysOf(employeeId, day(15), day(15)).get(0).countsAsAbsence(),
                "an unpaid leave is deducted");

        // Make the type paid, and the day already recorded changes with it.
        update("UPDATE leave_type SET is_paid = 1 WHERE id = " + unpaidTypeId);
        assertTrue(!ATTENDANCE.daysOf(employeeId, day(15), day(15)).get(0).countsAsAbsence(),
                "copying the decision onto the day would have left two answers in the database");
        update("UPDATE leave_type SET is_paid = 0 WHERE id = " + unpaidTypeId);
    }

    @Test
    @Order(6)
    @DisplayName("the month's summary is what a person would reach with a pen")
    void theSummary() throws Exception {
        AttendanceSummary summary = ATTENDANCE.summaryFor(employeeId,
                SEPTEMBER.firstDay(), SEPTEMBER.lastDay());

        // day 1 absent, days 10-12 paid leave, day 15 unpaid leave, day 20 paid leave.
        assertEquals(new BigDecimal("2.00"), summary.absenceDays(),
                "the absence and the unpaid leave; the four paid leave days cost nothing");
        assertEquals(new BigDecimal("0.00"), summary.workedDays());
    }

    @Test
    @Order(7)
    @DisplayName("and the payroll reads it - which is the whole point of this phase")
    void thePayrollReadsTheGrid() throws Exception {
        int runId = PAYROLL.createDraft(SEPTEMBER, STAMP + " september");
        PayrollLine line = PAYROLL.linesOf(runId).stream()
                .filter(candidate -> candidate.employeeId() == employeeId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line for the fixture's employee"));

        assertEquals(new BigDecimal("2.00"), line.absenceDays());
        assertEquals(new BigDecimal("200.00"), line.absenceDeduction(),
                "3000 over September's own 30 days, twice");
        assertEquals(new BigDecimal("2800.00"), line.netPay());
    }

    @Test
    @Order(8)
    @DisplayName("an employee with nothing recorded is untouched - a shop that keeps no grid is unaffected")
    void anUnrecordedMonthChangesNothing() throws Exception {
        int other = seedEmployee(STAMP + "-N", "1500");
        AttendanceSummary summary = ATTENDANCE.summaryFor(other,
                SEPTEMBER.firstDay(), SEPTEMBER.lastDay());

        assertTrue(summary.isEmpty(),
                "and an empty summary is what tells the payroll to leave the line alone");
    }

    // ---- the fixture ----------------------------------------------------------------------

    private static LocalDate day(int dayOfMonth) {
        return LocalDate.of(2026, 9, dayOfMonth);
    }

    private static void signIn(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "attendance-operator", Arrays.asList(granted));
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

    private static int seedEmployee(String name, String salary) throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO employees (column_name, job, hire_date, salary, user_id) "
                        + "VALUES (?, (SELECT MIN(id) FROM jobs), '2025-01-01', ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setBigDecimal(2, new BigDecimal(salary));
            insert.setInt(3, OWNER);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int seedLeaveType(String name, boolean paid) throws Exception {
        try (PreparedStatement insert = transaction.prepareStatement(
                "INSERT INTO leave_type (type_name, is_paid, annual_limit, user_id) "
                        + "VALUES (?, ?, 0, ?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setInt(2, paid ? 1 : 0);
            insert.setInt(3, OWNER);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int update(String sql) throws Exception {
        try (Statement statement = transaction.createStatement()) {
            return statement.executeUpdate(sql);
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
