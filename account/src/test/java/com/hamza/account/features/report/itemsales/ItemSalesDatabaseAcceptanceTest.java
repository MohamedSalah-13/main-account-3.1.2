package com.hamza.account.features.report.itemsales;

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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * October's item sales, worked out by hand against a real MySQL - and held to {@code document_profit}, the
 * profit and loss's own view: the items' net less the invoices' own discounts is its net revenue, the items'
 * margin less the same discounts its profit.
 *
 * <p>A sale on 5 October of ten rice at 60 (cost 450) and two cartons of twelve juice at 120 with 20 off the
 * line (cost 100), 30 off the whole invoice; a sale on 20 October of five oil at 40 (cost 150); a sale on
 * 22 October of three single juice at 12 (cost 12.50); a return on 25 October of one rice (cost 45) with 6
 * off the whole return; and a September sale of juice that is not in the period. So rice is 10 sold and 1
 * back, 540 net, 135 margin; juice 27 pieces on two invoices, 256 net, 143.50 margin; oil 5, 200, 50 -
 * 996 and 328.50, less 24 of the invoices' own discounts: 972 and 304.50. The same Pareto's story with a
 * second unit of juice, so an item's lines come in two prices.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG}. <b>The session is never
 * user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ItemSalesDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_is_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "IS-" + System.nanoTime();
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final ItemSalesFilter OCTOBER = ItemSalesFilter.of(FROM, TO);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int rice;
    private static int juice;
    private static int oil;

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
    @DisplayName("each item in base units, returns taken off, the most sold by value first")
    void theItemsAreWorkedOutByHand() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS, AppPermissions.REPORTS_SHOW_PROFIT);
        ItemSalesReport report = new ItemSalesService().report(OCTOBER);

        assertEquals(List.of(rice, juice, oil), report.rows().stream().map(ItemSalesRow::itemId).toList(),
                "juice's 27 pieces are worth less than rice's 9; the September juice is not in the period");
        ItemSalesRow riceRow = report.rows().get(0);
        assertMoney("10", riceRow.soldQuantity(), "rice sold");
        assertMoney("1", riceRow.returnedQuantity(), "rice back");
        assertEquals(1, riceRow.invoices());
        assertMoney("600", riceRow.sold(), "rice sold for");
        assertMoney("60", riceRow.returned(), "rice refunded");
        assertMoney("135", riceRow.margin().orElseThrow(), "rice's margin");

        ItemSalesRow juiceRow = report.rows().get(1);
        assertMoney("27", juiceRow.netQuantity(), "two cartons of twelve and three pieces");
        assertEquals(2, juiceRow.invoices());
        assertMoney("256", juiceRow.net(), "220 after the line's discount and 36");
        assertMoney("143.5", juiceRow.margin().orElseThrow(), "juice's margin");

        assertMoney("996", report.net(), "the items' net");
        assertMoney("328.5", report.margin().orElseThrow(), "the items' margin");
        assertMoney("24", report.headerDiscounts().orElseThrow(), "30 off the sale less 6 off the return");
    }

    /** The whole claim: the items and the invoices' own discounts add up to the profit and loss's view. */
    @Test
    @DisplayName("the items less the invoices' own discounts are document_profit's net and profit")
    void theItemsReconcileToDocumentProfit() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS, AppPermissions.REPORTS_SHOW_PROFIT);
        ItemSalesReport report = new ItemSalesService().report(OCTOBER);
        BigDecimal[] view = documentProfit();

        assertMoney("972", view[0], "the view's net revenue");
        assertMoney("304.5", view[1], "the view's profit");
        assertMoney(view[0].toPlainString(), report.invoicesNet().orElseThrow(), "the invoices' net");
        assertMoney(view[1].toPlainString(), report.margin().orElseThrow().subtract(report.headerDiscounts().orElseThrow()),
                "the margin less the invoices' discounts");
    }

    @Test
    @DisplayName("a row's lines come by unit and price, and add up to the row in base units and in money")
    void theLinesExplainTheRow() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        ItemSalesService service = new ItemSalesService();
        ItemSalesReport report = service.report(OCTOBER);

        for (ItemSalesRow row : report.rows()) {
            List<ItemSalesLine> lines = service.lines(OCTOBER, row.itemId());
            BigDecimal net = lines.stream().map(line -> line.isReturn() ? line.amount().negate() : line.amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal units = lines.stream().map(line -> line.isReturn() ? line.baseQuantity().negate()
                    : line.baseQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertMoney(row.net().toPlainString(), net, row.name() + "'s lines");
            assertMoney(row.netQuantity().toPlainString(), units, row.name() + "'s base units");
        }

        List<ItemSalesLine> juiceLines = service.lines(OCTOBER, juice);
        assertEquals(2, juiceLines.size(), "a carton and a piece are two prices");
        ItemSalesLine cartons = juiceLines.getFirst();
        assertEquals(STAMP + "-CARTON", cartons.unitName(), "the larger unit first");
        assertMoney("12", cartons.factor(), "twelve to a carton");
        assertMoney("2", cartons.quantity(), "two cartons as written");
        assertMoney("20", cartons.discount(), "the line's own discount");
        assertMoney("220", cartons.amount(), "after it");
        List<ItemSalesLine> riceLines = service.lines(OCTOBER, rice);
        assertTrue(riceLines.getLast().isReturn(), "the returns after the sales");
    }

    @Test
    @DisplayName("without the profit key no cost is read; without the items' key nothing is")
    void thePermissions() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        ItemSalesReport report = new ItemSalesService().report(OCTOBER);
        assertFalse(report.marginVisible());
        report.rows().forEach(row -> assertNull(row.cost(), row.name()));

        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        assertThrows(BusinessRuleException.class, () -> new ItemSalesService().report(OCTOBER));
        assertThrows(BusinessRuleException.class, () -> new ItemSalesService().lines(OCTOBER, rice));
    }

    @Test
    @DisplayName("a search is the items list's own, and leaves the invoices' discounts out")
    void aSearchNarrowsTheItems() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        ItemSalesReport report = new ItemSalesService().report(new ItemSalesFilter(FROM, TO, STAMP + "-RICE"));

        assertEquals(List.of(rice), report.rows().stream().map(ItemSalesRow::itemId).toList());
        assertTrue(report.headerDiscounts().isEmpty());
        assertTrue(report.invoicesNet().isEmpty());
    }

    // ---- October ------------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + STAMP + "-C', 0, 0, 1, 1, 1)");
        int customer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-C'");
        int delegate = scalar("SELECT MIN(id) FROM employees");
        int piece = scalar("SELECT MIN(unit_id) FROM units");
        execute("INSERT INTO units (unit_name) VALUES ('" + STAMP + "-CARTON')");
        int carton = scalar("SELECT unit_id FROM units WHERE unit_name = '" + STAMP + "-CARTON'");

        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-G')");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-SUB', (SELECT id FROM main_group"
                + " WHERE name_g = '" + STAMP + "-G'))");
        rice = seedItem(STAMP + "-RICE", "45");
        juice = seedItem(STAMP + "-JUICE", "4.1667");
        oil = seedItem(STAMP + "-OIL", "30");

        sale(9001, customer, "2026-10-05", "820", "30", delegate);
        line("sales", "num", 9001, rice, piece, "10", "60", "600", "450", "0", "1");
        line("sales", "num", 9001, juice, carton, "2", "120", "240", "100", "20", "12");
        sale(9002, customer, "2026-10-20", "200", "0", delegate);
        line("sales", "num", 9002, oil, piece, "5", "40", "200", "150", "0", "1");
        sale(9004, customer, "2026-10-22", "36", "0", delegate);
        line("sales", "num", 9004, juice, piece, "3", "12", "36", "12.5", "0", "1");
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (9101, " + customer
                + ", '2026-10-25', 2, 60, 6, 0, " + delegate + ", '" + STAMP + "')");
        line("sales_re", "item_id", 9101, rice, piece, "1", "60", "60", "45", "0", "1");
        sale(9003, customer, "2026-09-10", "10", "0", delegate);
        line("sales", "num", 9003, juice, piece, "1", "10", "10", "5", "0", "1");
    }

    private static int seedItem(String name, String buyPrice) throws Exception {
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id, buy_price) VALUES ('" + name + "', '" + name
                + "', (SELECT id FROM sub_group WHERE name = '" + STAMP + "-SUB'), (SELECT MIN(unit_id) FROM units), "
                + buyPrice + ")");
        return scalar("SELECT id FROM items WHERE nameItem = '" + name + "'");
    }

    private static void sale(int number, int party, String date, String total, String discount, int delegate)
            throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + party + ", 2, '" + date + "', "
                + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    private static void line(String table, String itemColumn, int document, int item, int unit, String quantity,
                             String price, String gross, String cost, String discount, String factor) throws Exception {
        execute("INSERT INTO " + table + " (invoice_number, " + itemColumn + ", type, quantity, price, buy_price,"
                + " total_sel_price, total_buy_price, total_profit, discount, type_value) VALUES (" + document
                + ", " + item + ", " + unit + ", " + quantity + ", " + price + ", 0, " + gross
                + ", " + cost + ", 0, " + discount + ", " + factor + ")");
    }

    /** The profit and loss's own view over October: its net revenue and its profit. */
    private static BigDecimal[] documentProfit() throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(SUM(net_revenue), 0), COALESCE(SUM(profit), 0) FROM document_profit"
                             + " WHERE document_date BETWEEN ? AND ?")) {
            statement.setObject(1, FROM);
            statement.setObject(2, TO);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return new BigDecimal[]{rows.getBigDecimal(1), rows.getBigDecimal(2)};
            }
        }
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
