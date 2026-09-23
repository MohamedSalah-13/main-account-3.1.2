package com.hamza.account.features.profitloss.statement;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.profitloss.ProfitLossDao;
import com.hamza.account.features.profitloss.ProfitLossFigures;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * September 2025, worked out by hand, against a real MySQL - and the claim the profit and loss screen rests
 * on: <b>every figure it shows is the statement's own days, summed</b>, and the lines that explain them add
 * up to them.
 *
 * <p>September: a sale of 1,000 with 100 off whose lines cost 600; a return of 200 with 20 off whose goods
 * cost 120; expenses of 70 under a main heading, 30 under one of its sub-headings and 40 under a second
 * main heading. So the month sold 1,000 - 100 - 180 = 720, its goods cost 600 - 120 = 480, it made 240
 * gross and 100 net. A posted count found two pieces missing at 6 and two over at 4 (12 short, 8 over), and
 * a draft count beside it counts for nothing; two shifts closed 15 short and 5 over, and a third, closed a
 * few minutes into October, is October's. August: a sale of 500 costing 300 and 50 spent.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} naming a config pair for
 * the server. <b>The session is never user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ProfitLossStatementDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_pl_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "PL-" + System.nanoTime();
    private static final ProfitLossPeriod SEPTEMBER =
            new ProfitLossPeriod(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30));

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int rent;
    private static int wages;

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
    @DisplayName("September's statement, worked out by hand, line by line")
    void theStatementByHand() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        ProfitLossReport report = service().report(SEPTEMBER, ComparisonBasis.PREVIOUS_PERIOD, ProfitLossGrouping.DAY);

        assertMoney("1000", line(report, "profitloss.line.gross.sales").current(), "sales before discounts");
        assertMoney("-100", line(report, "profitloss.line.invoice.discounts").current(), "invoice discounts");
        assertMoney("-180", line(report, "profitloss.line.returns").current(), "the return, net of its discount");
        assertMoney("720", line(report, "profitloss.net.sales").current(), "net sales");
        assertMoney("-600", line(report, "profitloss.line.cost.sold").current(), "cost of goods sold");
        assertMoney("120", line(report, "profitloss.line.cost.returned").current(), "cost of goods returned");
        assertMoney("-480", line(report, "profitloss.cost.sales").current(), "cost of sales");
        assertMoney("240", line(report, "profitloss.gross.profit").current(), "gross profit");
        assertMoney("-100", named(report, "rent-" + STAMP).current(), "a main heading with its sub-heading");
        assertMoney("-40", named(report, "wages-" + STAMP).current(), "the second main heading");
        assertMoney("-140", line(report, "profitloss.line.expenses.total").current(), "expenses");
        assertMoney("100", line(report, "profitloss.net.profit").current(), "net profit");
        assertTrue(report.statement().stream().noneMatch(line -> "profitloss.line.unexplained".equals(line.messageKey())),
                "every section adds up to the statement's own figure");

        assertMoney("12", report.outside().stockShortage(), "two pieces missing at 6");
        assertMoney("8", report.outside().stockSurplus(), "two pieces over at 4 - the draft counts for nothing");
        assertMoney("15", report.outside().tillShortage(), "a till 15 short");
        assertMoney("5", report.outside().tillSurplus(), "a till 5 over - October's shortage is October's");
        assertMoney("-14", line(report, "profitloss.line.outside.net").current(), "what is outside the profit");
        assertMoney("100", report.current().netProfit(), "and the profit does not move for it");
    }

    @Test
    @DisplayName("August is the period before, and is read the same way")
    void thePeriodBefore() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        ProfitLossReport report = service().report(SEPTEMBER, ComparisonBasis.PREVIOUS_PERIOD, ProfitLossGrouping.DAY);

        assertEquals(new ProfitLossPeriod(LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 31)), report.previousPeriod());
        assertMoney("500", report.previous().netSales(), "August's net sales");
        assertMoney("150", report.previous().netProfit(), "August's net profit");
        assertMoney("500", line(report, "profitloss.line.gross.sales").previous(), "August's sales");
        assertMoney("-50", named(report, "rent-" + STAMP).previous(), "August's rent");
        assertMoney("0", named(report, "wages-" + STAMP).previous(), "no wages in August");
    }

    /** The whole claim: the screen's figures are the statement's, read by the statement itself. */
    @Test
    @DisplayName("the cards, the subtotals and the rows are the statement's own days, summed")
    void everythingIsTheStatementsDays() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        List<ProfitLossRow> days = new ProfitLossService(new ProfitLossDao()).load(SEPTEMBER.from(), SEPTEMBER.to());
        ProfitLossFigures month = ProfitLossFigures.ZERO;
        for (ProfitLossRow day : days) {
            month = month.plus(day);
        }

        for (ProfitLossGrouping grouping : ProfitLossGrouping.values()) {
            ProfitLossReport report = service().report(SEPTEMBER, ComparisonBasis.SAME_PERIOD_LAST_YEAR, grouping);
            assertEquals(month, report.current(), grouping + ": the cards");
            ProfitLossFigures rows = ProfitLossFigures.ZERO;
            for (ProfitLossPeriodRow row : report.rows()) {
                rows = rows.plus(row.figures());
            }
            assertEquals(month, rows, grouping + ": the rows add up to the period");
        }
        assertEquals(30, service().report(SEPTEMBER, ComparisonBasis.PREVIOUS_PERIOD, ProfitLossGrouping.DAY)
                .rows().size(), "a quiet day is a row");
    }

    @Test
    @DisplayName("a row's movements add up to the row")
    void movementsAddUpToTheRow() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        ProfitLossReportService service = service();
        ProfitLossPeriodRow month = service.report(SEPTEMBER, ComparisonBasis.PREVIOUS_PERIOD,
                ProfitLossGrouping.MONTH).rows().get(0);

        List<ProfitLossMovement> movements = service.movements(month);
        assertEquals(5, movements.size(), "a sale, a return and three expenses");
        BigDecimal netSales = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (ProfitLossMovement movement : movements) {
            netSales = netSales.add(movement.netSales());
            cost = cost.add(movement.cost());
            expenses = expenses.add(movement.expense());
        }
        assertMoney(month.figures().netSales().toPlainString(), netSales, "net sales");
        assertMoney(month.figures().costOfSales().toPlainString(), cost, "cost");
        assertMoney(month.figures().expenses().toPlainString(), expenses, "expenses");

        ProfitLossMovement returned = movements.stream()
                .filter(movement -> movement.kind() == ProfitLossMovement.Kind.SALE_RETURN).findFirst().orElseThrow();
        assertMoney("-180", returned.netSales(), "a return is signed against the sales");
        assertMoney("-60", returned.profit(), "and takes its profit back");
        assertEquals("customer-" + STAMP, returned.name());
    }

    @Test
    @DisplayName("without the profit report's permission nothing is read")
    void thePermissionIsTheStatements() {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        ProfitLossPeriodRow row = new ProfitLossPeriodRow(SEPTEMBER.from(), SEPTEMBER.to(), ProfitLossFigures.ZERO,
                List.of());

        assertThrows(BusinessRuleException.class,
                () -> service().report(SEPTEMBER, ComparisonBasis.PREVIOUS_PERIOD, ProfitLossGrouping.DAY));
        assertThrows(BusinessRuleException.class, () -> service().movements(row));
    }

    // ---- September and August 2025 ------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        int delegate = scalar("SELECT MIN(id) FROM employees");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('customer-" + STAMP + "', 0, 0, 1, 1, 1)");
        int customer = scalar("SELECT id FROM custom WHERE name = 'customer-" + STAMP + "'");
        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-M')");
        int main = scalar("SELECT id FROM main_group WHERE name_g = '" + STAMP + "-M'");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-G', " + main + ")");
        int piece = item("A", "6", main);
        int other = item("B", "4", main);

        execute("INSERT INTO expenses (expenses_name) VALUES ('rent-" + STAMP + "')");
        rent = scalar("SELECT id FROM expenses WHERE expenses_name = 'rent-" + STAMP + "'");
        execute("INSERT INTO expenses (expenses_name, parent_id) VALUES ('rent-fees-" + STAMP + "', " + rent + ")");
        int rentFees = scalar("SELECT id FROM expenses WHERE expenses_name = 'rent-fees-" + STAMP + "'");
        execute("INSERT INTO expenses (expenses_name) VALUES ('wages-" + STAMP + "')");
        wages = scalar("SELECT id FROM expenses WHERE expenses_name = 'wages-" + STAMP + "'");

        // September 2025
        sale(9001, customer, "2025-09-03", "1000", "100", delegate);
        line("sales", "num", 9001, piece, "100", "10", "1000", "600");
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (9101, " + customer
                + ", '2025-09-10', 2, 200, 20, 0, " + delegate + ", '" + STAMP + "')");
        line("sales_re", "item_id", 9101, piece, "20", "10", "200", "120");
        expense(rent, "2025-09-05", "70");
        expense(rentFees, "2025-09-12", "30");
        expense(wages, "2025-09-12", "40");
        count("2025-09-20", "POSTED", piece, "10", "8", other, "3", "5");
        count("2025-09-21", "DRAFT", piece, "10", "1", other, "3", "3");
        closedShift("2025-09-25 14:00:00", "-15");
        closedShift("2025-09-30 23:30:00", "5");
        closedShift("2025-10-01 00:10:00", "-100");
        // August 2025
        sale(8001, customer, "2025-08-03", "500", "0", delegate);
        line("sales", "num", 8001, piece, "50", "10", "500", "300");
        expense(rent, "2025-08-05", "50");
    }

    private static int item(String name, String buyPrice, int main) throws Exception {
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id, buy_price) VALUES ('" + STAMP + name
                + "', '" + STAMP + name + "', (SELECT id FROM sub_group WHERE main_id = " + main
                + "), (SELECT MIN(unit_id) FROM units), " + buyPrice + ")");
        return scalar("SELECT id FROM items WHERE nameItem = '" + STAMP + name + "'");
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

    private static void expense(int heading, String date, String amount) throws Exception {
        execute("INSERT INTO expenses_details (type_code, date, amount, notes, treasury_id, user_id) VALUES ("
                + heading + ", '" + date + "', " + amount + ", '" + STAMP + "', (SELECT MIN(id) FROM treasury), 1)");
    }

    /** A count of two lines, each (item, book, counted) in base units. */
    private static void count(String date, String status, int first, String firstBook, String firstCounted,
                              int second, String secondBook, String secondCounted) throws Exception {
        execute("INSERT INTO stock_count (stock_id, count_date, status, notes, user_id) VALUES ("
                + "(SELECT MIN(stock_id) FROM stocks), '" + date + "', '" + status + "', '" + STAMP + "', 1)");
        int count = scalar("SELECT MAX(id) FROM stock_count");
        for (String[] line : new String[][]{{String.valueOf(first), firstBook, firstCounted},
                {String.valueOf(second), secondBook, secondCounted}}) {
            execute("INSERT INTO stock_count_lines (count_id, item_id, unit_id, type_value, system_qty, counted_qty)"
                    + " VALUES (" + count + ", " + line[0] + ", (SELECT MIN(unit_id) FROM units), 1, " + line[1]
                    + ", " + line[2] + ")");
        }
    }

    private static void closedShift(String closedAt, String difference) throws Exception {
        execute("INSERT INTO user_shifts (user_id, open_time, close_time, is_open, shift_status, treasury_id)"
                + " VALUES (" + OPERATOR + ", DATE_SUB('" + closedAt + "', INTERVAL 8 HOUR), '" + closedAt
                + "', FALSE, 'CLOSED', (SELECT MIN(id) FROM treasury))");
        int shift = scalar("SELECT MAX(id) FROM user_shifts");
        execute("INSERT INTO shift_close_snapshots (shift_id, closed_by_user_id, shift_status, open_time, close_time,"
                + " open_balance, actual_balance, expected_balance, difference_amount, total_sales,"
                + " total_sales_returns, total_expenses, total_deposits, total_withdrawals, total_cash_in,"
                + " total_cash_out, invoices_count) SELECT id, user_id, 'CLOSED', open_time, close_time, 0, "
                + "100 + " + difference + ", 100, " + difference + ", 0, 0, 0, 0, 0, 0, 0, 0 FROM user_shifts"
                + " WHERE id = " + shift);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static ProfitLossReportService service() {
        return new ProfitLossReportService(new ProfitLossService(new ProfitLossDao())::load,
                new JdbcProfitLossStatementRepository());
    }

    private static StatementLine line(ProfitLossReport report, String key) {
        return report.statement().stream().filter(line -> key.equals(line.messageKey())).findFirst()
                .orElseThrow(() -> new AssertionError("no line " + key));
    }

    private static StatementLine named(ProfitLossReport report, String name) {
        return report.statement().stream().filter(line -> name.equals(line.name())).findFirst()
                .orElseThrow(() -> new AssertionError("no heading " + name));
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
