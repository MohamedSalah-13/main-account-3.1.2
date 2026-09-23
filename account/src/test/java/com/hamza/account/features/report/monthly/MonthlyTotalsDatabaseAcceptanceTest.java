package com.hamza.account.features.report.monthly;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The monthly totals against a real MySQL, worked out by hand, and held to the yearly report's own view.
 *
 * <p>"Today" is the 15th of March 2026. Sales: in January 2025 an invoice of 1,000 with 100 off and two of
 * 500 and 250 on one day; in March 2025 invoices of 2,000 (the 5th) and 400 (the 20th, after the same date)
 * and a return of 300 with 30 off (the 10th). In 2026: 800 with 50 off in January, a return of 100 in
 * February, 600 on the 14th of March. Purchases: 5,000 with 200 off and a return of 1,000 in February
 * 2025; 3,000 in March 2026.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} naming a config pair for
 * the server. <b>The session is never user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class MonthlyTotalsDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_mt_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "MT-" + System.nanoTime();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
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
    @DisplayName("the sales by the month, worked out by hand: a day's invoices added, a return taken off net")
    void theSalesByTheMonth() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        MonthlyTotalsReport report = new MonthlyTotalsService().report(MonthlySide.SALES, TODAY);

        assertEquals(List.of(2026, 2025), report.years().stream().map(YearRow::year).toList());
        YearRow last = report.years().get(1);
        // Compared piece by piece: a record's equals holds 0 and 0.00 apart, and MySQL answers the second.
        assertEquals(3, last.month(1).invoices(), "two invoices on the 20th are both counted");
        assertMoney("1750", last.month(1).gross(), "January before its discounts");
        assertMoney("100", last.month(1).discount(), "January's discounts");
        assertEquals(0, last.month(1).returnDocuments());
        assertMoney("2130", last.value(MonthlyMeasure.NET, 3), "2,400 sold less the 270 given back");
        assertMoney("0", last.value(MonthlyMeasure.NET, 7), "a quiet month is a zero");
        assertMoney("3780", last.total(MonthlyMeasure.NET), "the year");

        YearRow running = report.years().getFirst();
        assertEquals(3, running.lastMonth(), "the running year stops at this month");
        assertMoney("750", running.value(MonthlyMeasure.NET, 1), "800 less 50");
        assertMoney("-100", running.value(MonthlyMeasure.NET, 2), "a month of nothing but a return");
        assertNull(running.value(MonthlyMeasure.NET, 4));
    }

    @Test
    @DisplayName("this year so far against last year to the same date, and the highest month")
    void theComparison() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        MonthlyTotalsReport report = new MonthlyTotalsService().report(MonthlySide.SALES, TODAY);

        assertMoney("1250", report.yearToDate().net(), "this year");
        assertMoney("3380", report.sameDaysLastYear().net(), "the 400 of the 20th of March is after the date");
        assertEquals(Optional.of(new BigDecimal("-63.02")),
                MonthlyTotalsReport.change(report.yearToDate().net(), report.sameDaysLastYear().net()));
        assertMoney("3780", report.previousYear().net(), "last year in full");
        assertEquals(Optional.of(1), report.highestMonth(MonthlyMeasure.NET), "January's 750 over March's 600");
    }

    @Test
    @DisplayName("every month's net is the yearly report's own view's, on both sides")
    void heldToTheYearlyReportsView() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES, AppPermissions.REPORTS_SHOW_PURCHASE);
        MonthlyTotalsService service = new MonthlyTotalsService();
        MonthlyTotalsReport sales = service.report(MonthlySide.SALES, TODAY);
        MonthlyTotalsReport purchases = service.report(MonthlySide.PURCHASES, TODAY);
        int compared = 0;
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT report_year, report_month,
                            sales - sales_discount - (sales_return - sales_return_discount) AS net_sales,
                            purchases - purchases_discount - (purchases_return - purchases_return_discount)
                                AS net_purchases
                     FROM view_yearly_monthly_report""")) {
            while (rows.next()) {
                int year = rows.getInt("report_year");
                int month = rows.getInt("report_month");
                assertMoney(rows.getBigDecimal("net_sales").toPlainString(),
                        row(sales, year).value(MonthlyMeasure.NET, month), "sales " + year + "-" + month);
                // January 2025 holds a sale and no purchase, and comes before the first purchase: the
                // purchases do not read it at all, which the view's zero agrees with.
                BigDecimal purchased = row(purchases, year).value(MonthlyMeasure.NET, month);
                assertMoney(rows.getBigDecimal("net_purchases").toPlainString(),
                        purchased == null ? BigDecimal.ZERO : purchased, "purchases " + year + "-" + month);
                compared++;
            }
        }
        assertEquals(6, compared, "every month the view holds a document in");
        assertMoney("3800", row(purchases, 2025).value(MonthlyMeasure.NET, 2), "5,000 less 200 less 1,000");
        assertEquals(2, row(purchases, 2025).firstMonth(), "the purchases start at their first document's month");
    }

    @Test
    @DisplayName("the purchases ask their own key, and the two old views are gone from a migrated schema")
    void thePermissionAndTheOldViews() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        assertThrows(BusinessRuleException.class,
                () -> new MonthlyTotalsService().report(MonthlySide.PURCHASES, TODAY));

        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = '" + schema
                + "' AND TABLE_NAME IN ('view_monthly_sales', 'view_monthly_purchase')"));
    }

    // ---- the documents ---------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        int delegate = scalar("SELECT MIN(id) FROM employees");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('customer-" + STAMP + "', 0, 0, 1, 1, 1)");
        int customer = scalar("SELECT id FROM custom WHERE name = 'customer-" + STAMP + "'");
        execute("INSERT INTO suppliers (name) VALUES ('supplier-" + STAMP + "')");
        int supplier = scalar("SELECT id FROM suppliers WHERE name = 'supplier-" + STAMP + "'");
        int stock = scalar("SELECT MIN(stock_id) FROM stocks");

        sale(9001, customer, "2025-01-10", "1000", "100", delegate);
        sale(9002, customer, "2025-01-20", "500", "0", delegate);
        sale(9003, customer, "2025-01-20", "250", "0", delegate);
        sale(9004, customer, "2025-03-05", "2000", "0", delegate);
        sale(9005, customer, "2025-03-20", "400", "0", delegate);
        salesReturn(9101, customer, "2025-03-10", "300", "30", delegate);
        sale(9006, customer, "2026-01-05", "800", "50", delegate);
        salesReturn(9102, customer, "2026-02-10", "100", "0", delegate);
        sale(9007, customer, "2026-03-14", "600", "0", delegate);

        purchase(9501, supplier, "2025-02-02", "5000", "200");
        execute("INSERT INTO total_buy_re (id, sup_id, invoice_type, invoice_date, total, discount,"
                + " paid_to_treasury, stock_id, notes) VALUES (9601, " + supplier + ", 2, '2025-02-20', 1000, 0, 0, "
                + stock + ", '" + STAMP + "')");
        purchase(9502, supplier, "2026-03-01", "3000", "0");
    }

    private static void sale(int number, int party, String date, String total, String discount, int delegate)
            throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + party + ", 2, '" + date + "', "
                + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    private static void salesReturn(int number, int party, String date, String total, String discount, int delegate)
            throws Exception {
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (" + number + ", " + party + ", '" + date
                + "', 2, " + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    private static void purchase(int number, int supplier, String date, String total, String discount)
            throws Exception {
        execute("INSERT INTO total_buy (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, notes) VALUES (" + number + ", " + supplier + ", 2, '" + date + "', " + total + ", "
                + discount + ", 0, '" + STAMP + "')");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static YearRow row(MonthlyTotalsReport report, int year) {
        return report.years().stream().filter(row -> row.year() == year).findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + year));
    }

    private static void assertMoney(String expected, BigDecimal actual, String what) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), what + ": expected " + expected + ", was " + actual);
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
