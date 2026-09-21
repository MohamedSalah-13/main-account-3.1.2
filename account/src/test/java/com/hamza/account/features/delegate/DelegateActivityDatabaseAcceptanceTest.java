package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.delegate.trend.DelegateTrend;
import com.hamza.account.features.delegate.trend.DelegateTrendFilter;
import com.hamza.account.features.delegate.trend.DelegateTrendPoint;
import com.hamza.account.features.delegate.trend.DelegateTrendService;
import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.Users;
import com.hamza.account.service.AccountCustomerService;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase B against a real MySQL: V71, the delegate a collection is given, and the month's figures
 * worked out with a pen and compared with what the statement answers.
 *
 * <p>What only a database can say:
 * <ul>
 *   <li><b>V71 applies</b> - from nothing, and <b>over a V70 schema that already holds
 *       collections</b>, which is the {@code ALTER} every customer's upgrade is. Their rows must
 *       come through with their amounts untouched and no delegate guessed for them.</li>
 *   <li><b>The attribution happens inside the real save.</b> The collections here go through
 *       {@code AccountCustomerService.save}, not through a fixture: the invoice's delegate wins
 *       over the customer's default, a customer with no default leaves NULL, and moving the
 *       customer to another delegate afterwards changes nothing already written.</li>
 *   <li><b>The three groupings do not multiply one another</b>, which is the only way a query
 *       with three aggregated joins is ever wrong, and which no unit test can see.</li>
 *   <li><b>It reconciles</b>: the delegates' sales add up to the sales of the month.</li>
 * </ul>
 *
 * Two scratch schemas, created here and dropped in {@code @AfterAll}; the configured database is
 * a credential carrier and is never opened. <b>The session is never user 1.</b>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DelegateActivityDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_delegate_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "DLG-" + System.nanoTime();
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String freshSchema;
    private static String upgradedSchema;

    private static int firstDelegate;
    private static int secondDelegate;
    private static int customerWithDefault;
    private static int customerWithNone;
    private static BigDecimal collectedBeforeV71;
    private static BigDecimal collectedAfterV71;

    @BeforeAll
    static void migrateScratchSchemas() throws Exception {
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configSource().getAbsolutePath());
        host = config.get(CryptoDatabaseConfig.HOST);
        port = config.get(CryptoDatabaseConfig.PORT);
        username = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_USER", config.get(CryptoDatabaseConfig.USERNAME));
        password = environmentOr("ACCOUNT_DB_ACCEPTANCE_ADMIN_PASSWORD", config.get(CryptoDatabaseConfig.PASSWORD));
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        freshSchema = SCHEMA_PREFIX + "fresh_" + unique;
        upgradedSchema = SCHEMA_PREFIX + "upgrade_" + unique;

        try {
            createDatabase(upgradedSchema);
            migrate(upgradedSchema, "filesystem:" + migrationsBeforeV71().toAbsolutePath().toString().replace('\\', '/'));
            try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
                seedCollectionsAsV70WroteThem(connection);
                collectedBeforeV71 = decimal(connection, "SELECT SUM(paid) FROM customers_accounts");
            }
            migrate(upgradedSchema, "classpath:db/migration");
            try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
                collectedAfterV71 = decimal(connection, "SELECT SUM(paid) FROM customers_accounts");
            }

            createDatabase(freshSchema);
            migrate(freshSchema, "classpath:db/migration");
            DataSourceProvider.initialize(host, port, freshSchema, username, password);
            seedTheMonth();
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
            if (schema == null || !schema.startsWith(SCHEMA_PREFIX)) {
                continue;
            }
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }

    // ---- the migration -------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("from nothing: the column with its key and index, the permission, and no helper left behind")
    void freshInstall() throws Exception {
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '71' AND success = 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = 'customers_accounts' AND column_name = 'delegate_id' AND is_nullable = 'YES'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.referential_constraints"
                + " WHERE constraint_schema = DATABASE() AND constraint_name = 'customers_accounts_delegate_fk'"
                + " AND delete_rule IN ('RESTRICT', 'NO ACTION')"));
        for (String index : List.of("customers_accounts_delegate_date_idx", "total_sales_delegate_date_idx",
                "total_sales_re_delegate_date_idx")) {
            assertEquals(2, scalar("SELECT COUNT(*) FROM information_schema.statistics"
                    + " WHERE table_schema = DATABASE() AND index_name = '" + index + "'"), index);
        }
        // The key must have used the composite index rather than building a second one on the column.
        assertEquals(1, scalar("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics"
                + " WHERE table_schema = DATABASE() AND table_name = 'customers_accounts'"
                + " AND column_name = 'delegate_id' AND seq_in_index = 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM auth_permission WHERE permission_key = 'commission.reports'"));
        assertEquals(0, scalar(rolesHolding("commission.show") + " AND role_id NOT IN ("
                + roleIdsHolding("commission.reports") + ")"), "whoever sees the rules sees the report");
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.routines"
                + " WHERE routine_schema = DATABASE() AND routine_name LIKE 'add\\_%'"));
    }

    @Test
    @Order(2)
    @DisplayName("over a V70 schema with collections in it: every amount kept, and no delegate guessed")
    void theUpgradeKeepsEveryCollectionAndGuessesNothing() throws Exception {
        assertEquals(0, new BigDecimal("1750.00").compareTo(collectedBeforeV71));
        assertEquals(0, collectedBeforeV71.compareTo(collectedAfterV71));
        try (Connection connection = DriverManager.getConnection(jdbcUrl(upgradedSchema), username, password)) {
            assertEquals(3, scalar(connection, "SELECT COUNT(*) FROM customers_accounts"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM customers_accounts WHERE delegate_id IS NOT NULL"),
                    "the customer has a default delegate today, and that says nothing about who collected last year");
            assertEquals(1, scalar(connection,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '71' AND success = 1"));
        }
    }

    // ---- the attribution, through the real save -----------------------------------------------

    @Test
    @Order(3)
    @DisplayName("a collection is given its delegate inside the save: the invoice's first, the customer's default second")
    void whoseCollectionItIs() throws Exception {
        assertEquals(firstDelegate, delegateOf("on account, default"), "on account: the customer's default");
        assertEquals(secondDelegate, delegateOf("allocated, no default"),
                "allocated to an invoice: that invoice's delegate, though the customer has no default");
        assertEquals(secondDelegate, delegateOf("allocated, other delegate"),
                "the invoice's delegate wins over the customer's default");
        assertEquals(0, scalar("SELECT COUNT(*) FROM customers_accounts WHERE notes = '" + STAMP
                + " on account, no default' AND delegate_id IS NOT NULL"), "nobody to name is NULL, not a guess");
    }

    @Test
    @Order(4)
    @DisplayName("moving the customer to another delegate afterwards rewrites nobody's history")
    void itIsWrittenOnceAndNotDerived() throws Exception {
        execute("UPDATE custom SET default_delegate_id = " + secondDelegate + " WHERE id = " + customerWithDefault);
        assertEquals(firstDelegate, delegateOf("on account, default"));
        // Running the attribution again over a row that names somebody changes nothing.
        int collection = scalar("SELECT account_num FROM customers_accounts WHERE notes = '" + STAMP
                + " on account, default'");
        assertEquals(0, new JdbcDelegateActivityRepository().attributeCollection(collection));
        assertEquals(firstDelegate, delegateOf("on account, default"));
    }

    @Test
    @Order(5)
    @DisplayName("a note is nobody's collection")
    void aNoteIsNotAttributed() throws Exception {
        int note = scalar("SELECT account_num FROM customers_accounts WHERE notes = '" + STAMP + " credit note'");
        assertEquals(0, new JdbcDelegateActivityRepository().attributeCollection(note));
        assertEquals(0, scalar("SELECT COUNT(*) FROM customers_accounts WHERE account_num = " + note
                + " AND delegate_id IS NOT NULL"));
    }

    // ---- the month, against a pen ---------------------------------------------------------------

    /**
     * October, by hand:
     * <pre>
     * first delegate   sales 900 (1000 less 100)           returns 300 (dated October, of a September invoice)
     *                  collected 400 cash on the invoice - 50 refunded + 250 on account          = 600
     * second delegate  sales 500 + 600 + 400 = 1500        returns 0 (his return is dated November)
     *                  collected 500 cash + 300 + 150 allocated to his invoices                 = 950
     * direct sale      sales 200, collected 200
     * no delegate      120 on account from a customer with no default + 1000 entered before V71 = 1120
     * </pre>
     */
    @Test
    @Order(6)
    @DisplayName("October is what a pen makes it, and the three groupings do not multiply one another")
    void theMonthByHand() throws Exception {
        signIn(AppPermissions.COMMISSION_REPORTS);
        DelegatePerformanceMonth october = new DelegatePerformanceService().month(OCTOBER);

        DelegateActivity first = activityOf(october, firstDelegate);
        assertMoney("900.00", first.sales());
        assertMoney("300.00", first.salesReturns());
        assertMoney("600.00", first.netSales());
        assertMoney("600.00", first.collected());

        DelegateActivity second = activityOf(october, secondDelegate);
        assertMoney("1500.00", second.sales());
        assertMoney("0.00", second.salesReturns());
        assertMoney("950.00", second.collected());

        DelegateActivity direct = activityOf(october, 1);
        assertMoney("200.00", direct.sales());
        assertMoney("200.00", direct.collected());

        assertMoney("1120.00", october.unattributedCollections());
        assertTrue(october.rows().stream().allMatch(row -> row.rule().isEmpty()),
                "no rate was fetched for a reader holding the report permission alone");
    }

    @Test
    @Order(7)
    @DisplayName("the delegates' sales add up to the sales of the month, and the cash to what the tills took")
    void itReconciles() throws Exception {
        signIn(AppPermissions.COMMISSION_REPORTS);
        DelegatePerformanceMonth october = new DelegatePerformanceService().month(OCTOBER);

        assertMoney(decimal("SELECT SUM(total - discount) FROM total_sales"
                + " WHERE invoice_date BETWEEN '2026-10-01' AND '2026-10-31'").toPlainString(), october.totalSales());
        assertMoney(decimal("SELECT SUM(total - discount) FROM total_sales_re"
                + " WHERE invoice_date BETWEEN '2026-10-01' AND '2026-10-31'").toPlainString(), october.totalReturns());

        BigDecimal throughTheTills = decimal("SELECT"
                + " (SELECT SUM(paid_up) FROM total_sales WHERE invoice_date BETWEEN '2026-10-01' AND '2026-10-31')"
                + " - (SELECT SUM(paid_from_treasury) FROM total_sales_re"
                + "      WHERE invoice_date BETWEEN '2026-10-01' AND '2026-10-31')"
                + " + (SELECT SUM(paid) FROM customers_accounts"
                + "      WHERE account_date BETWEEN '2026-10-01' AND '2026-10-31')");
        assertMoney(throughTheTills.toPlainString(),
                october.totalCollected().add(october.unattributedCollections()));
    }

    @Test
    @Order(8)
    @DisplayName("with the rate permission each delegate is judged on his own rule's basis")
    void thePreview() throws Exception {
        signIn(AppPermissions.COMMISSION_RULE_UPDATE, AppPermissions.COMMISSION_SHOW);
        CommissionRuleService rules = new CommissionRuleService();
        rules.save(firstDelegate, LocalDate.of(2026, 10, 1), CommissionBasis.SALES, TierMode.WHOLE,
                new BigDecimal("1000"), List.of(tier("50", "1"), tier("100", "2")), STAMP);
        rules.save(secondDelegate, LocalDate.of(2026, 10, 1), CommissionBasis.COLLECTED, TierMode.WHOLE,
                BigDecimal.ZERO, List.of(tier("0", "2")), STAMP);

        signIn(AppPermissions.COMMISSION_REPORTS, AppPermissions.COMMISSION_SHOW);
        DelegatePerformanceMonth october = new DelegatePerformanceService().month(OCTOBER);

        DelegatePerformanceRow first = rowOf(october, firstDelegate);
        assertMoney("60.00", first.achievementPercent());
        assertMoney("6.00", first.commission());
        DelegatePerformanceRow second = rowOf(october, secondDelegate);
        assertMoney("19.00", second.commission());
        assertTrue(rowOf(october, 1).rule().isEmpty(), "direct sale has no rule, and shows no commission");
        assertMoney("25.00", october.totalCommission());

        // September has the first delegate's invoice and none of October's rule: it started on 1 October.
        DelegatePerformanceMonth september = new DelegatePerformanceService().month(YearMonth.of(2026, 9));
        assertMoney("700.00", activityOf(september, firstDelegate).sales());
        assertTrue(rowOf(september, firstDelegate).rule().isEmpty());
    }

    /**
     * A delegate's trend is the performance report by the month - read, not typed a second time -
     * over three months holding a September invoice, its return dated October, a return dated
     * November, collections written to a delegate at entry and a credit note that is nobody's.
     */
    @Test
    @Order(9)
    @DisplayName("each month of a delegate's trend is that month's row of the performance report")
    void theTrendIsThePerformanceReportByMonth() throws Exception {
        signIn(AppPermissions.COMMISSION_REPORTS);
        DelegateTrendService trends = new DelegateTrendService();
        DelegatePerformanceService performance = new DelegatePerformanceService();

        for (int delegate : new int[]{firstDelegate, secondDelegate, 1}) {
            DelegateTrend trend = trends.trend(new DelegateTrendFilter(delegate, TrendGranularity.MONTH,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), false));
            assertEquals(3, trend.points().size(), "September, October and November, a quiet one included");
            for (DelegateTrendPoint point : trend.points()) {
                DelegatePerformanceMonth month = performance.month(YearMonth.from(point.start()));
                DelegatePerformanceRow row = month.rows().stream()
                        .filter(candidate -> candidate.activity().employeeId() == delegate).findFirst().orElse(null);
                assertMoney(row == null ? "0" : row.activity().netSales().toPlainString(), point.netSales());
                assertMoney(row == null ? "0" : row.activity().collected().toPlainString(), point.collected());
            }
        }

        // The November return of the second delegate's customer: 90 back, 90 handed over in cash.
        DelegateTrend november = trends.trend(new DelegateTrendFilter(secondDelegate, TrendGranularity.MONTH,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), false));
        assertMoney("-90.00", november.points().getFirst().netSales());
        assertMoney("-90.00", november.points().getFirst().collected());
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private static void seedTheMonth() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        firstDelegate = seedDelegate(STAMP + "-D1");
        secondDelegate = seedDelegate(STAMP + "-D2");
        customerWithDefault = seedCustomer(STAMP + "-C1", firstDelegate);
        customerWithNone = seedCustomer(STAMP + "-C2", 0);

        // invoice, customer, type (1 cash, 2 deferred), date, total, discount, paid, delegate
        invoice(9001, customerWithDefault, 2, "2026-10-05", "1000", "100", "400", firstDelegate);
        invoice(9002, customerWithNone, 1, "2026-10-06", "500", "0", "500", secondDelegate);
        invoice(9003, customerWithDefault, 2, "2026-09-20", "700", "0", "0", firstDelegate);
        invoice(9004, customerWithNone, 1, "2026-10-07", "200", "0", "200", 1);
        invoice(9005, customerWithNone, 2, "2026-10-08", "600", "0", "0", secondDelegate);
        invoice(9006, customerWithDefault, 2, "2026-10-09", "400", "0", "0", secondDelegate);

        // A return dated October of a September invoice counts in October; one dated November does not.
        salesReturn(9101, customerWithDefault, "2026-10-10", "300", "0", "50", firstDelegate);
        salesReturn(9102, customerWithNone, "2026-11-02", "90", "0", "90", secondDelegate);

        signIn(AppPermissions.CUSTOMER_ACCOUNT_CREATE);
        AccountCustomerService collections = new AccountCustomerService(DaoFactory.INSTANCE);
        assertEquals(1, collections.save(collection(customerWithDefault, 0, "250", "on account, default")));
        assertEquals(1, collections.save(collection(customerWithNone, 9005, "300", "allocated, no default")));
        assertEquals(1, collections.save(collection(customerWithNone, 0, "120", "on account, no default")));
        assertEquals(1, collections.save(collection(customerWithDefault, 9006, "150", "allocated, other delegate")));

        // As every row entered before V71 looks, and a credit note: purchase, not cash.
        execute("INSERT INTO customers_accounts (account_code, account_date, purchase, paid, notes, numberInv,"
                + " treasury_id, user_id) VALUES (" + customerWithDefault + ", '2026-10-01', 0, 1000, '"
                + STAMP + " before V71', 0, 1, 1)");
        execute("INSERT INTO customers_accounts (account_code, account_date, purchase, paid, notes, numberInv,"
                + " treasury_id, user_id) VALUES (" + customerWithDefault + ", '2026-10-15', -80, 0, '"
                + STAMP + " credit note', 0, 1, 1)");
    }

    private static void seedCollectionsAsV70WroteThem(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO employees (column_name, job, hire_date, salary, user_id) VALUES ('"
                    + STAMP + "-old', (SELECT MIN(id) FROM jobs WHERE is_delegate = 1), '2025-01-01', 0, 1)");
            statement.executeUpdate("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id,"
                    + " default_delegate_id) VALUES ('" + STAMP + "-old', 0, 0, 1, 1, 1,"
                    + " (SELECT id FROM employees WHERE column_name = '" + STAMP + "-old'))");
            for (String paid : List.of("1000", "500", "250")) {
                statement.executeUpdate("INSERT INTO customers_accounts (account_code, account_date, purchase, paid,"
                        + " notes, numberInv, treasury_id, user_id) VALUES ((SELECT id FROM custom WHERE name = '"
                        + STAMP + "-old'), '2026-01-10', 0, " + paid + ", '" + STAMP + "', 0, 1, 1)");
            }
        }
    }

    /** Every migration but V71 - and nothing after it, or Flyway would skip V71 as older than what is applied. */
    private static Path migrationsBeforeV71() throws Exception {
        Path source = Paths.get(DelegateActivityDatabaseAcceptanceTest.class.getResource("/db/migration").toURI());
        Path target = Files.createTempDirectory("delegate-migrations-v70-");
        target.toFile().deleteOnExit();
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.matches("V(\\d+)__.*") && Integer.parseInt(name.substring(1, name.indexOf("__"))) >= 71) {
                    continue;
                }
                String sql = Files.readString(file, StandardCharsets.UTF_8);
                if (name.equals("R__triggers.sql")) {
                    // A trigger cannot be created on a table that does not exist yet, and the V72
                    // section - kept last in the file for this - guards tables a V70 schema lacks.
                    int cut = sql.indexOf("-- commission run (V72)");
                    assertTrue(cut > 0, "the V72 section of R__triggers.sql is where this test expects it");
                    sql = sql.substring(0, cut);
                }
                Files.writeString(target.resolve(name), sql, StandardCharsets.UTF_8);
            }
        }
        return target;
    }

    private static int seedDelegate(String name) throws Exception {
        execute("INSERT INTO employees (column_name, job, hire_date, salary, user_id) VALUES ('" + name
                + "', (SELECT MIN(id) FROM jobs WHERE is_delegate = 1), '2025-01-01', 3000, 1)");
        return scalar("SELECT id FROM employees WHERE column_name = '" + name + "'");
    }

    private static int seedCustomer(String name, int defaultDelegate) throws Exception {
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id, default_delegate_id)"
                + " VALUES ('" + name + "', 0, 0, 1, 1, 1, " + defaultDelegate + ")");
        return scalar("SELECT id FROM custom WHERE name = '" + name + "'");
    }

    private static void invoice(int number, int customer, int type, String date, String total, String discount,
                                String paid, int delegate) throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + customer + ", " + type + ", '" + date
                + "', " + total + ", " + discount + ", " + paid + ", " + delegate + ", '" + STAMP + "')");
    }

    private static void salesReturn(int id, int customer, String date, String total, String discount,
                                    String refunded, int delegate) throws Exception {
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (" + id + ", " + customer + ", '" + date
                + "', 2, " + total + ", " + discount + ", " + refunded + ", " + delegate + ", '" + STAMP + "')");
    }

    private static CustomerAccount collection(int customer, int invoice, String paid, String what) {
        CustomerAccount account = new CustomerAccount(0, "2026-10-12", Double.parseDouble(paid),
                STAMP + " " + what, invoice, new Customers(customer), new Treasury(1));
        account.setUsers(new Users(OPERATOR));
        return account;
    }

    private static int delegateOf(String what) throws Exception {
        return scalar("SELECT COALESCE(delegate_id, -1) FROM customers_accounts WHERE notes = '"
                + STAMP + " " + what + "'");
    }

    private static DelegatePerformanceRow rowOf(DelegatePerformanceMonth month, int employeeId) {
        return month.rows().stream().filter(row -> row.activity().employeeId() == employeeId).findFirst()
                .orElseThrow(() -> new AssertionError("no row for employee " + employeeId));
    }

    private static DelegateActivity activityOf(DelegatePerformanceMonth month, int employeeId) {
        return rowOf(month, employeeId).activity();
    }

    private static CommissionTiers.Tier tier(String from, String rate) {
        return new CommissionTiers.Tier(new BigDecimal(from), new BigDecimal(rate));
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
        try (Connection connection = ConnectionManager.acquire()) {
            return decimal(connection, sql);
        }
    }

    private static BigDecimal decimal(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
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
