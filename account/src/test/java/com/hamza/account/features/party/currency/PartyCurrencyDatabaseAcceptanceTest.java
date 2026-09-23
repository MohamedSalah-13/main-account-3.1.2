package com.hamza.account.features.party.currency;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.ExchangeRateDraft;
import com.hamza.account.features.currency.JdbcCurrencyRepository;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.invoice.InvoicePartyCurrency;
import com.hamza.account.features.invoice.InvoicePaymentTerms;
import com.hamza.account.features.party.ageing.AgeingBucket;
import com.hamza.account.features.party.ageing.PartyAgeingFilter;
import com.hamza.account.features.party.ageing.PartyAgeingPage;
import com.hamza.account.features.party.ageing.PartyAgeingRow;
import com.hamza.account.features.party.ageing.PartyAgeingService;
import com.hamza.account.features.party.balances.BalanceState;
import com.hamza.account.features.party.balances.PartyBalanceFilter;
import com.hamza.account.features.party.balances.PartyBalancePage;
import com.hamza.account.features.party.balances.PartyBalanceRow;
import com.hamza.account.features.party.balances.PartyBalanceService;
import com.hamza.account.features.party.payment.PartyPaymentAllocationService;
import com.hamza.account.features.party.statement.PartyStatementFilter;
import com.hamza.account.features.party.statement.PartyStatementPage;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.features.party.statement.PartyStatementSummary;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.service.AccountCustomerService;
import com.hamza.account.service.AccountSupplierService;
import com.hamza.account.service.CustomerService;
import com.hamza.account.service.SuppliersService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A customer and a supplier in a foreign currency against a real MySQL (V82, docs/currency-plan.md §14) -
 * the only place the claims that need the database exist: that V82 applies to a schema built from nothing
 * and its CHECKs refuse what the rules refuse, that the ledger views carry every figure twice with the base
 * figures unchanged, and that the statement, the balances list, the ageing report, allocation and the
 * credit-limit warning read the party's own figures while every total across parties stays in the base.
 * <p>
 * The case is worked out by hand. Rates for the dollar: 48 from ten days ago, 50 from today. The customer
 * deals in dollars and opens with 100 (4,800 at 48, dated ten days ago). Invoices: 200 dollars five days
 * ago (9,600), 100 three days ago (4,800), and a cash sale of 1,000 today (20 dollars, settled). A return
 * of 960 today naming the first invoice is 20 dollars at <b>its</b> rate, 48. Collections today: 150
 * dollars into the dollar drawer (7,500) and 2,500 pounds into the main treasury (50 dollars), both against
 * the first invoice, which they settle. A debit note of 10 dollars (500). So the customer owes 190 dollars,
 * at a book value of 8,740 - the difference the rates made is shown, and posted nowhere.
 * <p>
 * Gated like the others ({@code -Daccount.db.acceptance=true}); a scratch schema is built from nothing,
 * named uniquely, and dropped. The session is user 9, never user 1: {@code isSystemAdministrator()}
 * bypasses every permission.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PartyCurrencyDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_party_currency_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "PCUR-" + System.nanoTime();
    private static final int MAIN = 1;
    private static final LocalDate TODAY = LocalDate.now();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    private static int usd;
    private static int sar;
    private static int dollarDrawer;
    private static int riyalDrawer;
    private static int dollarCustomer;
    private static int poundCustomer;
    private static int dollarSupplier;

    private static CurrencyService currencies;
    private static AccountCustomerService collections;

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
            UserSessionContext session = new UserSessionContext();
            session.signIn(OPERATOR, "operator", List.of(AppPermissions.CUSTOMER_SHOW,
                    AppPermissions.CUSTOMER_CREATE, AppPermissions.CUSTOMER_UPDATE,
                    AppPermissions.CUSTOMER_ACCOUNT_SHOW, AppPermissions.CUSTOMER_ACCOUNT_CREATE,
                    AppPermissions.CUSTOMER_ACCOUNT_ADJUST, AppPermissions.SUPPLIERS_SHOW,
                    AppPermissions.SUPPLIERS_CREATE, AppPermissions.SUPPLIERS_UPDATE,
                    AppPermissions.SUPPLIERS_ACCOUNT_SHOW, AppPermissions.SUPPLIERS_ACCOUNT_CREATE,
                    AppPermissions.TREASURY_SHOW, AppPermissions.TREASURY_UPDATE, AppPermissions.TREASURY_OPENING,
                    AppPermissions.CURRENCY_SHOW, AppPermissions.CURRENCY_UPDATE,
                    AppPermissions.CURRENCY_RATE_UPDATE));
            ServiceRegistry.register(UserSessionContext.class, session);
            currencies = new CurrencyService();
            collections = new AccountCustomerService(DaoFactory.INSTANCE);
            usd = scalar("SELECT id FROM currency WHERE code = 'USD'");
            sar = scalar("SELECT id FROM currency WHERE code = 'SAR'");
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
    @DisplayName("from nothing: V82's columns, keys and CHECKs, and every party still in the base")
    void theSchema() throws Exception {
        for (String table : List.of("custom", "suppliers")) {
            assertEquals(3, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = '" + table + "' AND COLUMN_NAME IN ('currency_id', 'opening_foreign', 'opening_rate')"));
            assertEquals(0, scalar("SELECT COUNT(*) FROM " + table + " WHERE currency_id IS NOT NULL"),
                    "nothing moved on upgrade");
        }
        for (String table : List.of("total_sales", "total_sales_re", "total_buy", "total_buy_re")) {
            assertEquals(4, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = '" + table + "' AND COLUMN_NAME IN"
                    + " ('exchange_rate', 'total_foreign', 'discount_foreign', 'paid_foreign')"), table);
        }
        // A currency with nothing opened in it, and a movement with a foreign figure and no rate.
        assertSqlRefused(3819, "UPDATE custom SET currency_id = " + usd + " WHERE id = 1");
        assertSqlRefused(3819, "INSERT INTO customers_accounts (account_code, account_date, paid, purchase,"
                + " numberInv, paid_foreign) VALUES (1, CURDATE(), 10, 0, 0, 5)");
        assertSqlRefused(3819, "INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date,"
                + " total, discount, paid_up, stock_id, delegate_id, treasury_id, user_id, exchange_rate)"
                + " VALUES (8999, 1, 1, CURDATE(), 10, 0, 10, 1, 1, 1, 1, 48)");
    }

    @Test
    @Order(2)
    @DisplayName("a dollar customer's opening is valued at its day's rate; the currency and the drawers are set up")
    void theParties() throws Exception {
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY.minusDays(10), new BigDecimal("48"), null));
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY, new BigDecimal("50"), null));

        TreasuryService treasuries = new TreasuryService(DaoFactory.INSTANCE);
        treasuries.insert(drawer("USD", usd));
        treasuries.insert(drawer("SAR", sar));
        dollarDrawer = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-USD'");
        riyalDrawer = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-SAR'");

        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id) VALUES ('"
                + STAMP + "-C', 0, 0, 1, 1, " + OPERATOR + ")");
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id) VALUES ('"
                + STAMP + "-B', 0, 1000, 1, 1, " + OPERATOR + ")");
        execute("INSERT INTO suppliers (name, first_balance, area_id, user_id) VALUES ('"
                + STAMP + "-S', 0, 1, " + OPERATOR + ")");
        dollarCustomer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-C'");
        poundCustomer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-B'");
        dollarSupplier = scalar("SELECT id FROM suppliers WHERE name = '" + STAMP + "-S'");

        // Through the service the party form saves with, as an edit of a party that has not moved.
        CustomerService customers = new CustomerService(DaoFactory.INSTANCE);
        Customers customer = customers.getCustomerById(dollarCustomer);
        customer.setCurrency_id(usd);
        customer.setOpening_foreign(new BigDecimal("100"));
        customer.setOpening_balance_date(TODAY.minusDays(10));
        customer.setCredit_limit(200);
        customers.save(customer);
        assertEquals("4800.00|100.000|48.0000000000|" + usd, text("SELECT CONCAT(first_balance, '|', opening_foreign,"
                + " '|', opening_rate, '|', currency_id) FROM custom WHERE id = " + dollarCustomer));

        SuppliersService suppliers = new SuppliersService(DaoFactory.INSTANCE);
        Suppliers supplier = suppliers.nameDao().getDataById(dollarSupplier);
        supplier.setCurrency_id(usd);
        supplier.setOpening_foreign(BigDecimal.ZERO);
        suppliers.save(supplier);
        assertEquals(1, scalar("SELECT COUNT(*) FROM suppliers WHERE id = " + dollarSupplier + " AND currency_id = "
                + usd + " AND opening_foreign = 0 AND opening_rate IS NULL AND first_balance = 0"),
                "an opening of zero needs no rate");

        assertEquals(2, new JdbcCurrencyRepository().foreignPartyCount());
        assertEquals(2, new JdbcCurrencyRepository().activePartyCount(usd));
    }

    @Test
    @Order(3)
    @DisplayName("invoices carry their day's rate and their dollars; an edit keeps its rate; a cash sale leaves nothing")
    void theInvoices() throws Exception {
        // Since V83 a dollar customer's invoice is typed in dollars (docs/currency-plan.md §15): its
        // figures in dollars are what was typed, and the header names the currency. The base rows are
        // written here by hand; InvoiceSaveService's own conversion is DocumentCurrencyDatabaseAcceptanceTest's.
        InvoicePartyCurrency translator = InvoicePartyCurrency.jdbc();
        insertSale(9001, TODAY.minusDays(5), "9600", "0", 2);
        translator.write(DocumentType.SALES, 9001,
                translator.rateFor(DocumentType.SALES, dollarCustomer, TODAY.minusDays(5), 0, 0, usd),
                typed("200", "0"));
        insertSale(9002, TODAY.minusDays(3), "4800", "0", 2);
        translator.write(DocumentType.SALES, 9002,
                translator.rateFor(DocumentType.SALES, dollarCustomer, TODAY.minusDays(3), 0, 0, usd),
                typed("100", "0"));
        insertSale(9003, TODAY, "1000", "1000", 1);
        translator.write(DocumentType.SALES, 9003,
                translator.rateFor(DocumentType.SALES, dollarCustomer, TODAY, 0, 0, usd), typed("20", "20"));

        assertEquals("48.0000000000|200.000|0.000|0.000", translation("total_sales", "invoice_number", 9001));
        assertEquals("48.0000000000|100.000|0.000|0.000", translation("total_sales", "invoice_number", 9002));
        assertEquals("50.0000000000|20.000|0.000|20.000", translation("total_sales", "invoice_number", 9003));
        assertEquals(usd, scalar("SELECT currency_id FROM total_sales WHERE invoice_number = 9001"));

        // A rate recorded later for the invoice's day does not value it again on an edit.
        int later = currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY.minusDays(5), new BigDecimal("49"), null));
        assertEquals(0, new BigDecimal("48").compareTo(
                translator.rateFor(DocumentType.SALES, dollarCustomer, TODAY.minusDays(5), 9001, 0, usd).rate()));
        assertEquals(0, new BigDecimal("49").compareTo(
                translator.rateFor(DocumentType.SALES, dollarCustomer, TODAY.minusDays(5), 0, 0, usd).rate()));
        currencies.deleteRate(later);

        // No rate on the day is a refusal, a screen in the base is refused for a dollar customer, and a
        // party in the base is not translated at all.
        UserValidationException noRate = assertThrows(UserValidationException.class,
                () -> translator.rateFor(DocumentType.SALES, dollarCustomer, TODAY.minusDays(20), 0, 0, usd));
        assertTrue(noRate.getMessage().contains(String.valueOf(TODAY.minusDays(20))), noRate.getMessage());
        assertThrows(UserValidationException.class,
                () -> translator.rateFor(DocumentType.SALES, dollarCustomer, TODAY, 0, 0));
        assertFalse(translator.rateFor(DocumentType.SALES, poundCustomer, TODAY.minusDays(20), 0, 0).isForeign());
    }

    @Test
    @Order(4)
    @DisplayName("a return naming its invoice is translated at that invoice's rate, not its own day's")
    void theReturn() throws Exception {
        execute("INSERT INTO total_sales_re (id, sup_id, invoice_date, invoice_type, total, discount,"
                + " paid_from_treasury, stock_id, delegate_id, treasury_id, notes, user_id) VALUES (5001, "
                + dollarCustomer + ", CURDATE(), 2, 960, 0, 0, 1, 1, 1, '" + STAMP + "', " + OPERATOR + ")");
        InvoicePartyCurrency translator = InvoicePartyCurrency.jdbc();
        translator.write(DocumentType.SALES_RETURN, 5001,
                translator.rateFor(DocumentType.SALES_RETURN, dollarCustomer, TODAY, 0, 9001, usd),
                typed("20", "0"));
        assertEquals("48.0000000000|20.000|0.000|0.000", translation("total_sales_re", "id", 5001));
    }

    @Test
    @Order(5)
    @DisplayName("collections: in dollars into the dollar drawer, in pounds into the till, and nothing else")
    void theCollections() throws Exception {
        long before = scalar("SELECT COALESCE(MAX(account_num), 0) FROM customers_accounts");
        collections.save(payment(dollarCustomer, 150, 9001, dollarDrawer, TODAY), BigDecimal.ZERO);
        long inDollars = scalar("SELECT MAX(account_num) FROM customers_accounts");
        collections.save(payment(dollarCustomer, 2500, 9001, MAIN, TODAY), BigDecimal.ZERO);
        long inPounds = scalar("SELECT MAX(account_num) FROM customers_accounts");
        assertTrue(inDollars > before && inPounds > inDollars);

        assertEquals("7500.00|150.000|50.0000000000", text("SELECT CONCAT(paid, '|', paid_foreign, '|', exchange_rate)"
                + " FROM customers_accounts WHERE account_num = " + inDollars));
        assertEquals("2500.00|50.000|50.0000000000", text("SELECT CONCAT(paid, '|', paid_foreign, '|', exchange_rate)"
                + " FROM customers_accounts WHERE account_num = " + inPounds));

        // Allocated in dollars: 200 owed, 150 + 50 paid - settled, whatever the rates did.
        PartyPaymentAllocationService allocation = new PartyPaymentAllocationService();
        assertEquals(0, allocation.remainingOn(PartyKind.CUSTOMER, dollarCustomer, 9001, 0).signum());
        assertThrows(UserValidationException.class,
                () -> collections.save(payment(dollarCustomer, 1, 9001, dollarDrawer, TODAY), BigDecimal.ZERO));

        // The drawer's own column reads the dollars, the till's the pounds.
        assertEquals(0, new BigDecimal("150").compareTo(decimal("SELECT income_own FROM treasury_balance"
                + " WHERE source_type = 5 AND id_no = " + inDollars)));
        assertEquals(0, new BigDecimal("2500").compareTo(decimal("SELECT income_own FROM treasury_balance"
                + " WHERE source_type = 5 AND id_no = " + inPounds)));
        assertEquals(0, new BigDecimal("150").compareTo(decimal("SELECT balance_own FROM treasury_current_balance"
                + " WHERE id = " + dollarDrawer)));
        assertEquals(0, new BigDecimal("7500").compareTo(decimal("SELECT balance FROM treasury_current_balance"
                + " WHERE id = " + dollarDrawer)));

        // A third currency, a fee on a foreign drawer, a base party in a foreign drawer, a day with no rate.
        assertThrows(BusinessRuleException.class,
                () -> collections.save(payment(dollarCustomer, 10, 0, riyalDrawer, TODAY), BigDecimal.ZERO));
        assertThrows(BusinessRuleException.class,
                () -> collections.save(payment(dollarCustomer, 10, 0, dollarDrawer, TODAY), new BigDecimal("1")));
        assertThrows(BusinessRuleException.class,
                () -> collections.save(payment(poundCustomer, 10, 0, dollarDrawer, TODAY), BigDecimal.ZERO));
        assertEquals("currency.error.no.rate", assertThrows(UserValidationException.class,
                () -> collections.save(payment(dollarCustomer, 10, 0, MAIN, TODAY.minusDays(20)), BigDecimal.ZERO))
                .getMessage());
        assertEquals(inPounds, scalar("SELECT MAX(account_num) FROM customers_accounts"), "nothing refused was written");
    }

    @Test
    @Order(6)
    @DisplayName("a debit note is typed in dollars and valued at its day's rate")
    void theNote() throws Exception {
        CustomerAccount note = payment(dollarCustomer, 0, 0, MAIN, TODAY);
        note.setPurchase(10);
        collections.save(note, BigDecimal.ZERO);
        assertEquals("500.00|10.000|0.000", text("SELECT CONCAT(purchase, '|', purchase_foreign, '|', paid_foreign)"
                + " FROM customers_accounts WHERE account_num = (SELECT MAX(account_num) FROM customers_accounts)"));
    }

    @Test
    @Order(7)
    @DisplayName("the statement is in dollars: 190 owed, at a book value of 8,740")
    void theStatement() throws Exception {
        PartyStatementService statements = new PartyStatementService();
        assertEquals(0, new BigDecimal("190").compareTo(statements.currentBalanceOwn(PartyKind.CUSTOMER, dollarCustomer)));
        assertEquals(0, new BigDecimal("8740").compareTo(statements.currentBalance(PartyKind.CUSTOMER, dollarCustomer)));

        PartyStatementFilter period = new PartyStatementFilter(PartyKind.CUSTOMER, dollarCustomer,
                TODAY.minusDays(10), TODAY, Set.of(), null, null, null, null, "", false, 0, 100);
        PartyStatementPage page = statements.search(period);
        assertTrue(page.currency().isForeign());
        assertEquals("USD", page.currency().code());

        PartyStatementSummary own = page.summary();
        assertEquals(0, own.openingBalance().signum());
        assertEquals(0, new BigDecimal("430").compareTo(own.totalDebit()), "200 + 100 + 100 + 20 + 10");
        assertEquals(0, new BigDecimal("240").compareTo(own.totalCredit()), "20 + 20 + 150 + 50");
        assertEquals(0, new BigDecimal("190").compareTo(own.closingBalance()));
        assertEquals(0, new BigDecimal("8740").compareTo(page.totals().base().closingBalance()));
        assertEquals(0, new BigDecimal("190").compareTo(page.rows().getFirst().runningBalanceOwn()),
                "the newest row's running balance is the closing balance, in dollars");

        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (var row : page.shownRows()) {
            debit = debit.add(row.debit());
            credit = credit.add(row.credit());
        }
        assertEquals(0, own.totalDebit().compareTo(debit), "the rows add up to the summary, in dollars");
        assertEquals(0, own.totalCredit().compareTo(credit));
    }

    @Test
    @Order(8)
    @DisplayName("the balances list: the row in dollars, the limit held against dollars, the total in the base")
    void theBalances() throws Exception {
        PartyBalanceService balances = new PartyBalanceService();
        PartyBalancePage page = balances.search(filter(false));
        PartyBalanceRow dollars = row(page.rows(), dollarCustomer);
        assertEquals(usd, dollars.currencyId());
        assertEquals(0, new BigDecimal("190").compareTo(dollars.balanceOwn()));
        assertEquals(0, new BigDecimal("8740").compareTo(dollars.balance()));
        assertFalse(dollars.isOverCreditLimit(), "190 dollars under a limit of 200 dollars - 8,740 pounds is not the debt");
        PartyBalanceRow pounds = row(page.rows(), poundCustomer);
        assertNull(pounds.currencyId());
        assertEquals(0, pounds.balance().compareTo(pounds.balanceOwn()));
        assertEquals(0, new BigDecimal("9740").compareTo(page.summary().totalBalance()), "8,740 + 1,000, in the base");

        assertTrue(balances.search(filter(true)).rows().stream().noneMatch(r -> r.partyId() == dollarCustomer),
                "the over-limit filter reads dollars too");

        CustomerReceivable owed = DaoFactory.INSTANCE.customerReceivableDao().getOwedInOwnCurrency().stream()
                .filter(receivable -> receivable.getCustomerId() == dollarCustomer).findFirst().orElseThrow();
        assertEquals(190, owed.getTotalReceivableOwn(), 0.001, "what the credit-limit warning reads");
        assertEquals(8740, owed.getTotalReceivable(), 0.001);
    }

    @Test
    @Order(9)
    @DisplayName("ageing: the row in dollars, the foot in the base at each invoice's own rate")
    void theAgeing() throws Exception {
        PartyAgeingPage page = new PartyAgeingService().search(new PartyAgeingFilter(PartyKind.CUSTOMER, TODAY,
                null, false, true, null, STAMP, 0, 50));
        PartyAgeingRow dollars = page.rows().stream().filter(r -> r.partyId() == dollarCustomer).findFirst()
                .orElseThrow();
        assertTrue(dollars.isForeign());
        assertEquals(0, new BigDecimal("100").compareTo(dollars.amount(AgeingBucket.DAYS_1_30)),
                "the second invoice, three days old, in dollars");
        assertEquals(0, new BigDecimal("90").compareTo(dollars.unallocated()), "the opening 100, the note 10, the return -20");
        assertEquals(0, new BigDecimal("190").compareTo(dollars.balance()));
        assertEquals(0, new BigDecimal("8740").compareTo(dollars.bookBalance()));

        assertEquals(0, new BigDecimal("4800").compareTo(page.summary().amount(AgeingBucket.DAYS_1_30)),
                "100 dollars at the 48 copied onto that invoice");
        assertEquals(0, new BigDecimal("9740").compareTo(page.summary().balance()));
        assertEquals(0, new BigDecimal("4940").compareTo(page.summary().unallocated()),
                "8,740 - 4,800 for the dollar customer and 1,000 for the other");
    }

    @Test
    @Order(10)
    @DisplayName("once the customer has moved, its currency and its opening are fixed")
    void theCurrencyIsFixed() throws Exception {
        CustomerService customers = new CustomerService(DaoFactory.INSTANCE);
        Customers moved = customers.getCustomerById(dollarCustomer);
        moved.setCurrency_id(sar);
        assertThrows(BusinessRuleException.class, () -> customers.save(moved));

        Customers reopened = customers.getCustomerById(dollarCustomer);
        reopened.setOpening_foreign(new BigDecimal("150"));
        assertThrows(BusinessRuleException.class, () -> customers.save(reopened));

        Customers renamed = customers.getCustomerById(dollarCustomer);
        renamed.setTel("0100");
        customers.save(renamed);
        assertEquals("4800.00|100.000|48.0000000000", text("SELECT CONCAT(first_balance, '|', opening_foreign,"
                + " '|', opening_rate) FROM custom WHERE id = " + dollarCustomer), "saving a phone moves no figure");
    }

    @Test
    @Order(11)
    @DisplayName("a payment to a dollar supplier out of the dollar drawer leaves it in dollars")
    void aSupplierPayment() throws Exception {
        SupplierAccount payment = new SupplierAccount(0, TODAY.toString(), 20, STAMP, 0,
                new Suppliers(dollarSupplier), new Treasury(dollarDrawer));
        new AccountSupplierService(DaoFactory.INSTANCE).save(payment, BigDecimal.ZERO);
        assertEquals("1000.00|20.000", text("SELECT CONCAT(paid, '|', paid_foreign) FROM suppliers_accounts"
                + " WHERE account_code = " + dollarSupplier));
        assertEquals(0, new BigDecimal("130").compareTo(decimal("SELECT balance_own FROM treasury_current_balance"
                + " WHERE id = " + dollarDrawer)), "150 in, 20 out");
        assertEquals(0, new BigDecimal("-20").compareTo(new PartyStatementService()
                .currentBalanceOwn(PartyKind.SUPPLIER, dollarSupplier)), "we paid 20 dollars on account");
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private static Treasury drawer(String suffix, int currencyId) {
        Treasury treasury = new Treasury();
        treasury.setName(STAMP + "-" + suffix);
        treasury.setType(TreasuryType.CASH);
        treasury.setActive(true);
        treasury.setCurrencyId(currencyId);
        treasury.setOpeningForeign(BigDecimal.ZERO);
        treasury.setOpeningDate(TODAY.minusDays(10));
        treasury.setUserId(OPERATOR);
        return treasury;
    }

    private static void insertSale(int number, LocalDate day, String total, String paid, int type) throws Exception {
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, stock_id, delegate_id, treasury_id, notes, user_id) VALUES (" + number + ", "
                + dollarCustomer + ", " + type + ", '" + day + "', " + total + ", 0, " + paid + ", 1, 1, "
                + MAIN + ", '" + STAMP + "', " + OPERATOR + ")");
    }

    /** A header as a screen typed it in dollars, with no discount. */
    private static InvoicePaymentTerms typed(String total, String paid) {
        BigDecimal net = new BigDecimal(total);
        BigDecimal cash = new BigDecimal(paid);
        return new InvoicePaymentTerms(cash.compareTo(net) == 0 ? InvoiceType.CASH : InvoiceType.DEFER, net,
                BigDecimal.ZERO, net, cash, net.subtract(cash));
    }

    private static String translation(String table, String key, int number) throws Exception {
        return text("SELECT CONCAT(exchange_rate, '|', total_foreign, '|', discount_foreign, '|', paid_foreign)"
                + " FROM " + table + " WHERE " + key + " = " + number);
    }

    private static CustomerAccount payment(int customer, double paid, int invoice, int treasury, LocalDate day) {
        return new CustomerAccount(0, day.toString(), paid, STAMP, invoice, new Customers(customer),
                new Treasury(treasury));
    }

    private static PartyBalanceFilter filter(boolean overLimitOnly) {
        return new PartyBalanceFilter(PartyKind.CUSTOMER, TODAY, null, BalanceState.ALL, null, null, null, null,
                overLimitOnly, null, STAMP, 0, 50);
    }

    private static PartyBalanceRow row(List<PartyBalanceRow> rows, int partyId) {
        return rows.stream().filter(r -> r.partyId() == partyId).findFirst().orElseThrow();
    }

    private static void assertSqlRefused(int errorCode, String sql) {
        SQLException refused = assertThrows(SQLException.class, () -> execute(sql), sql);
        assertEquals(errorCode, refused.getErrorCode(), sql + " -> " + refused.getMessage());
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
