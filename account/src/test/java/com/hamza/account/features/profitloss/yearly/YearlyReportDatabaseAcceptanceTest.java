package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.profitloss.ProfitLossDao;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.account.features.profitloss.ProfitLossService;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2025, worked out by hand, against a real MySQL - and the claim the yearly report rests on: <b>it is the
 * profit and loss statement grouped by the month</b>, so the year it reports is the statement's year to the
 * piastre, and the breakdown under each month's net sales adds up to them.
 *
 * <p>January 2025: a sale of 1,000 with 100 off (its lines cost 600); a return of 200 with 20 off (cost
 * 120); a purchase of 500 with 50 off and a purchase return of 100 with 10 off; an expense of 70. So the
 * month sold 1,000 - 100 - 180 = 720, its goods cost 600 - 120 = 480, it made 240 gross and 170 net, and
 * it bought 500 - 50 - 90 = 360, which is in no profit. March 2025: a sale of 300 costing 200. The year
 * before: 500 on 15 January, 80 on 10 March and 100 on 20 December 2024.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} naming a config pair for
 * the server. <b>The session is never user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class YearlyReportDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_yearly_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "YR-" + System.nanoTime();
    private static final Clock NEXT_YEAR = clockOn(LocalDate.of(2026, 6, 1));

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
    @DisplayName("January and March are worked out by hand, and the quiet months are zeros")
    void theMonthsByHand() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        YearlyReport report = service(NEXT_YEAR).report(2025);

        assertEquals(12, report.rows().size());
        YearlyReportRow january = report.rows().get(0);
        assertMoney("720", january.netSales(), "January's net sales");
        assertMoney("480", january.costOfSales(), "January's cost");
        assertMoney("240", january.grossProfit(), "January's gross profit");
        assertMoney("70", january.expenses(), "January's expenses");
        assertMoney("170", january.netProfit(), "January's net profit");
        assertMoney("1000", january.grossSales(), "January's invoices before their discounts");
        assertMoney("100", january.salesDiscount(), "January's invoice discounts");
        assertMoney("180", january.salesReturns(), "the return, net of its own discount");
        assertMoney("360", january.netPurchases(), "bought, and in no profit");
        assertEquals(3, january.days().size(), "the sale, the return and the expense are on three days");

        assertFalse(report.rows().get(1).hasActivity(), "February traded nothing");
        assertMoney("300", report.rows().get(2).netSales(), "March");
        assertMoney("100", report.rows().get(2).netProfit(), "March");

        YearlySummary summary = report.summary();
        assertMoney("1020", summary.current().netSales(), "the year's net sales");
        assertMoney("270", summary.current().netProfit(), "the year's net profit");
        assertEquals(0, summary.unexplainedSales().signum(), "every month's breakdown adds up to its net sales");
    }

    /** The whole claim: the report's year is the statement's year, read by the statement itself. */
    @Test
    @DisplayName("the year's five figures are the profit and loss statement's own for the same dates")
    void theYearIsTheStatementsYear() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        YearlySummary summary = service(NEXT_YEAR).report(2025).summary();
        List<ProfitLossRow> statement = new ProfitLossService(new ProfitLossDao())
                .load(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        MonthFigures year = MonthFigures.ZERO;
        for (ProfitLossRow day : statement) {
            year = year.plus(day);
        }
        assertMoney(year.netSales().toPlainString(), summary.current().netSales(), "net sales");
        assertMoney(year.costOfSales().toPlainString(), summary.current().costOfSales(), "cost of sales");
        assertMoney(year.grossProfit().toPlainString(), summary.current().grossProfit(), "gross profit");
        assertMoney(year.expenses().toPlainString(), summary.current().expenses(), "expenses");
        assertMoney(year.netProfit().toPlainString(), summary.current().netProfit(), "net profit");
    }

    @Test
    @DisplayName("a past year is set against the whole year before it")
    void aPastYearAgainstTheWholeYearBefore() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        YearlyReport report = service(NEXT_YEAR).report(2025);

        assertMoney("500", report.rows().get(0).previousNetSales(), "January 2024");
        assertMoney("80", report.rows().get(2).previousNetSales(), "March 2024");
        assertMoney("100", report.rows().get(11).previousNetSales(), "December 2024");
        assertMoney("680", report.summary().previous().netSales(), "the whole of 2024");
        assertEquals(Optional.of(new BigDecimal("50.00")), report.summary().netSalesChange(),
                "1,020 against 680");
    }

    @Test
    @DisplayName("a running year stops at this month and sets it against the year before to the same day")
    void aRunningYearToTheSameDay() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        YearlyReport report = service(clockOn(LocalDate.of(2025, 3, 4))).report(2025);

        assertEquals(3, report.rows().size(), "January to March");
        assertMoney("300", report.rows().get(2).netSales(),
                "March is read to its end - the breakdown knows only whole months");
        assertMoney("0", report.rows().get(2).previousNetSales(), "10 March 2024 is after the 4th");
        assertMoney("500", report.summary().previous().netSales(), "2024 to the 4th of March");
        assertTrue(report.period().toDate());
    }

    @Test
    @DisplayName("the years offered are the documents' and this one, newest first")
    void theYears() throws Exception {
        List<Integer> years = service(NEXT_YEAR).years();

        assertEquals(List.of(2026, 2025, 2024), years);
    }

    @Test
    @DisplayName("without the profit report's permission nothing is read")
    void thePermissionIsTheStatements() {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);

        assertThrows(BusinessRuleException.class, () -> service(NEXT_YEAR).report(2025));
    }

    // ---- 2025 and 2024 ------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        int delegate = scalar("SELECT MIN(id) FROM employees");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + STAMP + "-C', 0, 0, 1, 1, 1)");
        int customer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-C'");
        execute("INSERT INTO suppliers (name) VALUES ('" + STAMP + "-S')");
        int supplier = scalar("SELECT id FROM suppliers WHERE name = '" + STAMP + "-S'");
        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-M')");
        int main = scalar("SELECT id FROM main_group WHERE name_g = '" + STAMP + "-M'");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-G', " + main + ")");
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id, buy_price) VALUES ('" + STAMP
                + "-I', '" + STAMP + "-I', (SELECT id FROM sub_group WHERE main_id = " + main
                + "), (SELECT MIN(unit_id) FROM units), 6)");
        int item = scalar("SELECT id FROM items WHERE nameItem = '" + STAMP + "-I'");

        // January 2025
        sale(8001, customer, "2025-01-10", "1000", "100", delegate);
        line("sales", "num", 8001, item, "100", "10", "1000", "600");
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (8101, " + customer
                + ", '2025-01-20', 2, 200, 20, 0, " + delegate + ", '" + STAMP + "')");
        line("sales_re", "item_id", 8101, item, "20", "10", "200", "120");
        execute("INSERT INTO total_buy (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, notes) VALUES (8501, " + supplier + ", 2, '2025-01-25', 500, 50, 0, '" + STAMP + "')");
        execute("INSERT INTO total_buy_re (id, sup_id, invoice_type, invoice_date, total, discount,"
                + " paid_to_treasury, stock_id, notes) VALUES (8601, " + supplier
                + ", 2, '2025-01-26', 100, 10, 0, (SELECT MIN(stock_id) FROM stocks), '" + STAMP + "')");
        execute("INSERT INTO expenses_details (type_code, date, amount, notes, emp_id, treasury_id, user_id)"
                + " VALUES ((SELECT MIN(id) FROM expenses), '2025-01-28', 70, '" + STAMP
                + "', NULL, (SELECT MIN(id) FROM treasury), 1)");
        // March 2025
        sale(8002, customer, "2025-03-05", "300", "0", delegate);
        line("sales", "num", 8002, item, "30", "10", "300", "200");
        // 2024
        sale(7001, customer, "2024-01-15", "500", "0", delegate);
        line("sales", "num", 7001, item, "50", "10", "500", "350");
        sale(7002, customer, "2024-03-10", "80", "0", delegate);
        line("sales", "num", 7002, item, "8", "10", "80", "40");
        sale(7003, customer, "2024-12-20", "100", "0", delegate);
        line("sales", "num", 7003, item, "10", "10", "100", "50");
    }

    private static void sale(int number, int party, String date, String total, String discount, int delegate)
            throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + party + ", 2, '" + date + "', "
                + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    private static void line(String table, String itemColumn, int document, int item, String quantity,
                             String price, String gross, String cost) throws Exception {
        execute("INSERT INTO " + table + " (invoice_number, " + itemColumn + ", type, quantity, price, buy_price,"
                + " total_sel_price, total_buy_price, total_profit, discount, type_value) VALUES (" + document
                + ", " + item + ", (SELECT MIN(unit_id) FROM units), " + quantity + ", " + price + ", 0, " + gross
                + ", " + cost + ", 0, 0, 1)");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static YearlyReportService service(Clock clock) {
        return new YearlyReportService(new ProfitLossService(new ProfitLossDao())::load,
                new JdbcYearlyBreakdownRepository(), clock);
    }

    private static Clock clockOn(LocalDate day) {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(day.atTime(12, 0).atZone(zone).toInstant(), zone);
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
