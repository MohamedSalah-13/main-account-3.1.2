package com.hamza.account.features.party.payment;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
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
 * The payments report's statement against a real MySQL: September 2025's cash on the two ledgers, what
 * the text and the treasury narrow it to, and who entered each movement.
 *
 * <p>Customers: 1,500 from «علي» on the main till, 900 from «سعيد» allocated to invoice 9001 on a second
 * treasury, 200 handed back to «علي», and a debit note of 300 - which moves no cash and is no payment.
 * A collection on the 1st of October is October's. Suppliers: 5,000 paid to one supplier.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}, run with
 * {@code -Daccount.db.acceptance=true} and {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} naming a config pair for
 * the server. <b>The session is never user 1</b>, who bypasses every permission.</p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class PartyPaymentsDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_pp_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "PP-" + System.nanoTime();
    private static final LocalDate FROM = LocalDate.of(2025, 9, 1);
    private static final LocalDate TO = LocalDate.of(2025, 9, 30);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int ali;
    private static int second;
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
    @DisplayName("the period's cash on the customers' ledger, a note left out, with who entered each")
    void theCustomersCash() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        List<PartyPaymentRow> rows = service().payments(PartyPaymentsFilter.of(PartyKind.CUSTOMER, FROM, TO));

        assertEquals(3, rows.size(), "the note moves no cash, and October's is October's");
        PartyPaymentsSummary summary = PartyPaymentsSummary.of(rows);
        assertMoney("2400", summary.received(), "collected");
        assertMoney("200", summary.returned(), "handed back");
        assertMoney("2200", summary.total(), "net");
        assertEquals(STAMP, rows.get(0).userName(), "who entered it");
        assertEquals(9001, rows.get(1).invoiceNumber());
    }

    @Test
    @DisplayName("the text finds a name by part, or a code or an invoice by number; the treasury narrows it")
    void theFilters() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        PartyPaymentsService service = service();

        assertEquals(2, service.payments(new PartyPaymentsFilter(PartyKind.CUSTOMER, FROM, TO, "علي", 0)).size());
        assertEquals(1, service.payments(new PartyPaymentsFilter(PartyKind.CUSTOMER, FROM, TO, "9001", 0)).size(),
                "the invoice a payment was allocated to");
        assertEquals(2, service.payments(new PartyPaymentsFilter(PartyKind.CUSTOMER, FROM, TO,
                String.valueOf(ali), 0)).size(), "the customer's code");
        assertEquals(1, service.payments(new PartyPaymentsFilter(PartyKind.CUSTOMER, FROM, TO, "", second)).size(),
                "the second treasury");
        assertEquals(0, service.payments(new PartyPaymentsFilter(PartyKind.CUSTOMER, FROM, TO, "%", 0)).size(),
                "a typed % is a character, not everything");
    }

    @Test
    @DisplayName("the suppliers' side reads its own ledger, under its own key")
    void theSuppliers() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PURCHASE);
        List<PartyPaymentRow> rows = service().payments(PartyPaymentsFilter.of(PartyKind.SUPPLIER, FROM, TO));

        assertEquals(1, rows.size());
        assertMoney("5000", rows.get(0).paid(), "paid to the supplier");
        assertThrows(BusinessRuleException.class,
                () -> service().payments(PartyPaymentsFilter.of(PartyKind.CUSTOMER, FROM, TO)));
        assertTrue(service().treasuries(PartyKind.SUPPLIER).size() >= 2);
    }

    // ---- September 2025 ---------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        // Names without digits, so a number searched for can match a code or an invoice and nothing else.
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('علي حسن', 0, 0, 1, 1, 1), ('سعيد محمود', 0, 0, 1, 1, 1)");
        ali = scalar("SELECT id FROM custom WHERE name = 'علي حسن'");
        int said = scalar("SELECT id FROM custom WHERE name = 'سعيد محمود'");
        execute("INSERT INTO suppliers (name) VALUES ('مورد الاختبار')");
        int supplier = scalar("SELECT id FROM suppliers WHERE name = 'مورد الاختبار'");
        int main = scalar("SELECT MIN(id) FROM treasury");
        execute("INSERT INTO treasury (t_name, amount) VALUES ('محفظة', 0)");
        second = scalar("SELECT id FROM treasury WHERE t_name = 'محفظة'");

        movement("customers_accounts", ali, "2025-09-02", "1500", "0", main, 0);
        movement("customers_accounts", said, "2025-09-05", "900", "0", second, 9001);
        movement("customers_accounts", ali, "2025-09-09", "-200", "0", main, 0);
        movement("customers_accounts", said, "2025-09-12", "0", "300", main, 0);
        movement("customers_accounts", ali, "2025-10-01", "700", "0", main, 0);
        movement("suppliers_accounts", supplier, "2025-09-15", "5000", "0", main, 0);
    }

    private static void movement(String table, int party, String date, String paid, String purchase, int treasury,
                                 int invoice) throws Exception {
        execute("INSERT INTO " + table + " (account_code, account_date, paid, purchase, notes, treasury_id,"
                + " numberInv, user_id) VALUES (" + party + ", '" + date + "', " + paid + ", " + purchase + ", '"
                + STAMP + "', " + treasury + ", " + invoice + ", " + OPERATOR + ")");
    }

    private static PartyPaymentsService service() {
        return new PartyPaymentsService();
    }

    // ---- helpers -----------------------------------------------------------------------------

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
