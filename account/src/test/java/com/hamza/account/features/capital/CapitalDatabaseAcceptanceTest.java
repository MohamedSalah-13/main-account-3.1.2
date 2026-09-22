package com.hamza.account.features.capital;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.itemreports.CatalogFact;
import com.hamza.account.features.itemreports.JdbcCatalogFactRepository;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.party.balances.PartyBalanceFilter;
import com.hamza.account.features.party.balances.PartyBalanceService;
import com.hamza.account.features.party.balances.PartyBalanceSummary;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.party.trend.TrendGranularity;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An owner's first quarter, worked out by hand, against a real MySQL - the figures
 * {@code EquityStatementTest} asserts over plain values, here produced by the statements, the
 * views and the profit and loss's own query.
 *
 * <p>Openings of 1,000 in the drawer, 400 owed by a customer, 150 owed to a supplier and five
 * pieces of stock at 50; before the year 3,000 paid in, 500 drawn and a sale earning 1,200. In the
 * quarter 5,000 paid in, 800 drawn from a second treasury, and two sales earning 900. The opening
 * equity is 5,200 and the closing 10,300 - and the profit and loss for the quarter is 900 exactly,
 * which is the proof that neither the 5,000 nor the 800 reached it.</p>
 *
 * <p>The reconciliation (§13) as at the quarter's end, by hand: the treasuries hold 11,177, the
 * customer owes 460 (the 400 brought forward and a debit note of 60), the supplier is owed 150 and two
 * pieces are left at 50 - net assets of 11,587 against equity of 10,300. The 777 ordinary deposit and
 * the 60 note explain 837 of the 1,287 between them; the 450 left is the sales' recorded costs (600)
 * less what the three pieces were worth at the buy price (150), which is exactly the kind of difference
 * the screen says it leaves unexplained.</p>
 *
 * <p>One scratch schema, migrated from empty and dropped in {@code @AfterAll}; the configured
 * database is a credential carrier and is never opened. <b>The session is never user 1.</b></p>
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class CapitalDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_capital_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "CAP-" + System.nanoTime();
    private static final CapitalFilter Q1 = new CapitalFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    private static final EquityStatementService SERVICE = new EquityStatementService();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static int drawer;
    private static int wallet;

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
    @DisplayName("every opening figure is brought forward: 1,000 + 400 - 150 + 5 x 50")
    void broughtForward() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL, AppPermissions.REPORTS_SHOW_PROFIT);
        BroughtForward forward = SERVICE.statement(Q1).broughtForward();

        assertMoney("1000", forward.treasuries());
        assertMoney("400", forward.customers());
        assertMoney("150", forward.suppliers());
        assertMoney("250", forward.stock());
        assertMoney("1500", forward.total());
    }

    @Test
    @DisplayName("the quarter opens on 5,200 and closes on 10,300")
    void theStatement() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL, AppPermissions.REPORTS_SHOW_PROFIT);
        EquityStatement statement = SERVICE.statement(Q1);

        assertMoney("2500", statement.capitalBefore().net());
        assertMoney("1200", statement.profitBefore());
        assertMoney("5200", statement.opening());
        assertMoney("5000", statement.paidIn());
        assertMoney("800", statement.drawn());
        assertMoney("900", statement.profit());
        assertMoney("10300", statement.closing());
        assertEquals(2, statement.movements());
        assertMoney("10300", statement.periods(TrendGranularity.MONTH).getLast().closing());
    }

    /** Read from the profit and loss itself: the owner's 5,000 and 800 are in neither of its figures. */
    @Test
    @DisplayName("the profit on the statement is the profit and loss's, and capital never reached it")
    void theProfitIsTheProfitAndLosss() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL, AppPermissions.REPORTS_SHOW_PROFIT);
        BigDecimal reported = new ProfitLossService(new ProfitLossDao()).load(Q1.from(), Q1.to()).stream()
                .map(ProfitLossRow::netProfit).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertMoney("900", reported);
        assertEquals(0, reported.compareTo(SERVICE.statement(Q1).profit()));
    }

    @Test
    @DisplayName("a row per treasury: paid in through the drawer, drawn from the wallet")
    void byTreasury() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL);
        List<CapitalDay> days = SERVICE.movements(Q1);
        EquityStatement shaped = new EquityStatement(Q1, BroughtForward.NONE,
                new CapitalBefore(BigDecimal.ZERO, BigDecimal.ZERO), BigDecimal.ZERO, days, List.of());

        List<CapitalByTreasuryRow> rows = shaped.byTreasury();
        assertEquals(List.of(drawer, wallet), rows.stream().map(CapitalByTreasuryRow::treasuryId).toList());
        assertMoney("5000", rows.get(0).paidIn());
        assertMoney("800", rows.get(1).drawn());
    }

    @Test
    @DisplayName("the return is the period's profit over the mean of its opening and closing equity")
    void theReturnOnEquity() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL, AppPermissions.REPORTS_SHOW_PROFIT);
        List<EquityPeriod> months = SERVICE.statement(Q1).periods(TrendGranularity.MONTH);

        assertMoney("8000", months.get(0).averageEquity());
        assertMoney("7.50", months.get(0).returnOnEquity().orElseThrow());
        assertMoney("0.00", months.get(1).returnOnEquity().orElseThrow());
        assertMoney("10550", months.get(2).averageEquity());
        assertMoney("2.84", months.get(2).returnOnEquity().orElseThrow());
    }

    @Test
    @DisplayName("the reconciliation, worked by hand: 450 left unexplained, and it is the costs")
    void theReconciliation() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL, AppPermissions.REPORTS_SHOW_PROFIT);
        EquityReconciliation reconciliation = SERVICE.reconciliation(Q1.to());
        ReconciliationFigures figures = reconciliation.figures();

        assertMoney("11177", figures.treasuries());
        assertMoney("460", figures.customersOwe());
        assertMoney("0", figures.customersInCredit());
        assertMoney("150", figures.suppliersOwed());
        assertMoney("0", figures.suppliersInAdvance());
        assertMoney("100", figures.stock());
        assertMoney("60", figures.customersNonCash());
        assertMoney("777", figures.ordinaryCash());
        assertMoney("11587", reconciliation.netAssets());
        assertMoney("10300", reconciliation.equity());
        assertMoney("1287", reconciliation.difference());
        assertMoney("450", reconciliation.unexplained());
    }

    /** Each figure is the one the screen that owns it shows - read from that screen's own code. */
    @Test
    @DisplayName("each figure of the reconciliation is its own screen's")
    void eachFigureIsItsScreens() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL, AppPermissions.REPORTS_SHOW_PROFIT,
                AppPermissions.CUSTOMER_ACCOUNT_SHOW, AppPermissions.SUPPLIERS_ACCOUNT_SHOW);
        ReconciliationFigures figures = SERVICE.reconciliation(Q1.to()).figures();
        PartyBalanceService balances = new PartyBalanceService();
        PartyBalanceSummary customers = balances.search(PartyBalanceFilter.allToday(PartyKind.CUSTOMER)).summary();
        PartyBalanceSummary suppliers = balances.search(PartyBalanceFilter.allToday(PartyKind.SUPPLIER)).summary();
        double stock = new JdbcCatalogFactRepository().facts(ItemCatalogFilter.EMPTY, false).stream()
                .mapToDouble(CatalogFact::valueAtCost).sum();

        assertEquals(0, customers.totalOwed().compareTo(figures.customersOwe()));
        assertEquals(0, customers.totalInCredit().compareTo(figures.customersInCredit()));
        assertEquals(0, suppliers.totalOwed().compareTo(figures.suppliersOwed()));
        assertEquals(0, suppliers.totalInCredit().compareTo(figures.suppliersInAdvance()));
        assertEquals(stock, figures.stock().doubleValue(), 0.001, "the item reports' stock valuation");
        assertEquals(scalar("SELECT ROUND(SUM(balance)) FROM treasury_current_balance"),
                figures.treasuries().intValue());
    }

    @Test
    @DisplayName("the capital alone opens the movements; the statement also needs the profit")
    void thePermissions() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL);
        assertEquals(2, SERVICE.movements(Q1).size());
        assertThrows(BusinessRuleException.class, () -> SERVICE.statement(Q1));
        assertThrows(BusinessRuleException.class, () -> SERVICE.reconciliation(Q1.to()));

        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        assertThrows(BusinessRuleException.class, () -> SERVICE.movements(Q1));
    }

    // ---- the quarter -------------------------------------------------------------------------

    private static void seed() throws Exception {
        execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
        execute("UPDATE treasury SET amount = 0");
        drawer = scalar("SELECT MIN(id) FROM treasury");
        execute("UPDATE treasury SET amount = 1000 WHERE id = " + drawer);
        execute("INSERT INTO treasury (t_name, amount) VALUES ('" + STAMP + "-W', 0)");
        wallet = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-W'");

        execute("UPDATE custom SET first_balance = 0");
        execute("UPDATE suppliers SET first_balance = 0");
        execute("UPDATE items_stock SET first_balance = 0");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id)"
                + " VALUES ('" + STAMP + "-C', 0, 400, 1, 1, 1)");
        int customer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-C'");
        execute("INSERT INTO suppliers (name, first_balance) VALUES ('" + STAMP + "-S', 150)");

        execute("INSERT INTO main_group (name_g) VALUES ('" + STAMP + "-M')");
        int main = scalar("SELECT id FROM main_group WHERE name_g = '" + STAMP + "-M'");
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-G', " + main + ")");
        execute("INSERT INTO items (barcode, nameItem, sub_num, unit_id, buy_price) VALUES ('" + STAMP + "-I', '"
                + STAMP + "-I', (SELECT id FROM sub_group WHERE name = '" + STAMP + "-G'),"
                + " (SELECT MIN(unit_id) FROM units), 50)");
        int item = scalar("SELECT id FROM items WHERE nameItem = '" + STAMP + "-I'");
        execute("INSERT INTO items_stock (item_id, stock_id, first_balance) VALUES (" + item
                + ", (SELECT MIN(stock_id) FROM stocks), 5)");

        // Before the year: 3,000 paid in, 500 drawn, and a sale earning 1,200.
        owner("2025-12-01", drawer, 1, "CAPITAL_IN", "3000");
        owner("2025-12-15", drawer, 2, "OWNER_DRAW", "500");
        sale(8001, customer, "2025-12-20", "1200", "0", item);
        // The quarter: 5,000 paid in, 800 drawn from the wallet, and two sales earning 600 and 300.
        owner("2026-01-10", drawer, 1, "CAPITAL_IN", "5000");
        owner("2026-03-05", wallet, 2, "OWNER_DRAW", "800");
        sale(8002, customer, "2026-01-15", "1000", "400", item);
        sale(8003, customer, "2026-03-20", "500", "200", item);
        // An ordinary deposit is the till's, not the owner's: in no line of the statement.
        owner("2026-02-02", drawer, 1, "NORMAL", "777");
        // A debit note: the customer owes 60 more, and no cash and no profit moved.
        execute("INSERT INTO customers_accounts (account_code, account_date, purchase, paid, notes, numberInv)"
                + " VALUES (" + customer + ", '2026-02-10', 60, 0, '" + STAMP + "', 0)");
    }

    private static void owner(String date, int treasury, int direction, String category, String amount)
            throws Exception {
        execute("INSERT INTO treasury_deposit_expenses (statement, date_inter, amount, description_data,"
                + " deposit_or_expenses, treasury_id, user_id, category) VALUES ('" + STAMP + "', '" + date + "', "
                + amount + ", '" + STAMP + "', " + direction + ", " + treasury + ", 1, '" + category + "')");
    }

    /** A cash sale of one line, whose cost is what the profit and loss subtracts from it. */
    private static void sale(int number, int customer, String date, String total, String cost, int item)
            throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, delegate_id, notes) VALUES (" + number + ", " + customer + ", 1, '" + date + "', "
                + total + ", 0, " + total + ", (SELECT MIN(id) FROM employees), '" + STAMP + "')");
        execute("INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price, total_sel_price,"
                + " total_buy_price, total_profit, discount, type_value) VALUES (" + number + ", " + item
                + ", (SELECT MIN(unit_id) FROM units), 1, " + total + ", " + cost + ", " + total + ", " + cost
                + ", 0, 0, 1)");
    }

    // ---- helpers -----------------------------------------------------------------------------

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
