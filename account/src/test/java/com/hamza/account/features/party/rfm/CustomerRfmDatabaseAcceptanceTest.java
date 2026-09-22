package com.hamza.account.features.party.rfm;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.profile.PartyProfile;
import com.hamza.account.features.party.profile.PartyProfileFilter;
import com.hamza.account.features.party.profile.PartyProfileService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.perm.PermAccountAndNameInt;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Five customers' October, worked out by hand, against a real MySQL - and the claim the table rests
 * on: <b>every row says what the customer's own profile says</b> for the same dates, and the rows add
 * up to what the customer ledger view says for the same documents.
 *
 * <p>The five, with the period ending 31 October:
 * <pre>
 *   A  10 Sep 140; 5 Oct 150-10; 20 Oct 95; returned 25 Oct 10-1   2 invoices, 226, last 20 Oct
 *   B  1 Aug 300                                                     none,       0,   last 1 Aug
 *   C  30 Oct 50                                                     1 invoice,  50,  last 30 Oct
 *   D  2 Oct 1000                                                    1 invoice,  1000, last 2 Oct
 *   E  1 Jun 400; returned 10 Oct 100                                none,      -100, last 1 Jun
 * </pre>
 * Five customers, so each distinct figure is its own fifth: recency E1 B2 D3 A4 C5; frequency B and E
 * share the lowest (1), C and D the middle (3), A 5; value E1 B2 C3 A4 D5. Around them: the customer
 * cash sales land on, with three October invoices of 5000 that would move every score if it were
 * counted; one who bought only in November, after the period, and one who never bought - neither is a
 * row.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}; the configured database
 * is a credential carrier and is never opened. <b>The session is never user 1.</b></p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class CustomerRfmDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_customer_rfm_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "RFM-" + System.nanoTime();
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private static final CustomerRfmService SERVICE = new CustomerRfmService();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static final Map<String, Integer> CUSTOMERS = new HashMap<>();
    private static int cash;

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
    @DisplayName("the five are scored by hand; the cash customer, the November buyer and the non-buyer are not rows")
    void theFiguresAndTheScoresAreTheOnesWorkedOutByHand() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);

        Map<String, CustomerRfmRow> rows = byName(SERVICE.search(october("", CustomerRfmOrder.SCORE, 50)).rows());

        assertEquals(List.of("A", "B", "C", "D", "E"), rows.keySet().stream().sorted().toList());
        assertRow(rows.get("A"), "2026-10-20", 11, 2, "235", "9", "226", "4-5-4");
        assertRow(rows.get("B"), "2026-08-01", 91, 0, "0", "0", "0", "2-1-2");
        assertRow(rows.get("C"), "2026-10-30", 1, 1, "50", "0", "50", "5-3-3");
        assertRow(rows.get("D"), "2026-10-02", 29, 1, "1000", "0", "1000", "3-3-5");
        assertRow(rows.get("E"), "2026-06-01", 152, 0, "0", "100", "-100", "1-1-1");
    }

    @Test
    @DisplayName("the customer left out is named; with none set in the settings, every customer is counted")
    void theCashCustomerIsLeftOutOnlyWhenOneIsNamed() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);
        CustomerRfmFilter nobodyLeftOut = new CustomerRfmFilter(FROM, TO, "", 0, CustomerRfmOrder.SCORE, 0, 50);

        assertEquals(java.util.Optional.of(STAMP + "-CASH"), SERVICE.excludedName(october("", CustomerRfmOrder.SCORE, 50)));
        assertEquals(java.util.Optional.empty(), SERVICE.excludedName(nobodyLeftOut));

        // Six customers now, so a fifth is no longer one rank each: 1 + floor(5 x (rank - 1) / 6) gives
        // ranks 1 to 6 the scores 1, 1, 2, 3, 4, 5 - the bucket takes the top of two figures and
        // pushes everybody else's down, which is why it is left out when the settings name it.
        List<CustomerRfmRow> rows = SERVICE.search(nobodyLeftOut).rows();
        Map<String, CustomerRfmRow> everybody = byName(rows);
        assertEquals(List.of("CASH", "A", "C", "D", "B", "E"), rows.stream().map(CustomerRfmDatabaseAcceptanceTest::short_).toList());
        assertEquals("4-5-5", everybody.get("CASH").scores());
        assertEquals("3-4-3", everybody.get("A").scores(), "4-5-4 without the bucket");
        assertEquals(3, everybody.get("CASH").documents());
    }

    @Test
    @DisplayName("every row is what the customer's own profile says for the same dates")
    void everyRowIsTheCustomersProfile() throws Exception {
        PermissionKey showNames = PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showNames();
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW, showNames);
        PartyProfileService profiles = new PartyProfileService();

        for (CustomerRfmRow row : SERVICE.search(october("", CustomerRfmOrder.SCORE, 50)).rows()) {
            PartyProfile profile = profiles.profile(new PartyProfileFilter(PartyKind.CUSTOMER, row.partyId(), FROM, TO));
            assertEquals(profile.summary().documents(), row.documents(), row.name() + " invoices");
            assertMoney(profile.summary().net(), row.net(), row.name() + " value");
            assertEquals(profile.summary().lastEver().orElseThrow(), row.lastDay(), row.name() + " last purchase");
        }
    }

    @Test
    @DisplayName("the rows add up to what the ledger view says for the same documents, and so does the summary")
    void theRowsAddUpToTheLedger() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);

        CustomerRfmPage page = SERVICE.search(october("", CustomerRfmOrder.SCORE, 50));
        BigDecimal rows = page.rows().stream().map(CustomerRfmRow::net).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertMoney(new BigDecimal("1176"), rows, "226 + 0 + 50 + 1000 - 100");
        assertMoney(ledgerNet(), rows, "the ledger's invoices and returns for the same five");
        assertEquals(new CustomerRfmSummary(5, 3, 4, page.summary().net()), page.summary());
        assertMoney(rows, page.summary().net(), "the summary");
    }

    @Test
    @DisplayName("a search finds one customer and moves nobody's score")
    void aSearchMovesNoScore() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);

        CustomerRfmPage found = SERVICE.search(october(STAMP + "-C", CustomerRfmOrder.SCORE, 50));

        assertEquals(1, found.rows().size());
        assertEquals("5-3-3", found.rows().getFirst().scores(), "scored among all five, not among one");
        assertEquals(1, found.summary().parties());
    }

    @Test
    @DisplayName("the pages list the order asked for, the ties in a fixed order, each customer once")
    void thePagesFollowTheOrderAndShowEachCustomerOnce() throws Exception {
        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);

        assertEquals(List.of("A", "D", "C", "B", "E"), names(CustomerRfmOrder.SCORE),
                "13, then the two elevens by value, then 5 and 3");
        assertEquals(List.of("C", "A", "D", "B", "E"), names(CustomerRfmOrder.RECENCY));
        assertEquals(List.of("D", "A", "C", "B", "E"), names(CustomerRfmOrder.VALUE));

        CustomerRfmPage first = SERVICE.search(october("", CustomerRfmOrder.SCORE, 2));
        CustomerRfmPage second = SERVICE.search(october("", CustomerRfmOrder.SCORE, 2).withPage(1));
        CustomerRfmPage third = SERVICE.search(october("", CustomerRfmOrder.SCORE, 2).withPage(2));
        assertTrue(first.hasNext() && second.hasNext());
        assertFalse(third.hasNext());
        assertEquals(List.of("A", "D", "C", "B", "E"), List.of(short_(first, 0), short_(first, 1),
                short_(second, 0), short_(second, 1), short_(third, 0)));
        assertEquals(5, third.summary().parties(), "the summary is the filter's, not the page's");
    }

    @Test
    @DisplayName("reading asks the balances screen's key; a file asks reports.show.customers on top")
    void theKeysAreTheBalancesScreensAndTheReportsOnTop() throws Exception {
        signIn(PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showNames());
        assertThrows(BusinessRuleException.class, () -> SERVICE.search(october("", CustomerRfmOrder.SCORE, 50)));

        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW);
        assertThrows(BusinessRuleException.class, () -> SERVICE.forExport(october("", CustomerRfmOrder.SCORE, 50)));

        signIn(AppPermissions.CUSTOMER_ACCOUNT_SHOW, AppPermissions.REPORTS_SHOW_CUSTOMERS);
        assertEquals(5, SERVICE.forExport(october("", CustomerRfmOrder.SCORE, 2)).rows().size(),
                "the whole set, whatever the page");
    }

    // ---- the month ---------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        int delegate = scalar("SELECT MIN(id) FROM employees");
        for (String name : List.of("A", "B", "C", "D", "E", "NOVEMBER", "NEVER", "CASH")) {
            CUSTOMERS.put(name, seedCustomer(STAMP + "-" + name));
        }
        cash = CUSTOMERS.get("CASH");
        int number = 8000;

        sale(++number, "A", "2026-09-10", "140", "0", delegate);
        sale(++number, "A", "2026-10-05", "150", "10", delegate);
        sale(++number, "A", "2026-10-20", "95", "0", delegate);
        saleReturn(8101, "A", "2026-10-25", "10", "1", delegate);
        sale(++number, "B", "2026-08-01", "300", "0", delegate);
        sale(++number, "C", "2026-10-30", "50", "0", delegate);
        sale(++number, "D", "2026-10-02", "1000", "0", delegate);
        sale(++number, "E", "2026-06-01", "400", "0", delegate);
        saleReturn(8102, "E", "2026-10-10", "100", "0", delegate);
        sale(++number, "NOVEMBER", "2026-11-05", "700", "0", delegate);
        for (String day : List.of("2026-10-03", "2026-10-14", "2026-10-28")) {
            sale(++number, "CASH", day, "5000", "0", delegate);
        }
        // A credit note on A's account moves the ledger and is no sale - and no return.
        execute("INSERT INTO customers_accounts (account_code, account_date, purchase, paid, notes, numberInv)"
                + " VALUES (" + CUSTOMERS.get("A") + ", '2026-10-15', -50, 0, '" + STAMP + "', 0)");
    }

    private static int seedCustomer(String name) throws Exception {
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + name + "', 0, 0, 1, 1, 1)");
        return scalar("SELECT id FROM custom WHERE name = '" + name + "'");
    }

    private static void sale(int number, String customer, String date, String total, String discount,
                             int delegate) throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + CUSTOMERS.get(customer) + ", 2, '"
                + date + "', " + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    private static void saleReturn(int number, String customer, String date, String total, String discount,
                                   int delegate) throws Exception {
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, delegate_id, notes) VALUES (" + number + ", " + CUSTOMERS.get(customer)
                + ", '" + date + "', 2, " + total + ", " + discount + ", 0, " + delegate + ", '" + STAMP + "')");
    }

    /** The ledger's invoices (3) and returns (4) for the five over the period. */
    private static BigDecimal ledgerNet() throws Exception {
        String five = List.of("A", "B", "C", "D", "E").stream().map(name -> String.valueOf(CUSTOMERS.get(name)))
                .collect(Collectors.joining(", "));
        try (Connection connection = ConnectionManager.acquire();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(SUM(purchase - discount), 0) FROM account_customer_table"
                             + " WHERE account_code IN (" + five + ") AND account_date BETWEEN ? AND ?"
                             + " AND information IN (3, 4)")) {
            statement.setObject(1, FROM);
            statement.setObject(2, TO);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getBigDecimal(1);
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static CustomerRfmFilter october(String text, CustomerRfmOrder order, int pageSize) {
        return new CustomerRfmFilter(FROM, TO, text, cash, order, 0, pageSize);
    }

    private static List<String> names(CustomerRfmOrder order) throws Exception {
        return SERVICE.search(october("", order, 50)).rows().stream().map(CustomerRfmDatabaseAcceptanceTest::short_)
                .toList();
    }

    private static String short_(CustomerRfmPage page, int index) {
        return short_(page.rows().get(index));
    }

    private static String short_(CustomerRfmRow row) {
        return row.name().substring(STAMP.length() + 1);
    }

    private static Map<String, CustomerRfmRow> byName(List<CustomerRfmRow> rows) {
        return rows.stream().collect(Collectors.toMap(CustomerRfmDatabaseAcceptanceTest::short_, row -> row));
    }

    private static void assertRow(CustomerRfmRow row, String last, int recency, int documents, String sold,
                                  String returned, String net, String scores) {
        assertEquals(LocalDate.parse(last), row.lastDay(), row.name() + " last purchase");
        assertEquals(recency, row.recencyDays(), row.name() + " recency");
        assertEquals(documents, row.documents(), row.name() + " invoices");
        assertMoney(new BigDecimal(sold), row.sold(), row.name() + " sold");
        assertMoney(new BigDecimal(returned), row.returned(), row.name() + " returned");
        assertMoney(new BigDecimal(net), row.net(), row.name() + " value");
        assertEquals(scores, row.scores(), row.name() + " scores");
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual, String what) {
        assertEquals(0, expected.compareTo(actual), what + ": expected " + expected + " but was " + actual);
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
