package com.hamza.account.features.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The employee form's two writes against a real MySQL: what an edit may move, and what a new
 * employee is created with.
 *
 * <p>The form sends a salary only to a reader who may see one. Before this test the service did
 * not know that: an editor without {@code employees.show.salary} was shown an empty salary box,
 * saved a corrected telephone number, and the service read the box's zero as a new salary - refused
 * without {@code employee.salary.change}, and <b>written</b> with it, which V57 grants to whoever
 * holds {@code employee.update}.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG}. <b>The session is
 * never user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class EmployeeFormDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_empform_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "EMP-" + System.nanoTime();
    private static final LocalDate HIRED = LocalDate.of(2026, 1, 1);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    private final EmployeeService service = new EmployeeService();

    @BeforeAll
    static void migrate() throws Exception {
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
            execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                    + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
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
        ServiceRegistry.register(UserSessionContext.class, null);
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
    @DisplayName("an edit by a reader who may not see salaries leaves the salary where it was")
    void anEditWithoutTheSalaryKeysLeavesTheSalary() throws Exception {
        int id = hire(STAMP + "-A", "5000");

        // The shape V57 leaves every role in that could edit an employee: the update key and the
        // salary-change key it was granted alongside, and not the key that shows a salary.
        signIn(AppPermissions.EMPLOYEE_SHOW, AppPermissions.EMPLOYEE_UPDATE,
                AppPermissions.EMPLOYEE_SALARY_CHANGE);
        Employee shown = service.find(id);
        assertNull(shown.rate(), "the figure never reached this reader");

        service.update(formAfterCorrectingTheTelephone(shown));

        assertEquals("01000000001", string("SELECT tel FROM employees WHERE id = " + id));
        assertMoney("5000", decimal("SELECT rate FROM employee_compensation WHERE employee_id = " + id),
                "the empty box was not a salary of zero");
        assertMoney("5000", decimal("SELECT salary FROM employees WHERE id = " + id), "the hire rate");
    }

    @Test
    @DisplayName("and without the salary-change key the edit is saved rather than refused")
    void anEditWithoutTheSalaryChangeKeyIsSaved() throws Exception {
        int id = hire(STAMP + "-B", "4000");

        signIn(AppPermissions.EMPLOYEE_SHOW, AppPermissions.EMPLOYEE_UPDATE);
        service.update(formAfterCorrectingTheTelephone(service.find(id)));

        assertEquals("01000000001", string("SELECT tel FROM employees WHERE id = " + id));
        assertMoney("4000", decimal("SELECT rate FROM employee_compensation WHERE employee_id = " + id),
                "the salary");
    }

    @Test
    @DisplayName("a new employee's picture is saved with the employee, by whoever may add one")
    void aPictureComesWithTheNewEmployee() throws Exception {
        byte[] picture = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        signIn(AppPermissions.EMPLOYEE_CREATE);

        int id = service.create(EmployeeDraft.parse(0, STAMP + "-C", 1, null, HIRED, null,
                EmploymentType.FULL_TIME, "", "", "", "", "", null, SalaryKind.MONTHLY, BigDecimal.ZERO), picture);

        assertArrayEquals(picture, bytes("SELECT image FROM employees WHERE id = ?", id), "without the update key");
        assertMoney("0", decimal("SELECT rate FROM employee_compensation WHERE employee_id = " + id),
                "a clerk who may not see salaries hires at nothing, for somebody who may to correct");
    }

    @Test
    @DisplayName("the form lists the jobs for whoever may add an employee, and a suggested salary only with the salary key")
    void theFormsJobs() throws Exception {
        execute("UPDATE jobs SET default_salary = 3000 WHERE id = 1");
        signIn(AppPermissions.EMPLOYEE_CREATE);
        List<Job> jobs = service.jobsForPicker(EmployeeScope.ACTIVE_ONLY);
        assertTrue(jobs.stream().anyMatch(job -> job.id() == 1), "no job.show, and still a job to pick");
        assertTrue(jobs.stream().allMatch(job -> job.defaultSalary() == null), "a suggested salary is a salary");

        signIn(AppPermissions.EMPLOYEE_CREATE, AppPermissions.EMPLOYEES_SHOW_SALARY);
        assertMoney("3000", service.jobsForPicker(EmployeeScope.ACTIVE_ONLY).stream()
                .filter(job -> job.id() == 1).findFirst().orElseThrow().defaultSalary(), "with the key");
    }

    // ---- the form, as EmployeeFormController reads it -------------------------------------------

    private int hire(String name, String rate) throws Exception {
        signIn(AppPermissions.EMPLOYEE_SHOW, AppPermissions.EMPLOYEE_CREATE, AppPermissions.EMPLOYEE_UPDATE,
                AppPermissions.EMPLOYEES_SHOW_SALARY, AppPermissions.EMPLOYEE_SALARY_CHANGE);
        return service.create(EmployeeDraft.parse(0, name, 1, null, HIRED, null, EmploymentType.FULL_TIME,
                "", "", "0100", "", "", null, SalaryKind.MONTHLY, new BigDecimal(rate)));
    }

    /**
     * What the form sends back for this reader: every field as it was shown, a new telephone, and
     * the salary box as a reader without the salary key has it - empty, which the form reads as zero.
     */
    private static EmployeeDraft formAfterCorrectingTheTelephone(Employee shown) throws Exception {
        return EmployeeDraft.parse(shown.id(), shown.name(), shown.jobId(), shown.birthDate(), shown.hireDate(),
                shown.endDate(), shown.employmentType(), shown.nationalId(), shown.email(), "01000000001",
                shown.address(), shown.notes(), shown.defaultTreasuryId(),
                shown.salaryKind() == null ? SalaryKind.MONTHLY : shown.salaryKind(), BigDecimal.ZERO);
    }

    // ---- plumbing ---------------------------------------------------------------------------------

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static void assertMoney(String expected, BigDecimal actual, String what) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), what + ": expected " + expected + " but was " + actual);
    }

    private static BigDecimal decimal(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getBigDecimal(1);
        }
    }

    private static String string(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getString(1);
        }
    }

    private static byte[] bytes(String sql, int id) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), sql);
                return rows.getBytes(1);
            }
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
