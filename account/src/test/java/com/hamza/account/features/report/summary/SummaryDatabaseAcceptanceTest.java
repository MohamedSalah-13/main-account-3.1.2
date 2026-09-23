package com.hamza.account.features.report.summary;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
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
 * The summary against a real MySQL, worked out by hand. "Today" is the 23rd of September 2026, and the
 * period is this month so far, set against the 1st to the 23rd of August.
 *
 * <p>Sales: customer A a cash invoice of 1,000 with 100 off on the 5th (paid 900); customer B a deferred
 * invoice of 400 on the 6th; A returns 200 with 20 off on the 10th, refunded 180 in cash. In August A bought
 * 500 on the 10th, and 700 on the 30th - after the 23rd, so outside the comparison. A cash purchase of 600 on
 * the 12th. B pays 300 on account on the 15th. A deposit of 1,000 on the 16th, a transfer of 250 between two
 * treasuries on the 17th, an expense of 50 on the 18th, and a second treasury opened with 5,000.</p>
 *
 * <p>So September's net sales are 1,400 less 100 less 180 = 1,120 against August's 500 (+124%); the
 * treasuries took in 900 + 300 + 1,000 = 2,200 and paid out 180 + 600 + 50 = 830 - the transfer and the
 * opening balance being no money into or out of the business; B owes 100 and nobody else anything.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG}. <b>The session is never
 * user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class SummaryDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_sum_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "SUM-" + System.nanoTime();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);
    private static final ProfitLossPeriod MONTH = SummaryPeriod.MONTH.range(TODAY);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int shortItem;
    private static int oversold;

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
    @DisplayName("the net sales and purchases of the month so far, against the same days of August")
    void theSalesAndPurchases() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY, AppPermissions.REPORTS_SHOW_SALES,
                AppPermissions.REPORTS_SHOW_PURCHASE);
        Summary summary = new SummaryService().load(SummaryPeriod.MONTH, MONTH, TODAY);

        assertEquals(new ProfitLossPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 23)), summary.previous());
        assertMoney("1120", summary.sales().net(), "1,400 less 100 less the 180 given back");
        assertEquals(2, summary.sales().invoices());
        assertMoney("100", summary.sales().discount(), "the sales' own discounts, and nothing of the purchases'");
        assertMoney("500", summary.previousSales().net(), "the 700 of the 30th of August is after the 23rd");
        assertEquals(Optional.of(new BigDecimal("124.00")), summary.salesChange());
        assertMoney("600", summary.purchases().net(), "the purchase");
        assertEquals(1, summary.purchases().invoices());

        assertEquals(SummaryService.TREND_DAYS, summary.trend().size());
        assertEquals(LocalDate.of(2026, 9, 10), summary.trend().getFirst().day());
        assertMoney("-180", summary.trend().getFirst().net(), "the day of the return is on the line, below zero");
        assertMoney("0", summary.trend().getLast().net(), "a quiet today is a zero, not a gap");
    }

    @Test
    @DisplayName("the cash is the treasuries' movements, less a transfer between them and an opening balance")
    void theCash() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY, AppPermissions.TREASURY_SHOW);
        Summary summary = new SummaryService().load(SummaryPeriod.MONTH, MONTH, TODAY);

        assertMoney("2200", summary.cash().in(), "the sale's 900, the collection's 300 and the deposit's 1,000");
        assertMoney("830", summary.cash().out(), "the refund's 180, the purchase's 600 and the expense's 50");
        assertMoney("500", summary.previousCash().in(), "August's cash sale");
        assertEquals(2, summary.treasuries().size());
        assertNull(summary.sales(), "no sales key, no sales read");
    }

    @Test
    @DisplayName("the debts are the customer balances screen's own: one customer owing 100")
    void theDebts() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY, AppPermissions.CUSTOMER_ACCOUNT_SHOW);
        Summary summary = new SummaryService().load(SummaryPeriod.MONTH, MONTH, TODAY);

        assertEquals(1, summary.receivables().debtors());
        assertMoney("100", summary.receivables().owed(), "400 less the 300 paid on account");
        assertEquals(STAMP + "-B", summary.receivables().top().getFirst().name());
    }

    @Test
    @DisplayName("the low stock is the notification's: an item sold below zero first, then one out; not one in stock")
    void theLowStock() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY, AppPermissions.ITEMS_SHOW);
        LowStock lowStock = new SummaryService().load(SummaryPeriod.MONTH, MONTH, TODAY).lowStock();

        assertEquals(2, lowStock.count());
        assertEquals(List.of(oversold, shortItem), lowStock.items().stream().map(LowStockItem::itemId).toList());
        assertMoney("-2", lowStock.items().getFirst().balance(), "two sold of none");
        assertMoney("5", lowStock.items().get(1).minimum(), "its minimum");
    }

    @Test
    @DisplayName("a reader with the summary's key alone is shown, and read, nothing")
    void theSummaryKeyAlone() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SUMMARY);
        Summary summary = new SummaryService().load(SummaryPeriod.MONTH, MONTH, TODAY);

        assertTrue(summary.cards().isEmpty());
        assertNull(summary.cash());
        assertNull(summary.receivables());
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        assertThrows(BusinessRuleException.class, () -> new SummaryService().load(SummaryPeriod.MONTH, MONTH, TODAY));
    }

    // ---- September ----------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        execute("UPDATE treasury SET amount = 0");
        int drawer = scalar("SELECT MIN(id) FROM treasury");
        execute("INSERT INTO treasury (t_name, amount) VALUES ('" + STAMP + "-W', 5000)");
        int wallet = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-W'");
        execute("UPDATE custom SET first_balance = 0");
        int customerA = customer(STAMP + "-A");
        int customerB = customer(STAMP + "-B");
        execute("INSERT INTO suppliers (name) VALUES ('" + STAMP + "-S')");
        int supplier = scalar("SELECT id FROM suppliers WHERE name = '" + STAMP + "-S'");
        int delegate = scalar("SELECT MIN(id) FROM employees");

        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-G')");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-SUB', (SELECT id FROM main_group"
                + " WHERE name_g = '" + STAMP + "-G'))");
        shortItem = item(STAMP + "-SHORT", "5", "0");
        oversold = item(STAMP + "-OVERSOLD", "0", "0");
        item(STAMP + "-PLENTY", "0", "10");

        sale(9001, customerA, "2026-09-05", 1, "1000", "100", "900", delegate);
        execute("INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price, total_sel_price,"
                + " total_buy_price, total_profit, discount, type_value) VALUES (9001, " + oversold
                + ", (SELECT MIN(unit_id) FROM units), 2, 10, 0, 20, 0, 0, 0, 1)");
        sale(9002, customerB, "2026-09-06", 2, "400", "0", "0", delegate);
        sale(9003, customerA, "2026-08-10", 1, "500", "0", "500", delegate);
        sale(9004, customerA, "2026-08-30", 1, "700", "0", "700", delegate);
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (9101, " + customerA
                + ", '2026-09-10', 1, 200, 20, 180, " + delegate + ", '" + STAMP + "')");
        execute("INSERT INTO total_buy (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, notes) VALUES (9201, " + supplier + ", 1, '2026-09-12', 600, 0, 600, '" + STAMP + "')");
        execute("INSERT INTO customers_accounts (account_code, account_date, purchase, paid, notes, numberInv)"
                + " VALUES (" + customerB + ", '2026-09-15', 0, 300, '" + STAMP + "', 0)");
        execute("INSERT INTO treasury_deposit_expenses (statement, date_inter, amount, description_data,"
                + " deposit_or_expenses, treasury_id, user_id, category) VALUES ('" + STAMP + "', '2026-09-16', 1000, '"
                + STAMP + "', 1, " + drawer + ", 1, 'NORMAL')");
        execute("INSERT INTO treasury_transfers (treasury_from, treasury_to, amount, transfer_date, notes, user_id)"
                + " VALUES (" + drawer + ", " + wallet + ", 250, '2026-09-17', '" + STAMP + "', 1)");
        execute("INSERT INTO expenses_details (type_code, date, amount, notes, treasury_id, user_id) VALUES ("
                + "(SELECT MIN(id) FROM expenses), '2026-09-18', 50, '" + STAMP + "', " + drawer + ", 1)");
    }

    private static int customer(String name) throws Exception {
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + name + "', 0, 0, 1, 1, 1)");
        return scalar("SELECT id FROM custom WHERE name = '" + name + "'");
    }

    /** An item with its minimum and its opening balance in the default warehouse. */
    private static int item(String name, String minimum, String opening) throws Exception {
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id, buy_price, mini_quantity) VALUES ('" + name
                + "', '" + name + "', (SELECT id FROM sub_group WHERE name = '" + STAMP + "-SUB'),"
                + " (SELECT MIN(unit_id) FROM units), 1, " + minimum + ")");
        int id = scalar("SELECT id FROM items WHERE nameItem = '" + name + "'");
        execute("INSERT INTO items_stock (item_id, stock_id, first_balance) VALUES (" + id
                + ", (SELECT MIN(stock_id) FROM stocks), " + opening + ")");
        return id;
    }

    private static void sale(int number, int party, String date, int type, String total, String discount,
                             String paid, int delegate) throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + party + ", " + type + ", '" + date + "', "
                + total + ", " + discount + ", " + paid + ", " + delegate + ", '" + STAMP + "')");
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Compared by value: MySQL answers 540.00 where the arithmetic says 540. */
    private static void assertMoney(String expected, BigDecimal actual, String what) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), what + ": expected " + expected + " but was " + actual);
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
