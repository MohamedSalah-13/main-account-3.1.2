package com.hamza.account.features.party.profile;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.perm.PermAccountAndNameInt;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One customer's October and one supplier's, worked out by hand, against a real MySQL - and the
 * claim the profile rests on: <b>its items less the documents' own discounts are the documents'
 * net, and that net is what the party's ledger says</b>, read here from
 * {@code account_customer_table} itself rather than typed in twice.
 *
 * <p>The month holds what a pinned statement cannot show to be right: one item sold as a carton of
 * twelve and as three pieces on one invoice and then a piece returned, which is fourteen and not
 * three; a discount on a line and on the whole document; a return with its own discount and a cash
 * refund; another customer's invoice; a credit note, which is not a sale; and September, which is
 * what "stopped buying" is measured against.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}; the configured
 * database is a credential carrier and is never opened. <b>The session is never user 1.</b></p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class PartyProfileDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_party_profile_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "PPT-" + System.nanoTime();
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private static final PartyProfileService SERVICE = new PartyProfileService();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int customer;
    private static int supplier;
    private static int juice;
    private static int rice;
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
    @DisplayName("a carton and three pieces less a returned piece is fourteen pieces, not three of anything")
    void quantitiesAreInBaseUnitsNetOfReturns() throws Exception {
        signIn(customerView());
        PartyItemRow item = item(SERVICE.profile(customerOctober()).items(), juice);

        assertMoney("14", item.quantity());
        assertMoney("150.00", item.amount());
        assertMoney("10.00", item.returnedAmount());
        assertEquals(1, item.documents(), "one invoice named it, whatever its lines");
    }

    @Test
    @DisplayName("the items less the documents' own discounts are the documents' net")
    void theItemsReconcileToTheDocuments() throws Exception {
        signIn(customerView());
        PartyProfileSummary summary = SERVICE.profile(customerOctober()).summary();

        assertMoney("235.00", summary.itemsNet());
        assertMoney("9.00", summary.headerDiscount());
        assertMoney("226.00", summary.net());
        assertMoney("0", summary.unexplained());
        assertMoney("91.00", summary.cash());
        assertMoney("135.00", summary.deferred());
        assertEquals(2, summary.documents());
        assertEquals(1, summary.returns());
        assertEquals(Optional.of(LocalDate.of(2026, 10, 20)), summary.lastEver());
    }

    /** Read from the ledger view, not typed in: whatever the fixture says, the two must agree. */
    @Test
    @DisplayName("the net and the cash are what the customer's ledger says for the same documents - and a credit note is in neither")
    void theNetIsTheLedgers() throws Exception {
        signIn(customerView());
        PartyProfileSummary summary = SERVICE.profile(customerOctober()).summary();

        BigDecimal[] ledger = ledger("account_customer_table", customer);
        assertEquals(0, ledger[0].compareTo(summary.net()), "ledger net " + ledger[0]);
        assertEquals(0, ledger[1].compareTo(summary.cash()), "ledger cash " + ledger[1]);
    }

    @Test
    @DisplayName("what was taken in September and not in October has lapsed; what was taken in both has not")
    void lapsedItems() throws Exception {
        signIn(customerView());
        PartyProfile profile = SERVICE.profile(customerOctober());

        assertEquals(List.of(oil), profile.lapsed().stream().map(PartyLapsedItem::itemId).toList());
        assertMoney("4", profile.lapsed().getFirst().previousQuantity());
    }

    @Test
    @DisplayName("October by the week, from Saturday, and by weekday - the empty weeks included")
    void periodsAndWeekdays() throws Exception {
        signIn(customerView());
        PartyProfile profile = SERVICE.profile(customerOctober());

        var months = profile.periods(com.hamza.account.features.party.trend.TrendGranularity.MONTH);
        assertEquals(1, months.size());
        assertMoney("226.00", months.getFirst().net());
        var weeks = profile.periods(com.hamza.account.features.party.trend.TrendGranularity.WEEK);
        assertEquals(LocalDate.of(2026, 9, 26), weeks.getFirst().start(), "the week holding the 1st starts on a Saturday");
        assertMoney("226.00", weeks.stream().map(PartyProfilePeriod::net).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    @DisplayName("a supplier's profile reads the purchases and their returns, in base units")
    void aSuppliersProfile() throws Exception {
        signIn(PermAccountAndNameInt.forParty(PartyKind.SUPPLIER).showNames());
        PartyProfile profile = SERVICE.profile(new PartyProfileFilter(PartyKind.SUPPLIER, supplier, FROM, TO));

        PartyItemRow item = item(profile.items(), juice);
        assertMoney("23", item.quantity());
        assertMoney("170.00", item.net());
        assertMoney("170.00", profile.summary().net());
        assertMoney("0", profile.summary().unexplained());
        BigDecimal[] ledger = ledger("account_suppliers_table", supplier);
        assertEquals(0, ledger[0].compareTo(profile.summary().net()), "ledger net " + ledger[0]);
    }

    @Test
    @DisplayName("an ordinary reader who may open the customer sees the profile, and a file needs the report key")
    void thePermissions() throws Exception {
        signIn(customerView());
        assertTrue(SERVICE.profile(customerOctober()).balance().isEmpty(), "no accounts permission, no balance");
        try {
            SERVICE.forExport(customerOctober());
            throw new AssertionError("a file without the report key");
        } catch (com.hamza.controlsfx.error.BusinessRuleException expected) {
            // the rule this test is for
        }

        signIn(customerView(), AppPermissions.REPORTS_SHOW_CUSTOMERS,
                PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showAccounts());
        PartyProfile exported = SERVICE.forExport(customerOctober());
        assertTrue(exported.balance().isPresent(), "the statement's balance, for a reader who may see accounts");
    }

    // ---- the month ---------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        int delegate = scalar("SELECT MIN(id) FROM employees");
        customer = seedCustomer(STAMP + "-C");
        int other = seedCustomer(STAMP + "-X");
        execute("INSERT INTO suppliers (name) VALUES ('" + STAMP + "-S')");
        supplier = scalar("SELECT id FROM suppliers WHERE name = '" + STAMP + "-S'");

        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-M')");
        int main = scalar("SELECT id FROM main_group WHERE name_g = '" + STAMP + "-M'");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-G', " + main + ")");
        juice = seedItem(STAMP + "-JUICE");
        rice = seedItem(STAMP + "-RICE");
        oil = seedItem(STAMP + "-OIL");

        // 7001, 5 October: a carton of twelve at 120 and three pieces at 10 = 150; 10 off the whole
        // invoice -> 140; 100 paid.
        sale(7001, customer, "2026-10-05", "150", "10", "100", delegate);
        line("sales", "num", 7001, juice, "1", "120", "120", "0", "12");
        line("sales", "num", 7001, juice, "3", "10", "30", "0", "1");
        // 7002, 20 October: two of rice at 50, 5 off the line -> 95, on account.
        sale(7002, customer, "2026-10-20", "95", "0", "0", delegate);
        line("sales", "num", 7002, rice, "2", "50", "100", "5", "1");
        // Another customer's invoice in the same month: not in this profile.
        sale(7003, other, "2026-10-06", "500", "0", "500", delegate);
        line("sales", "num", 7003, juice, "50", "10", "500", "0", "1");
        // September: oil, and rice again - oil lapses in October, rice does not.
        sale(7004, customer, "2026-09-10", "140", "0", "140", delegate);
        line("sales", "num", 7004, oil, "4", "10", "40", "0", "1");
        line("sales", "num", 7004, rice, "2", "50", "100", "0", "1");
        // A return on 25 October: one piece of juice at 10, 1 off the whole return -> 9, refunded.
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (7101, " + customer
                + ", '2026-10-25', 1, 10, 1, 9, " + delegate + ", '" + STAMP + "')");
        line("sales_re", "item_id", 7101, juice, "1", "10", "10", "0", "1");
        // A credit note on the account: it moves the ledger and is no sale.
        execute("INSERT INTO customers_accounts (account_code, account_date, purchase, paid, notes, numberInv)"
                + " VALUES (" + customer + ", '2026-10-15', -50, 0, '" + STAMP + "', 0)");

        // The supplier: two cartons of twelve at 100, 20 off the line -> 180, paid; one piece back.
        execute("INSERT INTO total_buy (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, notes) VALUES (7501, " + supplier + ", 1, '2026-10-08', 180, 0, 180, '" + STAMP + "')");
        execute("INSERT INTO purchase (invoice_number, num, type, quantity, price, discount, type_value)"
                + " VALUES (7501, " + juice + ", (SELECT MIN(unit_id) FROM units), 2, 100, 20, 12)");
        execute("INSERT INTO total_buy_re (id, sup_id, invoice_type, invoice_date, total, discount,"
                + " paid_to_treasury, stock_id, notes) VALUES (7601, " + supplier
                + ", 1, '2026-10-12', 10, 0, 10, (SELECT MIN(stock_id) FROM stocks), '" + STAMP + "')");
        execute("INSERT INTO purchase_re (invoice_number, item_id, type, quantity, price, discount, type_value)"
                + " VALUES (7601, " + juice + ", (SELECT MIN(unit_id) FROM units), 1, 10, 0, 1)");
    }

    private static int seedCustomer(String name) throws Exception {
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + name + "', 0, 0, 1, 1, 1)");
        return scalar("SELECT id FROM custom WHERE name = '" + name + "'");
    }

    private static int seedItem(String name) throws Exception {
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id) VALUES ('" + name + "', '" + name
                + "', (SELECT id FROM sub_group WHERE name = '" + STAMP + "-G'), (SELECT MIN(unit_id) FROM units))");
        return scalar("SELECT id FROM items WHERE nameItem = '" + name + "'");
    }

    private static void sale(int number, int party, String date, String total, String discount, String paid,
                             int delegate) throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + party + ", 2, '" + date + "', "
                + total + ", " + discount + ", " + paid + ", " + delegate + ", '" + STAMP + "')");
    }

    private static void line(String table, String itemColumn, int document, int item, String quantity,
                             String price, String gross, String discount, String factor) throws Exception {
        execute("INSERT INTO " + table + " (invoice_number, " + itemColumn + ", type, quantity, price, buy_price,"
                + " total_sel_price, total_buy_price, total_profit, discount, type_value) VALUES (" + document
                + ", " + item + ", (SELECT MIN(unit_id) FROM units), " + quantity + ", " + price + ", 0, " + gross
                + ", 0, 0, " + discount + ", " + factor + ")");
    }

    /** The ledger's net and cash over the month's documents - invoices (3) and returns (4). */
    private static BigDecimal[] ledger(String view, int party) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(SUM(purchase - discount), 0), COALESCE(SUM(paid), 0) FROM " + view
                             + " WHERE account_code = ? AND account_date BETWEEN ? AND ? AND information IN (3, 4)")) {
            statement.setInt(1, party);
            statement.setObject(2, FROM);
            statement.setObject(3, TO);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return new BigDecimal[]{rows.getBigDecimal(1), rows.getBigDecimal(2)};
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static PartyProfileFilter customerOctober() {
        return new PartyProfileFilter(PartyKind.CUSTOMER, customer, FROM, TO);
    }

    private static PermissionKey customerView() {
        return PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showNames();
    }

    private static PartyItemRow item(List<PartyItemRow> rows, int itemId) {
        return rows.stream().filter(row -> row.itemId() == itemId).findFirst().orElseThrow();
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
