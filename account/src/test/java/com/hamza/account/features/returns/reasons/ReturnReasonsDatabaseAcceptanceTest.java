package com.hamza.account.features.returns.reasons;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * September 2025's returns, worked out by hand, against a real MySQL.
 *
 * <p>Sales: 500, and 1,500 with 100 off - 1,900 net. Returns: two for damage - 200 with 20 off, of two
 * pieces of A at 100; and 100 of a carton of twelve of B whose line carries 10 off - one with no reason
 * (one piece of A, 50), and one for the wrong item dated the 1st of October, which is October's. So damage
 * came to 180 + 100 = 280, nothing given to 50, the month to 330 - 17.37% of its sales - and A came back
 * three pieces worth 250 of lines, B twelve pieces worth 90. On the purchases side a return of 300 for
 * quality against 1,000 bought.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} naming a config pair for
 * the server. <b>The session is never user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ReturnReasonsDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_rr_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "RR-" + System.nanoTime();
    private static final LocalDate FROM = LocalDate.of(2025, 9, 1);
    private static final LocalDate TO = LocalDate.of(2025, 9, 30);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int itemA;
    private static int itemB;

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
    @DisplayName("each reason's returns and their value after their own discounts, and the month's rate")
    void theReasonsByHand() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_RETURNS);
        ReturnReasonsReport report = new ReturnReasonsService().report(ReturnSide.SALES, FROM, TO);

        ReasonTotal damaged = reason(report, "DAMAGED");
        assertEquals(2, damaged.count());
        assertMoney("280", damaged.value(), "damage, net of the 20 off the first");
        assertEquals(1, report.withoutReason().count());
        assertMoney("50", report.withoutReason().value(), "no reason given");
        assertEquals(3, report.count(), "October's return is October's");
        assertMoney("330", report.value(), "the month's returns");
        assertMoney("1900", report.documentsNet(), "the month's sales, net of their discounts");
        assertEquals(Optional.of(new BigDecimal("17.37")), report.returnRate());
        assertEquals("DAMAGED", report.leadingReason().orElseThrow().storedValue());
    }

    @Test
    @DisplayName("the items came back in base units, their lines after their own discounts, most first")
    void theItemsByHand() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_RETURNS);
        List<ReturnedItem> items = new ReturnReasonsService().report(ReturnSide.SALES, FROM, TO).items();

        assertEquals(2, items.size());
        assertEquals(itemA, items.get(0).itemId());
        assertMoney("3", items.get(0).baseQuantity(), "three pieces of A");
        assertMoney("250", items.get(0).value(), "A's lines");
        assertEquals(2, items.get(0).returns());
        assertEquals(itemB, items.get(1).itemId());
        assertMoney("12", items.get(1).baseQuantity(), "a carton of twelve");
        assertMoney("90", items.get(1).value(), "B's line after its own 10 off");
    }

    @Test
    @DisplayName("a reason's row opens its returns, which add up to it")
    void theReturnsUnderAReason() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_RETURNS);
        ReturnReasonsService service = new ReturnReasonsService();
        ReturnReasonsReport report = service.report(ReturnSide.SALES, FROM, TO);

        List<ReturnDocument> damaged = service.documents(ReturnSide.SALES, FROM, TO, reason(report, "DAMAGED"));
        assertEquals(2, damaged.size());
        assertMoney("280", damaged.stream().map(ReturnDocument::value).reduce(BigDecimal.ZERO, BigDecimal::add),
                "they add up to the row");
        assertEquals(9001, damaged.get(0).sourceInvoice());
        assertEquals("customer-" + STAMP, damaged.get(0).party());

        List<ReturnDocument> none = service.documents(ReturnSide.SALES, FROM, TO, report.withoutReason());
        assertEquals(1, none.size());
        assertEquals(0, none.get(0).sourceInvoice(), "a free return names no invoice");
    }

    @Test
    @DisplayName("the purchases side reads its own two tables")
    void thePurchasesSide() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_RETURNS);
        ReturnReasonsReport report = new ReturnReasonsService().report(ReturnSide.PURCHASES, FROM, TO);

        assertEquals(1, report.count());
        assertMoney("300", reason(report, "QUALITY_ISSUE").value(), "quality");
        assertMoney("1000", report.documentsNet(), "bought");
        assertEquals(Optional.of(new BigDecimal("30.00")), report.returnRate());
        assertMoney("300", report.items().get(0).value(), "a purchase line is quantity times price");
    }

    @Test
    @DisplayName("without the returns report's permission nothing is read")
    void thePermission() {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        assertThrows(BusinessRuleException.class,
                () -> new ReturnReasonsService().report(ReturnSide.SALES, FROM, TO));
    }

    // ---- September 2025 ---------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        int delegate = scalar("SELECT MIN(id) FROM employees");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('customer-" + STAMP + "', 0, 0, 1, 1, 1)");
        int customer = scalar("SELECT id FROM custom WHERE name = 'customer-" + STAMP + "'");
        execute("INSERT INTO suppliers (name) VALUES ('supplier-" + STAMP + "')");
        int supplier = scalar("SELECT id FROM suppliers WHERE name = 'supplier-" + STAMP + "'");
        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-M')");
        int main = scalar("SELECT id FROM main_group WHERE name_g = '" + STAMP + "-M'");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-G', " + main + ")");
        itemA = item("A", main);
        itemB = item("B", main);
        int stock = scalar("SELECT MIN(stock_id) FROM stocks");

        sale(9001, customer, "2025-09-02", "500", "0", delegate);
        sale(9002, customer, "2025-09-03", "1500", "100", delegate);
        salesReturn(9101, customer, "2025-09-10", "200", "20", "'DAMAGED'", "9001", delegate);
        returnLine(9101, itemA, "2", "1", "200", "0");
        salesReturn(9102, customer, "2025-09-12", "100", "0", "'DAMAGED'", "9002", delegate);
        returnLine(9102, itemB, "1", "12", "100", "10");
        salesReturn(9103, customer, "2025-09-20", "50", "0", "NULL", "NULL", delegate);
        returnLine(9103, itemA, "1", "1", "50", "0");
        salesReturn(9104, customer, "2025-10-01", "70", "0", "'WRONG_ITEM'", "NULL", delegate);
        returnLine(9104, itemA, "1", "1", "70", "0");

        execute("INSERT INTO total_buy (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, notes) VALUES (9501, " + supplier + ", 2, '2025-09-04', 1000, 0, 0, '" + STAMP + "')");
        execute("INSERT INTO total_buy_re (id, sup_id, invoice_type, invoice_date, total, discount,"
                + " paid_to_treasury, stock_id, notes, return_reason, source_invoice_number) VALUES (9601, "
                + supplier + ", 2, '2025-09-15', 300, 0, 0, " + stock + ", '" + STAMP + "', 'QUALITY_ISSUE', 9501)");
        execute("INSERT INTO purchase_re (invoice_number, item_id, type, quantity, price, discount, type_value)"
                + " VALUES (9601, " + itemA + ", (SELECT MIN(unit_id) FROM units), 3, 100, 0, 1)");
    }

    private static int item(String name, int main) throws Exception {
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id, buy_price) VALUES ('" + STAMP + name
                + "', '" + STAMP + name + "', (SELECT id FROM sub_group WHERE main_id = " + main
                + "), (SELECT MIN(unit_id) FROM units), 5)");
        return scalar("SELECT id FROM items WHERE nameItem = '" + STAMP + name + "'");
    }

    private static void sale(int number, int party, String date, String total, String discount, int delegate)
            throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + party + ", 2, '" + date + "', "
                + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    private static void salesReturn(int number, int party, String date, String total, String discount,
                                    String reason, String source, int delegate) throws Exception {
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes, return_reason, source_invoice_number) VALUES ("
                + number + ", " + party + ", '" + date + "', 2, " + total + ", " + discount + ", 0, " + delegate
                + ", '" + STAMP + "', " + reason + ", " + source + ")");
    }

    private static void returnLine(int document, int item, String quantity, String factor, String gross,
                                   String discount) throws Exception {
        execute("INSERT INTO sales_re (invoice_number, item_id, type, quantity, price, buy_price, total_sel_price,"
                + " total_buy_price, total_profit, discount, type_value) VALUES (" + document + ", " + item
                + ", (SELECT MIN(unit_id) FROM units), " + quantity + ", 0, 0, " + gross + ", 0, 0, " + discount
                + ", " + factor + ")");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static ReasonTotal reason(ReturnReasonsReport report, String stored) {
        return report.reasons().stream().filter(reason -> stored.equals(reason.storedValue())).findFirst()
                .orElseThrow(() -> new AssertionError("no reason " + stored + " in " + report.reasons()));
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
