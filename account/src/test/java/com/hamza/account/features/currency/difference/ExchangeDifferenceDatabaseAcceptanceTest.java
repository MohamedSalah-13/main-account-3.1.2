package com.hamza.account.features.currency.difference;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.ExchangeRateDraft;
import com.hamza.account.features.profitloss.statement.ComparisonBasis;
import com.hamza.account.features.profitloss.statement.JdbcProfitLossStatementRepository;
import com.hamza.account.features.profitloss.statement.ProfitLossGrouping;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.profitloss.statement.ProfitLossReport;
import com.hamza.account.features.profitloss.statement.ProfitLossReportService;
import com.hamza.account.features.profitloss.statement.StatementLine;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.CashMovementCommand;
import com.hamza.account.features.treasury.TreasuryCashService;
import com.hamza.account.features.treasury.TreasuryExchange;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.features.treasury.TreasuryTransferService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.service.AccountCustomerService;
import com.hamza.account.service.AccountSupplierService;
import com.hamza.account.service.CustomerService;
import com.hamza.account.service.SuppliersService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * The exchange differences against a real MySQL (docs/currency-plan.md §16): the movements are written by the
 * services the screens call - the treasuries, the exchange, the party notes and collections - and the report
 * reads them back off the views every statement reads. The only place these claims exist: that the report
 * walks what the views hold, that its whole difference is the treasuries screen's valuation difference and
 * the party's own balance at the rate less its book value, and that the profit and loss statement shows the
 * report's totals under its net profit without moving the net profit.
 * <p>
 * Worked out by hand. The dollar is 48 from thirty days ago, 50 from ten days ago and 52 from yesterday. The
 * period is the last sixteen days, T-15 to today; the day before it is at 48 and today at 52.
 * <ul>
 *   <li><b>A dollar drawer</b> opens with 100 dollars at T-20 (4,800). At T-12 it buys 100 for 4,900 from the
 *       main treasury - 200 at an average of 48.50 - and at T-5 sells 50 for 2,600: 50 x 48.50 = 2,425 of cost
 *       leaves, 175 realized. It holds 150 at a book value of 7,100; at 52 they are worth 7,800, 525 above their
 *       cost of 7,275. 175 + 525 = 700 = 7,800 - 7,100.</li>
 *   <li><b>A dollar customer</b> is charged 1,000 dollars by a debit note at T-12 (48,000) and pays 20,000 into
 *       the main treasury at T-5 - 400 dollars at 50, which cost 19,200: 800 realized. They owe 600 at a book
 *       value of 28,000; at 52, 31,200 - 2,400 above the 28,800 they cost. In all 3,200.</li>
 *   <li><b>A dollar supplier</b> is owed 500 dollars by a credit note at T-12 (24,000) and paid 10,000 from the
 *       main treasury at T-5 - 200 dollars at 50 that were owed at 48: 400 lost. The shop owes 300 at a book
 *       value of 14,000; at 52 that is 15,600, 1,200 lost. In all -1,600.</li>
 * </ul>
 * So the period realized 575, the unrealized moved by 1,725, and the result is 2,300. Gated like the others
 * ({@code -Daccount.db.acceptance=true}); a scratch schema is built from nothing, named uniquely, and dropped.
 * The session is user 9, never user 1.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExchangeDifferenceDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_exchange_difference_acceptance_";
    private static final int OPERATOR = 9;
    private static final int MAIN = 1;
    private static final String STAMP = "FXD-" + System.nanoTime();
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate FROM = TODAY.minusDays(15);

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    private static int usd;
    private static int dollarDrawer;
    private static int dollarCustomer;
    private static int dollarSupplier;
    private static UserSessionContext session;

    @BeforeAll
    static void migrateAScratchSchemaFromNothing() throws Exception {
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
            execute("INSERT INTO users (id, user_name, user_pass, user_available) VALUES ("
                    + OPERATOR + ", '" + STAMP + "', 'not-a-password', 0)");
            session = new UserSessionContext();
            session.signIn(OPERATOR, "operator", List.of(AppPermissions.CUSTOMER_SHOW,
                    AppPermissions.CUSTOMER_CREATE, AppPermissions.CUSTOMER_UPDATE,
                    AppPermissions.CUSTOMER_ACCOUNT_SHOW, AppPermissions.CUSTOMER_ACCOUNT_CREATE,
                    AppPermissions.CUSTOMER_ACCOUNT_ADJUST, AppPermissions.SUPPLIERS_SHOW,
                    AppPermissions.SUPPLIERS_CREATE, AppPermissions.SUPPLIERS_UPDATE,
                    AppPermissions.SUPPLIERS_ACCOUNT_SHOW, AppPermissions.SUPPLIERS_ACCOUNT_CREATE,
                    AppPermissions.SUPPLIERS_ACCOUNT_ADJUST, AppPermissions.TREASURY_SHOW,
                    AppPermissions.TREASURY_UPDATE, AppPermissions.TREASURY_OPENING, AppPermissions.TREASURY_DEPOSIT,
                    AppPermissions.TREASURY_TRANSFER, AppPermissions.CURRENCY_SHOW, AppPermissions.CURRENCY_UPDATE,
                    AppPermissions.CURRENCY_RATE_UPDATE, AppPermissions.REPORTS_SHOW_PROFIT));
            ServiceRegistry.register(UserSessionContext.class, session);
            usd = scalar("SELECT id FROM currency WHERE code = 'USD'");
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
    @Order(1)
    @DisplayName("a shop with nothing in a foreign currency has no account and no line to draw")
    void nothingForeign() throws Exception {
        ExchangeDifferenceReport report = new ExchangeDifferenceService().report(FROM, TODAY);
        assertTrue(report.rows().isEmpty());
        assertEquals(ExchangeFigures.NONE.applies(), report.summary().figures().applies());
    }

    @Test
    @Order(2)
    @DisplayName("the movements, written by the services the screens call")
    void theMovements() throws Exception {
        CurrencyService currencies = new CurrencyService();
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY.minusDays(30), new BigDecimal("48"), null));
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY.minusDays(10), new BigDecimal("50"), null));
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY.minusDays(1), new BigDecimal("52"), null));

        Treasury drawer = new Treasury();
        drawer.setName(STAMP + "-USD");
        drawer.setType(TreasuryType.CASH);
        drawer.setActive(true);
        drawer.setCurrencyId(usd);
        drawer.setOpeningForeign(new BigDecimal("100"));
        drawer.setOpeningDate(TODAY.minusDays(20));
        drawer.setUserId(OPERATOR);
        new TreasuryService(DaoFactory.INSTANCE).insert(drawer);
        dollarDrawer = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-USD'");

        new TreasuryCashService(DaoFactory.INSTANCE).record(new CashMovementCommand(MAIN, CashDirection.DEPOSIT,
                CashCategory.NORMAL, new BigDecimal("20000"), TODAY.minusDays(20), STAMP, "", OPERATOR));
        TreasuryTransferService transfers = new TreasuryTransferService(DaoFactory.INSTANCE);
        transfers.transfer(new TreasuryTransferCommand(MAIN, dollarDrawer, new BigDecimal("4900"),
                TODAY.minusDays(12), STAMP, OPERATOR, BigDecimal.ZERO, new BigDecimal("100")));
        transfers.transfer(new TreasuryTransferCommand(dollarDrawer, MAIN, new BigDecimal("50"),
                TODAY.minusDays(5), STAMP, OPERATOR, BigDecimal.ZERO, new BigDecimal("2600")));

        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id) VALUES ('"
                + STAMP + "-C', 0, 0, 1, 1, " + OPERATOR + ")");
        execute("INSERT INTO suppliers (name, first_balance, area_id, user_id) VALUES ('"
                + STAMP + "-S', 0, 1, " + OPERATOR + ")");
        dollarCustomer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-C'");
        dollarSupplier = scalar("SELECT id FROM suppliers WHERE name = '" + STAMP + "-S'");
        CustomerService customers = new CustomerService(DaoFactory.INSTANCE);
        Customers customer = customers.getCustomerById(dollarCustomer);
        customer.setCurrency_id(usd);
        customer.setOpening_foreign(BigDecimal.ZERO);
        customers.save(customer);
        SuppliersService suppliers = new SuppliersService(DaoFactory.INSTANCE);
        Suppliers supplier = suppliers.nameDao().getDataById(dollarSupplier);
        supplier.setCurrency_id(usd);
        supplier.setOpening_foreign(BigDecimal.ZERO);
        suppliers.save(supplier);

        AccountCustomerService collections = new AccountCustomerService(DaoFactory.INSTANCE);
        CustomerAccount note = new CustomerAccount(0, TODAY.minusDays(12).toString(), 0, STAMP, 0,
                new Customers(dollarCustomer), new Treasury(MAIN));
        note.setPurchase(1000);
        collections.save(note, BigDecimal.ZERO);
        collections.save(new CustomerAccount(0, TODAY.minusDays(5).toString(), 20000, STAMP, 0,
                new Customers(dollarCustomer), new Treasury(MAIN)), BigDecimal.ZERO);

        AccountSupplierService payments = new AccountSupplierService(DaoFactory.INSTANCE);
        SupplierAccount owed = new SupplierAccount(0, TODAY.minusDays(12).toString(), 0, STAMP, 0,
                new Suppliers(dollarSupplier), new Treasury(MAIN));
        owed.setPurchase(500);
        payments.save(owed, BigDecimal.ZERO);
        payments.save(new SupplierAccount(0, TODAY.minusDays(5).toString(), 10000, STAMP, 0,
                new Suppliers(dollarSupplier), new Treasury(MAIN)), BigDecimal.ZERO);

        assertEquals("48000.00|1000.000,20000.00|400.000", text("SELECT GROUP_CONCAT(CONCAT(purchase + paid, '|',"
                + " COALESCE(purchase_foreign, 0) + COALESCE(paid_foreign, 0)) ORDER BY account_num)"
                + " FROM customers_accounts WHERE account_code = " + dollarCustomer));
    }

    @Test
    @Order(3)
    @DisplayName("each account worked out by hand: realized at the average, unrealized at the last day's rate")
    void theReport() throws Exception {
        ExchangeDifferenceReport report = new ExchangeDifferenceService().report(FROM, TODAY);
        assertEquals(List.of(ExchangeAccountKind.TREASURY, ExchangeAccountKind.CUSTOMER,
                ExchangeAccountKind.SUPPLIER), report.rows().stream().map(row -> row.account().kind()).toList());

        ExchangeDifferenceRow drawer = report.rows().get(0);
        assertEquals(dollarDrawer, drawer.account().id());
        assertMoney("100", drawer.ownStart());
        assertMoney("4800", drawer.bookStart());
        assertMoney("0", drawer.unrealizedStart());
        assertMoney("150", drawer.ownEnd());
        assertMoney("7100", drawer.bookEnd());
        assertMoney("48.5", drawer.averageEnd());
        assertMoney("52", drawer.rateEnd());
        assertMoney("7800", drawer.valueEnd());
        assertMoney("175", drawer.realizedPeriod());
        assertMoney("525", drawer.unrealizedEnd());
        assertMoney("700", drawer.result());
        assertMoney("700", drawer.totalEnd());
        assertEquals(2, drawer.lines().size(), "the exchange in and the exchange out; the opening is brought forward");
        assertMoney("175", drawer.lines().get(1).realized());

        ExchangeDifferenceRow customer = report.rows().get(1);
        assertMoney("600", customer.ownEnd());
        assertMoney("28000", customer.bookEnd());
        assertMoney("800", customer.realizedPeriod());
        assertMoney("2400", customer.unrealizedEnd());
        assertMoney("3200", customer.result());

        ExchangeDifferenceRow supplier = report.rows().get(2);
        assertMoney("300", supplier.ownEnd());
        assertMoney("14000", supplier.bookEnd());
        assertMoney("-400", supplier.realizedPeriod());
        assertMoney("-1200", supplier.unrealizedEnd());
        assertMoney("-1600", supplier.result());

        ExchangeDifferenceSummary summary = report.summary();
        assertMoney("575", summary.realized());
        assertMoney("1725", summary.unrealizedChange());
        assertMoney("2300", summary.result());
        assertMoney("2300", summary.totalEnd());
        assertEquals(0, summary.accountsWithoutRate());
    }

    @Test
    @Order(4)
    @DisplayName("the whole difference is the treasuries screen's valuation difference and each party's balance at the rate")
    void theScreensAgree() throws Exception {
        ExchangeDifferenceReport report = new ExchangeDifferenceService().report(FROM, TODAY);
        TreasuryBalanceSummary drawer = DaoFactory.INSTANCE.treasuryCurrentBalanceDao().getDataById(dollarDrawer);
        BigDecimal rateToday = new CurrencyService().rateOn(usd, TODAY).orElseThrow().rate();
        // TreasuryController.valuationDifference, as the screen computes it.
        BigDecimal valuation = TreasuryExchange.baseOf(drawer.balanceOwn(), rateToday).subtract(drawer.balance());
        assertMoney(valuation.toPlainString(), report.rows().get(0).totalEnd());
        assertMoney(drawer.balanceOwn().toPlainString(), report.rows().get(0).ownEnd());

        ExchangeDifferenceRow customer = report.rows().get(1);
        assertMoney(decimal("SELECT SUM(purchase - discount - paid) FROM account_customer_table WHERE account_code = "
                + dollarCustomer).toPlainString(), customer.bookEnd());
        assertMoney(decimal("SELECT SUM(purchase_own - discount_own - paid_own) FROM account_customer_table"
                + " WHERE account_code = " + dollarCustomer).toPlainString(), customer.ownEnd());
        assertMoney(decimal("SELECT SUM(purchase - discount - paid) FROM account_suppliers_table WHERE account_code = "
                + dollarSupplier).toPlainString(), report.rows().get(2).bookEnd());
    }

    @Test
    @Order(5)
    @DisplayName("what is unrealized one period is realized the next, and two periods add up to their whole")
    void periodsAddUp() throws Exception {
        ExchangeDifferenceService service = new ExchangeDifferenceService();
        ExchangeDifferenceRow first = service.report(FROM, TODAY.minusDays(6)).rows().get(0);
        ExchangeDifferenceRow second = service.report(TODAY.minusDays(5), TODAY).rows().get(0);
        // 200 dollars held at 50 on T-6, bought at an average of 48.50.
        assertMoney("0", first.realizedPeriod());
        assertMoney("300", first.unrealizedEnd());
        assertMoney("300", first.result());
        assertMoney("300", second.unrealizedStart());
        assertMoney("175", second.realizedPeriod());
        assertMoney("400", second.result());
        assertMoney("700", first.result().add(second.result()));
    }

    @Test
    @Order(6)
    @DisplayName("the profit and loss statement shows the report's totals under its net profit, which they do not move")
    void theProfitAndLoss() throws Exception {
        ExchangeDifferenceService exchange = new ExchangeDifferenceService();
        ProfitLossReport report = new ProfitLossReportService((from, to) -> List.of(),
                new JdbcProfitLossStatementRepository(), exchange::figures)
                .report(ProfitLossPeriod.of(FROM, TODAY), ComparisonBasis.PREVIOUS_PERIOD, ProfitLossGrouping.DAY);

        ExchangeFigures figures = report.outside().exchange();
        assertEquals(new ExchangeFigures(3, new BigDecimal("575.00"), new BigDecimal("1725.00"), 0), figures);
        assertMoney("575", line(report, "profitloss.line.exchange.realized").current());
        assertMoney("1725", line(report, "profitloss.line.exchange.unrealized").current());
        assertMoney("0", line(report, "profitloss.line.exchange.realized").previous(),
                "nothing was settled before the period");
        assertMoney("0", report.current().netProfit());
    }

    @Test
    @Order(7)
    @DisplayName("a reader without the profit key is refused before anything is read")
    void theKey() {
        UserSessionContext clerk = new UserSessionContext();
        clerk.signIn(OPERATOR, "clerk", List.of(AppPermissions.CURRENCY_SHOW, AppPermissions.TREASURY_SHOW));
        ServiceRegistry.register(UserSessionContext.class, clerk);
        try {
            assertThrows(BusinessRuleException.class, () -> new ExchangeDifferenceService().report(FROM, TODAY));
        } finally {
            ServiceRegistry.register(UserSessionContext.class, session);
        }
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private static StatementLine line(ProfitLossReport report, String key) {
        return report.statement().stream().filter(line -> key.equals(line.messageKey())).findFirst().orElseThrow();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertMoney(expected, actual, "");
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> message + " expected " + expected + " but was " + actual);
    }

    private static int scalar(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getInt(1);
        }
    }

    private static BigDecimal decimal(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getBigDecimal(1);
        }
    }

    private static String text(String sql) throws Exception {
        try (Connection connection = ConnectionManager.acquire();
             Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getString(1);
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
