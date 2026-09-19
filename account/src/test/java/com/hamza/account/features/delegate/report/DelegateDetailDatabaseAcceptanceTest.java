package com.hamza.account.features.delegate.report;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.delegate.DelegateActivity;
import com.hamza.account.features.delegate.JdbcDelegateActivityRepository;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One delegate's October, worked out by hand, against a real MySQL - and the one claim the
 * package exists for: <b>every breakdown comes to the net the performance report shows</b> for
 * the same delegate and month, read here from {@code DelegateActivityQuery} itself rather than
 * typed in twice.
 *
 * <p>The month holds what a pinned statement cannot show to be right: an invoice with a discount
 * on a line <em>and</em> on the whole document, a line sold in a unit of twelve, a return in
 * October, another delegate's invoice, an invoice of September, a credit note, a collection of
 * somebody else's and one that names nobody.
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}; the configured
 * database is a credential carrier and is never opened. <b>The session is never user 1.</b>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class DelegateDetailDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_delegate_detail_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "DDT-" + System.nanoTime();
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private static final DelegateDetailService SERVICE = new DelegateDetailService();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int delegate;
    private static int customerOne;
    private static int customerTwo;
    private static int itemOne;
    private static int itemTwo;

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
            signIn(AppPermissions.COMMISSION_REPORTS);
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

    @Test
    @DisplayName("by customer: the documents' own net, one row per customer, the return against its customer")
    void byCustomer() throws Exception {
        var loaded = SERVICE.breakdown(DelegateBreakdown.CUSTOMER, filter());
        assertEquals(2, loaded.rows().size());
        DelegateDetailRow first = row(loaded.rows(), customerTwo);
        assertMoney("500.00", first.sales());
        assertMoney("0.00", first.returns());
        DelegateDetailRow second = row(loaded.rows(), customerOne);
        assertMoney("450.00", second.sales());
        assertMoney("90.00", second.returns());
        assertMoney("1", second.measure());
        assertEquals(customerTwo, loaded.rows().get(0).keyId(), "largest net first");
        assertMoney("860.00", loaded.summary().net());
    }

    @Test
    @DisplayName("by area: the customer's area, and the same net")
    void byArea() throws Exception {
        var loaded = SERVICE.breakdown(DelegateBreakdown.AREA, filter());
        assertEquals(2, loaded.rows().size());
        assertMoney("860.00", loaded.summary().net());
        assertTrue(loaded.rows().stream().anyMatch(row -> row.name().equals(STAMP + "-AREA")));
    }

    @Test
    @DisplayName("by item: line values, quantity in base units, and the header discounts standing between them and the net")
    void byItem() throws Exception {
        var loaded = SERVICE.breakdown(DelegateBreakdown.ITEM, filter());
        DelegateDetailRow one = row(loaded.rows(), itemOne);
        assertMoney("690.00", one.sales());
        assertMoney("95.00", one.returns());
        assertMoney("6", one.measure());
        DelegateDetailRow two = row(loaded.rows(), itemTwo);
        assertMoney("300.00", two.sales());
        assertMoney("12", two.measure());
        assertMoney("990.00", loaded.summary().sales());
        assertMoney("35.00", loaded.summary().headerDiscount());
        assertMoney("860.00", loaded.summary().net());
    }

    @Test
    @DisplayName("by group: the items' sub group, and the same net again")
    void byGroup() throws Exception {
        var loaded = SERVICE.breakdown(DelegateBreakdown.GROUP, filter());
        assertEquals(2, loaded.rows().size());
        assertMoney("860.00", loaded.summary().net());
    }

    /** Read from the activity query, not typed in: the two must agree whatever the fixture says. */
    @Test
    @DisplayName("all four breakdowns come to the performance report's net for this delegate")
    void everyBreakdownIsThePerformanceReportsNet() throws Exception {
        DelegateActivity activity = new JdbcDelegateActivityRepository().activity(FROM, TO).stream()
                .filter(candidate -> candidate.employeeId() == delegate).findFirst().orElseThrow();
        BigDecimal reported = activity.sales().subtract(activity.salesReturns());
        assertMoney("860.00", reported);
        for (DelegateBreakdown breakdown : DelegateBreakdown.values()) {
            assertEquals(0, reported.compareTo(SERVICE.breakdown(breakdown, filter()).summary().net()),
                    breakdown.name());
        }
    }

    @Test
    @DisplayName("collections: his, in cash, in the month - not a credit note, not another delegate's, not nobody's")
    void collections() throws Exception {
        var loaded = SERVICE.collections(filter());
        assertEquals(2, loaded.rows().size());
        assertMoney("500.00", loaded.total());
        assertMoney("200.00", loaded.onAccount());
        assertEquals(9001, loaded.rows().get(0).invoiceNumber());
        assertTrue(loaded.rows().get(1).onAccount());
        assertTrue(!loaded.rows().get(0).treasury().isBlank(), "the treasury is named");
    }

    // ---- the month ---------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        delegate = seedDelegate(STAMP + "-D");
        int other = seedDelegate(STAMP + "-E");

        execute("INSERT INTO table_area (area_name) VALUES ('" + STAMP + "-AREA')");
        int area = scalar("SELECT id FROM table_area WHERE area_name = '" + STAMP + "-AREA'");
        customerOne = seedCustomer(STAMP + "-C1", 1);
        customerTwo = seedCustomer(STAMP + "-C2", area);

        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-M')");
        int main = scalar("SELECT id FROM main_group WHERE name_g = '" + STAMP + "-M'");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-G1', " + main + "), ('"
                + STAMP + "-G2', " + main + ")");
        itemOne = seedItem(STAMP + "-I1", STAMP + "-G1");
        itemTwo = seedItem(STAMP + "-I2", STAMP + "-G2");

        // 9001: 190 + 300 on the lines, 40 off the whole invoice -> 450.
        invoice(9001, customerOne, "2026-10-05", "490", "40", delegate);
        line("sales", "num", 9001, itemOne, "2", "100", "200", "10", "1");
        line("sales", "num", 9001, itemTwo, "1", "300", "300", "0", "12");
        // 9002: 500, nothing off.
        invoice(9002, customerTwo, "2026-10-20", "500", "0", delegate);
        line("sales", "num", 9002, itemOne, "5", "100", "500", "0", "1");
        // Somebody else's, and last month's: neither is his October.
        invoice(9003, customerOne, "2026-10-06", "100", "0", other);
        line("sales", "num", 9003, itemOne, "1", "100", "100", "0", "1");
        invoice(9004, customerOne, "2026-09-30", "1000", "0", delegate);
        line("sales", "num", 9004, itemOne, "10", "100", "1000", "0", "1");
        // A return in October: 95 on the line, 5 off the whole return -> 90.
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (9101, " + customerOne
                + ", '2026-10-25', 2, 95, 5, 0, " + delegate + ", '" + STAMP + "')");
        line("sales_re", "item_id", 9101, itemOne, "1", "100", "100", "5", "1");

        collection(customerOne, "2026-10-12", "0", "300", 9001, String.valueOf(delegate));
        collection(customerTwo, "2026-10-13", "0", "200", 0, String.valueOf(delegate));
        collection(customerOne, "2026-10-14", "-50", "0", 0, String.valueOf(delegate));   // a credit note
        collection(customerOne, "2026-10-15", "0", "70", 0, String.valueOf(other));
        collection(customerOne, "2026-10-16", "0", "60", 0, "NULL");
        collection(customerOne, "2026-09-16", "0", "40", 0, String.valueOf(delegate));    // September
    }

    private static int seedDelegate(String name) throws Exception {
        execute("INSERT INTO employees (column_name, job, hire_date, salary, user_id) VALUES ('" + name
                + "', (SELECT MIN(id) FROM jobs WHERE is_delegate = 1), '2025-01-01', 3000, 1)");
        return scalar("SELECT id FROM employees WHERE column_name = '" + name + "'");
    }

    private static int seedCustomer(String name, int area) throws Exception {
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + name + "', 0, 0, 1, " + area + ", 1)");
        return scalar("SELECT id FROM custom WHERE name = '" + name + "'");
    }

    private static int seedItem(String name, String group) throws Exception {
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id) VALUES ('" + name + "', '" + name
                + "', (SELECT id FROM sub_group WHERE name = '" + group + "'), (SELECT MIN(unit_id) FROM units))");
        return scalar("SELECT id FROM items WHERE nameItem = '" + name + "'");
    }

    private static void invoice(int number, int customer, String date, String total, String discount,
                                int delegateId) throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + customer + ", 2, '" + date + "', "
                + total + ", " + discount + ", 0, " + delegateId + ", '" + STAMP + "')");
    }

    private static void line(String table, String itemColumn, int document, int item, String quantity,
                             String price, String gross, String discount, String factor) throws Exception {
        execute("INSERT INTO " + table + " (invoice_number, " + itemColumn + ", type, quantity, price, buy_price,"
                + " total_sel_price, total_buy_price, total_profit, discount, type_value) VALUES (" + document
                + ", " + item + ", (SELECT MIN(unit_id) FROM units), " + quantity + ", " + price + ", 0, " + gross
                + ", 0, 0, " + discount + ", " + factor + ")");
    }

    private static void collection(int customer, String date, String purchase, String paid, int invoice,
                                   String delegateId) throws Exception {
        execute("INSERT INTO customers_accounts (account_code, account_date, purchase, paid, notes, numberInv,"
                + " delegate_id) VALUES (" + customer + ", '" + date + "', " + purchase + ", " + paid + ", '"
                + STAMP + "', " + invoice + ", " + delegateId + ")");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static DelegateDetailFilter filter() {
        return new DelegateDetailFilter(delegate, FROM, TO);
    }

    private static DelegateDetailRow row(List<DelegateDetailRow> rows, int keyId) {
        return rows.stream().filter(row -> row.keyId() == keyId).findFirst().orElseThrow();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " but was " + actual);
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
