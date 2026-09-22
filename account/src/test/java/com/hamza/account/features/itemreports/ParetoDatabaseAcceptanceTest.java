package com.hamza.account.features.itemreports;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.ItemSalesRankDao;
import com.hamza.account.model.domain.ItemSalesRank;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * October, worked out by hand, against a real MySQL - and the claim the two Pareto reports rest on:
 * <b>the items less the invoices' own discounts are what {@code document_profit} says</b> for the same
 * dates, both the net and the profit.
 *
 * <p>A sale on 5 October of ten of rice at 60 (cost 450) and two cartons of twelve juice at 120 with 20
 * off the line (cost 100), 30 off the whole invoice; a sale on 20 October of five of oil at 40 (cost
 * 150); a return on 25 October of one rice (cost 45) with 6 off it; and a September sale that is not in
 * the period. So rice is 9 units, 540 net, 135 margin; juice 24 units, 220 net, 120 margin; oil 5
 * units, 200 net, 50 margin - 960 and 305, less 24 of the invoices' own discounts: 936 and 281.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}. <b>The session is never
 * user 1.</b></p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ParetoDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_pareto_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "PAR-" + System.nanoTime();
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int rice;
    private static int juice;
    private static int oil;
    private static int food;

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
    @DisplayName("each item's base units, net and cost over October, returns taken off")
    void theItemsAreWorkedOutByHand() throws Exception {
        Map<Integer, ItemSalesFact> facts = new JdbcItemSalesRepository().sales(ItemCatalogFilter.EMPTY, FROM, TO)
                .stream().collect(Collectors.toMap(ItemSalesFact::itemId, fact -> fact));

        assertEquals(3, facts.size(), "the September sale is not in the period");
        assertFact(facts.get(rice), 9, 540, 405);
        assertFact(facts.get(juice), 24, 220, 100);
        assertFact(facts.get(oil), 5, 200, 150);
    }

    /** The whole claim: the items less the invoices' discounts are the profit and loss's own view. */
    @Test
    @DisplayName("the items less the invoices' own discounts are document_profit's net and profit")
    void theItemsReconcileToDocumentProfit() throws Exception {
        JdbcItemSalesRepository repository = new JdbcItemSalesRepository();
        List<ItemSalesFact> facts = repository.sales(ItemCatalogFilter.EMPTY, FROM, TO);
        double net = facts.stream().mapToDouble(ItemSalesFact::net).sum();
        double margin = facts.stream().mapToDouble(ItemSalesFact::margin).sum();
        double discounts = repository.headerDiscounts(FROM, TO);

        assertEquals(24, discounts, 0.001);
        BigDecimal[] view = documentProfit();
        assertEquals(936, view[0].doubleValue(), 0.001);
        assertEquals(281, view[1].doubleValue(), 0.001);
        assertEquals(view[0].doubleValue(), net - discounts, 0.001);
        assertEquals(view[1].doubleValue(), margin - discounts, 0.001);
    }

    @Test
    @DisplayName("ranked by the net rice leads; by the margin juice is close behind and oil falls to B")
    void theTwoRankings() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS, AppPermissions.REPORTS_SHOW_PROFIT);
        ItemReportRequest request = new ItemReportRequest(ItemCatalogFilter.EMPTY, FROM, TO);

        ItemReportResult byNet = new ParetoReport(new JdbcItemSalesRepository(), ParetoReport.Basis.NET).run(request);
        ItemReportResult byMargin = new ParetoReport(new JdbcItemSalesRepository(), ParetoReport.Basis.MARGIN).run(request);

        assertEquals(List.of(rice, juice, oil), byNet.rows().stream().map(ItemReportRow::itemId).toList());
        assertEquals(List.of(rice, juice, oil), byMargin.rows().stream().map(ItemReportRow::itemId).toList());
        int classColumn = ParetoReport.MARGIN_COLUMNS.size() - 1;
        assertEquals(com.hamza.controlsfx.language.LanguageManager.getInstance().getString("itemreport.pareto.class.b"),
                byMargin.rows().get(2).value(classColumn), "rice's 44% and juice's 39% came before it");
        assertEquals(UnusedItemsReport.format(281), byMargin.totals().getLast().value());
        assertEquals(UnusedItemsReport.format(936), byNet.totals().getLast().value());
    }

    /**
     * The item movement report reads these same figures. Its old view put rice first with ten - the two
     * cartons of juice counted as two, the returned rice not at all, and every amount before the line's
     * own discount.
     */
    @Test
    @DisplayName("the item movement report: base units net of returns, by the month and by the year")
    void theItemMovementReportReadsTheSameFigures() throws Exception {
        List<ItemSalesRank> october =
                new ItemSalesRankDao().getBestSellersByMonth(2026, 10);
        assertEquals(List.of(juice, rice, oil),
                october.stream().map(ItemSalesRank::getItemId).toList());
        assertRank(october.get(0), 24, 220, 120);
        assertRank(october.get(1), 9, 540, 135);
        assertRank(october.get(2), 5, 200, 50);

        List<ItemSalesRank> year =
                new ItemSalesRankDao().getBestSellersByYear(2026);
        assertEquals(juice, year.getFirst().getItemId());
        assertRank(year.getFirst(), 25, 230, 125);
    }

    @Test
    @DisplayName("narrowed to a group, the group's items alone and no invoice discount")
    void aGroupNarrowsTheItems() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_ITEMS);
        ItemReportResult result = new ParetoReport(new JdbcItemSalesRepository(), ParetoReport.Basis.NET)
                .run(new ItemReportRequest(ItemCatalogFilter.EMPTY.withGroup(food, null), FROM, TO));

        assertEquals(List.of(rice, oil), result.rows().stream().map(ItemReportRow::itemId).toList());
        assertEquals(2, result.totals().size());
    }

    // ---- October -----------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        int customer = seedCustomer(STAMP + "-C");
        int delegate = scalar("SELECT MIN(id) FROM employees");

        food = seedMainGroup(STAMP + "-FOOD");
        int drinks = seedMainGroup(STAMP + "-DRINKS");
        rice = seedItem(STAMP + "-RICE", food, "45");
        juice = seedItem(STAMP + "-JUICE", drinks, "4.1667");
        oil = seedItem(STAMP + "-OIL", food, "30");

        sale(9001, customer, "2026-10-05", "820", "30", delegate);
        line("sales", "num", 9001, rice, "10", "60", "600", "450", "0", "1");
        line("sales", "num", 9001, juice, "2", "120", "240", "100", "20", "12");
        sale(9002, customer, "2026-10-20", "200", "0", delegate);
        line("sales", "num", 9002, oil, "5", "40", "200", "150", "0", "1");
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (9101, " + customer
                + ", '2026-10-25', 2, 60, 6, 0, " + delegate + ", '" + STAMP + "')");
        line("sales_re", "item_id", 9101, rice, "1", "60", "60", "45", "0", "1");
        sale(9003, customer, "2026-09-10", "10", "0", delegate);
        line("sales", "num", 9003, juice, "1", "10", "10", "5", "0", "1");
    }

    private static int seedCustomer(String name) throws Exception {
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + name + "', 0, 0, 1, 1, 1)");
        return scalar("SELECT id FROM custom WHERE name = '" + name + "'");
    }

    private static int seedMainGroup(String name) throws Exception {
        execute("INSERT INTO main_group (name_g) VALUES ('" + name + "')");
        int main = scalar("SELECT id FROM main_group WHERE name_g = '" + name + "'");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + name + "-SUB', " + main + ")");
        return main;
    }

    private static int seedItem(String name, int mainGroup, String buyPrice) throws Exception {
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id, buy_price) VALUES ('" + name + "', '" + name
                + "', (SELECT id FROM sub_group WHERE main_id = " + mainGroup + "), (SELECT MIN(unit_id) FROM units), "
                + buyPrice + ")");
        return scalar("SELECT id FROM items WHERE nameItem = '" + name + "'");
    }

    private static void sale(int number, int party, String date, String total, String discount, int delegate)
            throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + party + ", 2, '" + date + "', "
                + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    private static void line(String table, String itemColumn, int document, int item, String quantity,
                             String price, String gross, String cost, String discount, String factor) throws Exception {
        execute("INSERT INTO " + table + " (invoice_number, " + itemColumn + ", type, quantity, price, buy_price,"
                + " total_sel_price, total_buy_price, total_profit, discount, type_value) VALUES (" + document
                + ", " + item + ", (SELECT MIN(unit_id) FROM units), " + quantity + ", " + price + ", 0, " + gross
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

    // ---- helpers -----------------------------------------------------------------------------

    private static void assertFact(ItemSalesFact fact, double quantity, double net, double cost) {
        assertEquals(quantity, fact.quantity(), 0.001, fact.name() + " units");
        assertEquals(net, fact.net(), 0.001, fact.name() + " net");
        assertEquals(cost, fact.cost(), 0.001, fact.name() + " cost");
    }

    private static void assertRank(ItemSalesRank row, double quantity, double amount,
                                   double profit) {
        assertEquals(quantity, row.getTotalQty(), 0.001, row.getItemName() + " units");
        assertEquals(amount, row.getTotalAmount(), 0.001, row.getItemName() + " amount");
        assertEquals(profit, row.getTotalProfit(), 0.001, row.getItemName() + " profit");
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
