package com.hamza.account.features.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeCashPurpose;
import com.hamza.account.features.employee.EmployeePayment;
import com.hamza.account.features.employee.EmployeePaymentService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.treasury.WalletFeeService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-MySQL acceptance for the expenses rework: V64 on a schema built from nothing, V64 over a V63
 * database that already holds expenses, and {@link ExpenseService} against both halves of a transaction.
 * <p>
 * <b>Two scratch schemas and nothing else.</b> Neither is the configured business database: each is
 * created with a unique name and dropped in {@link #dropScratchSchemas()}. If the configured account
 * cannot create databases, supply {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_USER} and
 * {@code ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD} - the shape {@code AuditLogDatabaseAcceptanceTest} set.
 * <p>
 * <b>Why the upgrade path is migrated from a folder.</b> V64 cannot be left out of a classpath run, and
 * {@code Flyway.target} does not hold the repeatables back - they run whatever the target, and this
 * change's {@code R__triggers.sql} names columns V64 adds. So the V63 schema is migrated from a copy of
 * the migrations without V64, with the triggers file cut where the V64 section begins, then migrated
 * again from the classpath - which applies V64 and re-runs only the repeatable whose checksum moved.
 * <p>
 * <b>The session is never user 1</b>: {@code isSystemAdministrator()} bypasses every permission.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ExpenseDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_expense_acceptance_";
    private static final String TRIGGERS_V64_MARKER = "-- expenses_details and expenses (V64)";
    private static final int OPERATOR = 4242;
    private static final String STAMP = "EXP-" + System.nanoTime();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String freshSchema;
    private static String upgradedSchema;

    /** Read from the V63 schema just before V64, and again just after. */
    private static final List<String> FIGURES_BEFORE = new ArrayList<>();
    private static final List<String> FIGURES_AFTER = new ArrayList<>();

    @BeforeAll
    static void migrateScratchSchemas() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        host = config.get(CryptoDatabaseConfig.HOST);
        port = config.get(CryptoDatabaseConfig.PORT);
        username = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_USER", config.get(CryptoDatabaseConfig.USERNAME));
        password = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD", config.get(CryptoDatabaseConfig.PASSWORD));
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        freshSchema = SCHEMA_PREFIX + "fresh_" + unique;
        upgradedSchema = SCHEMA_PREFIX + "upgrade_" + unique;

        try {
            createDatabase(freshSchema);
            migrate(freshSchema, "classpath:db/migration");

            createDatabase(upgradedSchema);
            Path before = migrationsWithoutV64();
            migrate(upgradedSchema, "filesystem:" + before.toAbsolutePath().toString().replace('\\', '/'));
            try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
                seedTheYearsBeforeV64(connection);
                readFigures(connection, FIGURES_BEFORE);
            }
            migrate(upgradedSchema, "classpath:db/migration");
            try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
                readFigures(connection, FIGURES_AFTER);
            }

            DataSourceProvider.initialize(host, port, freshSchema, username, password);
            seedOperator();
        } catch (Exception failure) {
            try {
                dropScratchSchemas();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @AfterAll
    static void dropScratchSchemas() throws Exception {
        DataSourceProvider.shutdown();
        for (String schema : new String[]{freshSchema, upgradedSchema}) {
            if (schema == null || !schema.startsWith(SCHEMA_PREFIX)) continue;
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    // ---- the migration ------------------------------------------------------------------

    @Test
    @DisplayName("from nothing: the heading is numbered by the database, the fee has its key, salaries are employees'")
    void freshInstall() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = 'expenses' AND column_name = 'id' AND extra LIKE '%auto_increment%'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.key_column_usage"
                + " WHERE table_schema = DATABASE() AND table_name = 'expenses_details'"
                + " AND column_name = 'type_code' AND referenced_table_name = 'expenses'"),
                "the key onto the heading was taken off to renumber the column, and has to be back");
        // By key and not by name: walletFeeSurvivesARename renames this very row, and the order tests run
        // in is not this file's to decide. The name is checked on the upgraded schema, which nothing renames.
        assertEquals(1, scalar("SELECT COUNT(*) FROM expenses WHERE system_key = 'WALLET_FEE'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM expenses WHERE employee_payment = 1"
                + " AND expenses_name IN ('مرتبات', 'سلف')"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM auth_permission WHERE permission_key IN"
                + " ('expenses.show', 'expenses.headings.update', 'expenses.export')"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE()"
                + " AND routine_name LIKE '%expense%'"), "V64 leaves none of its helpers behind");
    }

    @Test
    @DisplayName("over data: no figure moves - not a till's balance, not a month's expenses, not an employee's account")
    void noFigureMoves() {
        assertTrue(!FIGURES_BEFORE.isEmpty(), "the V63 schema was read");
        assertEquals(FIGURES_BEFORE, FIGURES_AFTER);
    }

    @Test
    @DisplayName("over data: the fee keeps its row and gains its key; a heading all employees becomes theirs, a mixed one does not")
    void upgradeFlagsHeadings() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
            assertEquals("عمولات تحويل", string(connection,
                    "SELECT expenses_name FROM expenses WHERE system_key = 'WALLET_FEE'"));
            assertEquals(1, scalar(connection, "SELECT employee_payment FROM expenses WHERE expenses_name = 'مرتبات'"));
            assertEquals(1, scalar(connection, "SELECT employee_payment FROM expenses WHERE expenses_name = '"
                    + STAMP + " بدل'"), "every row on it names an employee");
            assertEquals(0, scalar(connection, "SELECT employee_payment FROM expenses WHERE expenses_name = 'أخرى'"),
                    "one stray employee row does not take a heading off the expenses screen");
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM employee_cash_purpose"),
                    "decision م-٣: no old advance is re-labelled by the migration");
        }
    }

    // ---- the service, against a real transaction ------------------------------------------

    @Test
    @DisplayName("an expense is written with its payee, audited on insert and update, and moves the till by its amount")
    void writeAuditAndBalance() throws Exception {
        signIn(AppPermissions.EXPENSES_SHOW, AppPermissions.EXPENSES_CREATE, AppPermissions.EXPENSES_UPDATE);
        ExpenseService service = new ExpenseService(DaoFactory.INSTANCE);
        int heading = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");
        int till = scalar("SELECT MIN(id) FROM treasury");
        BigDecimal before = decimal("SELECT balance FROM treasury_current_balance WHERE id = " + till);

        int id = service.create(ExpenseEntry.parse(0, LocalDate.now(), heading, till, new BigDecimal("125.50"),
                "شركة الكهرباء", "R-77", STAMP + " write"));
        ExpenseRow stored = service.find(id);
        assertEquals("شركة الكهرباء", stored.payee());
        assertEquals("R-77", stored.referenceNo());
        assertEquals(OPERATOR, stored.userId());
        assertEquals(before.subtract(new BigDecimal("125.50")),
                decimal("SELECT balance FROM treasury_current_balance WHERE id = " + till));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_log WHERE table_name = 'expenses_details'"
                + " AND record_id = '" + id + "' AND action_type = 'INSERT'"));

        service.update(ExpenseEntry.parse(id, LocalDate.now(), heading, till, new BigDecimal("100.00"),
                "شركة الكهرباء", "R-77", STAMP + " write"), "typo");
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_log WHERE table_name = 'expenses_details'"
                + " AND record_id = '" + id + "' AND action_type = 'UPDATE'"));
        assertEquals(before.subtract(new BigDecimal("100.00")),
                decimal("SELECT balance FROM treasury_current_balance WHERE id = " + till));
    }

    @Test
    @DisplayName("a batch refused on its third line leaves the first two unwritten")
    void batchIsAllOrNothing() throws Exception {
        signIn(AppPermissions.EXPENSES_CREATE);
        ExpenseService service = new ExpenseService(DaoFactory.INSTANCE);
        int power = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");
        int salaries = scalar("SELECT id FROM expenses WHERE expenses_name = 'مرتبات'");
        int till = scalar("SELECT MIN(id) FROM treasury");
        String note = STAMP + " batch";

        ExpenseBatchLineRefused refused = assertThrows(ExpenseBatchLineRefused.class, () -> service.createBatch(List.of(
                ExpenseEntry.parse(0, LocalDate.now(), power, till, BigDecimal.TEN, null, null, note),
                ExpenseEntry.parse(0, LocalDate.now(), power, till, BigDecimal.ONE, null, null, note),
                ExpenseEntry.parse(0, LocalDate.now(), salaries, till, BigDecimal.ONE, null, null, note))));
        assertEquals(3, refused.line());
        assertEquals(0, scalar("SELECT COUNT(*) FROM expenses_details WHERE notes = '" + note + "'"),
                "the transaction took back the two lines written before the refusal");
    }

    @Test
    @DisplayName("renaming the fee's heading does not lose it: a wallet fee still finds it by key")
    void walletFeeSurvivesARename() throws Exception {
        signIn(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        ExpenseHeadingService headings = new ExpenseHeadingService();
        ExpenseHeading fee = headings.all().stream().filter(ExpenseHeading::isSystem).findFirst().orElseThrow();
        headings.save(new ExpenseHeadingDraft(fee.id(), STAMP + " عمولة", null, true, false));

        int till = scalar("SELECT MIN(id) FROM treasury");
        assertEquals(1, new WalletFeeService().post(till, LocalDate.now(), new BigDecimal("1000"),
                new BigDecimal("15"), STAMP + " fee", OptionalInt.empty()));
        assertEquals(fee.id(), scalar("SELECT type_code FROM expenses_details WHERE notes = '" + STAMP + " fee'"));
    }

    @Test
    @DisplayName("a heading holding an expense is refused a delete; a third level is refused a save")
    void headingRulesAgainstTheSchema() throws Exception {
        signIn(AppPermissions.EXPENSES_HEADINGS_UPDATE, AppPermissions.EXPENSES_CREATE);
        ExpenseHeadingService headings = new ExpenseHeadingService();
        int main = headings.save(new ExpenseHeadingDraft(0, STAMP + " إدارية", null, true, false));
        int sub = headings.save(new ExpenseHeadingDraft(0, STAMP + " نظافة", main, true, false));
        assertThrows(UserValidationException.class,
                () -> headings.save(new ExpenseHeadingDraft(0, STAMP + " عمق", sub, true, false)));

        new ExpenseService(DaoFactory.INSTANCE).create(ExpenseEntry.parse(0, LocalDate.now(), sub,
                scalar("SELECT MIN(id) FROM treasury"), BigDecimal.ONE, null, null, STAMP + " held"));
        assertThrows(DaoException.class, () -> headings.delete(sub), "an expense holds it");
        assertThrows(DaoException.class, () -> headings.delete(main), "a sub-heading holds it");
    }

    @Test
    @DisplayName("an employee is paid under an employee heading, and refused under any other")
    void employeePayments() throws Exception {
        signIn(AppPermissions.EMPLOYEE_PAY, AppPermissions.EXPENSES_CREATE);
        int employee = scalar("SELECT MIN(id) FROM employees");
        int till = scalar("SELECT MIN(id) FROM treasury");
        EmployeePaymentService payments = new EmployeePaymentService(new ExpenseService(DaoFactory.INSTANCE));

        int salaries = scalar("SELECT id FROM expenses WHERE expenses_name = 'مرتبات'");
        int expenseId = payments.pay(EmployeePayment.parse(employee, LocalDate.now(), new BigDecimal("300"),
                EmployeeCashPurpose.ADVANCE, till, salaries, STAMP + " advance"));
        assertEquals(employee, scalar("SELECT emp_id FROM expenses_details WHERE id = " + expenseId));
        assertNotNull(string("SELECT purpose FROM employee_cash_purpose WHERE expense_id = " + expenseId));

        int power = scalar("SELECT id FROM expenses WHERE expenses_name = 'كهرباء'");
        assertThrows(UserValidationException.class, () -> payments.pay(EmployeePayment.parse(employee,
                LocalDate.now(), BigDecimal.TEN, EmployeeCashPurpose.SALARY, till, power, STAMP + " wrong")));
    }

    // ---- fixtures -----------------------------------------------------------------------

    /**
     * The migrations as they stood before this change: every versioned file but V64, and the triggers
     * file cut where its V64 section begins.
     */
    private static Path migrationsWithoutV64() throws Exception {
        Path source = Paths.get(ExpenseDatabaseAcceptanceTest.class.getResource("/db/migration").toURI());
        Path target = Files.createTempDirectory("expense-migrations-v63-");
        target.toFile().deleteOnExit();
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("V64__")) {
                    continue;
                }
                String sql = Files.readString(file, StandardCharsets.UTF_8);
                if (name.equals("R__triggers.sql")) {
                    int cut = sql.indexOf(TRIGGERS_V64_MARKER);
                    assertTrue(cut > 0, "the V64 section of R__triggers.sql is where this test expects it");
                    sql = sql.substring(0, cut);
                }
                Files.writeString(target.resolve(name), sql, StandardCharsets.UTF_8);
            }
        }
        return target;
    }

    /**
     * Years of expenses as V63 wrote them: a bill, salaries and an advance with their employee, a wallet
     * fee, a heading holding a stray employee row, and a heading a shop added whose every row is an
     * employee's. {@code expenses.id} is not yet auto-increment, hence the explicit id.
     */
    private static void seedTheYearsBeforeV64(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO employees (column_name, job, hire_date, salary, user_id)"
                    + " VALUES ('" + STAMP + "', (SELECT MIN(id) FROM jobs), '2025-01-01', 3000, 1)");
            statement.executeUpdate("INSERT INTO expenses (id, expenses_name) SELECT MAX(id) + 1, '" + STAMP
                    + " بدل' FROM expenses");
        }
        int employee = scalar(connection, "SELECT id FROM employees WHERE column_name = '" + STAMP + "'");
        int till = scalar(connection, "SELECT MIN(id) FROM treasury");
        insertExpense(connection, "كهرباء", "2026-01-05", "100.00", null, till);
        insertExpense(connection, "مرتبات", "2026-01-31", "3000.00", employee, till);
        insertExpense(connection, "سلف", "2026-02-10", "500.00", employee, till);
        insertExpense(connection, "عمولات تحويل", "2026-02-11", "7.50", null, till);
        insertExpense(connection, "أخرى", "2026-02-12", "20.00", employee, till);
        insertExpense(connection, "أخرى", "2026-02-13", "35.00", null, till);
        insertExpense(connection, STAMP + " بدل", "2026-02-14", "150.00", employee, till);
    }

    private static void insertExpense(Connection connection, String heading, String day, String amount,
                                      Integer employee, int till) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO expenses_details (type_code, date, amount, notes, emp_id, treasury_id, user_id)"
                        + " VALUES ((SELECT id FROM expenses WHERE expenses_name = ?), ?, ?, ?, ?, ?, 1)")) {
            insert.setString(1, heading);
            insert.setString(2, day);
            insert.setBigDecimal(3, new BigDecimal(amount));
            insert.setString(4, STAMP);
            insert.setObject(5, employee);
            insert.setInt(6, till);
            assertEquals(1, insert.executeUpdate());
        }
    }

    /** What must read the same before and after V64. */
    private static void readFigures(Connection connection, List<String> into) throws Exception {
        for (String sql : List.of(
                "SELECT CONCAT(id, '=', balance) FROM treasury_current_balance ORDER BY id",
                // Grouped in a derived table: only_full_group_by refuses a CONCAT around the grouped
                // expression, which is how the first run of this class failed.
                "SELECT CONCAT(month, '=', total) FROM (SELECT DATE_FORMAT(date, '%Y-%m') AS month,"
                        + " SUM(amount) AS total FROM expenses_details GROUP BY month) monthly ORDER BY month",
                "SELECT CONCAT(employee_id, '=', balance) FROM employee_balance ORDER BY employee_id",
                "SELECT CONCAT(source_id, '=', entry_kind, '=', debit) FROM employee_account_table"
                        + " WHERE source = 'CASH' ORDER BY source_id")) {
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    into.add(rows.getString(1));
                }
            }
        }
    }

    /** The signed-in user has to be a row: {@code expenses_details.user_id} is a foreign key. */
    private static void seedOperator() throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO users (id, user_name, user_pass, user_available) VALUES (?, ?, 'not-a-password', 0)")) {
            insert.setInt(1, OPERATOR);
            insert.setString(2, STAMP);
            insert.executeUpdate();
        }
        try (Connection connection = ConnectionManager.acquire(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO employees (column_name, job, hire_date, salary, user_id)"
                    + " VALUES ('" + STAMP + "', (SELECT MIN(id) FROM jobs), '2025-01-01', 3000, 1)");
        }
    }

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static void createDatabase(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
    }

    private static void migrate(String schema, String location) {
        Flyway.configure()
                .dataSource(jdbcUrl(schema), username, password)
                .locations(location)
                .validateOnMigrate(false)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static int scalar(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire()) {
            return scalar(connection, sql);
        }
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
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

    private static String string(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire()) {
            return string(connection, sql);
        }
    }

    private static String string(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getString(1);
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
}
