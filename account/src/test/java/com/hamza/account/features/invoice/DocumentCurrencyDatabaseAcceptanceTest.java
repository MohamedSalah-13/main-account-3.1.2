package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.document.TotalsPage;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.document.TotalsSummaryRow;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.ExchangeRateDraft;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.currency.PartyCurrencies;
import com.hamza.account.features.party.statement.MovementBalance;
import com.hamza.account.features.party.statement.PartyMovementKind;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoiceReturn;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.CustomerService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.database.DataSourceProvider;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A document typed in its party's currency against a real MySQL (V83, docs/currency-plan.md §15), through the
 * save the invoice screens call - {@link InvoiceSaveService} built by its public constructor, as
 * {@code CustomData.saveInvoice} builds it. The only place these claims exist: that V83 applies to a schema
 * built from nothing and its CHECKs refuse what the rules refuse, that the line statements carry what was typed
 * beside the base, that the ledger and treasury views read the typed figures as the party's and the drawer's
 * own, that a return picked from a dollar invoice passes every base guard, and that the paper reads it back.
 * <p>
 * The case is worked out by hand. The dollar is at 48.37 today, the dinar at 158.20; nothing is recorded
 * before today. One item costs 40.00 in the base and has 100 on the shelf.
 * <ul>
 *   <li><b>Invoice A</b>, deferred, into the main treasury: 3 at 2.07 dollars less 0.10 off the line, 0.11
 *       off the invoice, 1.00 paid. Typed: 6.11, less 0.11, is 6.00, 5.00 on account. In the base each line
 *       is its typed figure times the rate, rounded: 100.13 a piece (2.07 x 48.37 = 100.1259) and 4.84 off
 *       (4.837), so 300.39 less 4.84 = 295.55; the invoice's 0.11 is 5.32 and its dollar 48.37 - a net of
 *       290.23 with 241.86 on account.</li>
 *   <li><b>Invoice B</b>, cash, into the dollar drawer: 1 at 2.07 - 100.13 in the base, and 2.07 dollars in
 *       the drawer.</li>
 *   <li><b>Return R</b> of A, deferred: 1 of A's line, typed at the line's 2.07 less its share 0.03, and
 *       0.04 of A's discount (0.11 x 2.04 / 6.11). In the base it takes <b>A's</b> figures, never its own
 *       dollars converted: 100.13 less a third of 4.84 (1.61) is 98.52, and A's 5.32 shared the same way is
 *       1.77 - so every base guard holds. The customer then owes 3.00 dollars, at a book value of 145.11.</li>
 *   <li>A dinar customer's invoice of 500 is written in the base and translated, as phase C wrote every
 *       one: a dinar has three places and a document is worked out to two (ق-د٨).</li>
 * </ul>
 * Gated like the others ({@code -Daccount.db.acceptance=true}); a scratch schema is built from nothing,
 * named uniquely, and dropped. The session is user 9, never user 1.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentCurrencyDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_document_currency_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "DCUR-" + System.nanoTime();
    private static final String MAIN_TREASURY = "الخزينة الرئيسية";
    private static final String DELEGATE = "بيع مباشر";
    private static final LocalDate TODAY = LocalDate.now();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;

    private static int usd;
    private static int sar;
    private static int kwd;
    private static int dollarCustomer;
    private static int dinarCustomer;
    private static int dollarDrawer;
    private static int itemId;
    private static int invoiceA;
    private static int invoiceB;
    private static int returnR;

    private static CurrencyService currencies;

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
            session.signIn(OPERATOR, "operator", List.of(AppPermissions.SALES_CREATE, AppPermissions.SALES_RE_CREATE,
                    AppPermissions.CUSTOMER_SHOW, AppPermissions.CUSTOMER_CREATE, AppPermissions.CUSTOMER_UPDATE,
                    AppPermissions.CUSTOMER_ACCOUNT_SHOW, AppPermissions.TREASURY_SHOW,
                    AppPermissions.TREASURY_UPDATE, AppPermissions.TREASURY_OPENING, AppPermissions.CURRENCY_SHOW,
                    AppPermissions.CURRENCY_UPDATE, AppPermissions.CURRENCY_RATE_UPDATE));
            ServiceRegistry.register(UserSessionContext.class, session);
            currencies = new CurrencyService();
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
    @DisplayName("from nothing: V83's columns, keys and CHECKs, and every document still in the base")
    void theSchema() throws Exception {
        for (String table : List.of("total_sales", "total_sales_re", "total_buy", "total_buy_re")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE"
                    + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'"
                    + " AND COLUMN_NAME = 'currency_id' AND REFERENCED_TABLE_NAME = 'currency'"), table);
            assertEquals(0, scalar("SELECT COUNT(*) FROM " + table + " WHERE currency_id IS NOT NULL"),
                    "nothing moved on upgrade");
        }
        for (String table : List.of("sales", "sales_re", "purchase", "purchase_re")) {
            assertEquals(2, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = '" + table + "' AND COLUMN_NAME IN ('price_foreign', 'discount_foreign')"),
                    table);
        }
        for (String view : List.of("sales_names_table", "sales_return_names_table", "purchase_names_table",
                "purchase_return_names_table")) {
            assertEquals(2, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = '" + view + "' AND COLUMN_NAME IN ('price_foreign', 'discount_foreign')"),
                    view);
        }
        // A document naming a currency with no rate.
        assertSqlRefused(3819, "INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date,"
                + " total, discount, paid_up, stock_id, delegate_id, treasury_id, user_id, currency_id)"
                + " VALUES (8999, 1, 1, CURDATE(), 10, 0, 10, 1, 1, 1, 1, " + usd + ")");
    }

    @Test
    @Order(2)
    @DisplayName("the rates, the drawers, the item and a dollar and a dinar customer")
    void theSetUp() throws Exception {
        // V80 seeds the pound, the riyal and the dollar; a dinar is what somebody adds on the currencies screen.
        execute("INSERT INTO currency (code, name, symbol, decimal_places, sort_order) VALUES"
                + " ('KWD', 'دينار كويتي', 'د.ك', 3, 4)");
        kwd = scalar("SELECT id FROM currency WHERE code = 'KWD'");
        currencies.saveRate(new ExchangeRateDraft(0, usd, TODAY, new BigDecimal("48.37"), null));
        currencies.saveRate(new ExchangeRateDraft(0, kwd, TODAY, new BigDecimal("158.20"), null));

        TreasuryService treasuries = new TreasuryService(DaoFactory.INSTANCE);
        treasuries.insert(drawer("USD", usd));
        treasuries.insert(drawer("SAR", sar));
        dollarDrawer = scalar("SELECT id FROM treasury WHERE t_name = '" + STAMP + "-USD'");

        itemId = insertItem();
        // A line with a typed price and no typed discount.
        execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,"
                + " paid_up, stock_id, delegate_id, treasury_id, user_id) VALUES (8998, 1, 1, CURDATE(), 10, 0, 10,"
                + " 1, 1, 1, 1)");
        try {
            assertSqlRefused(3819, "INSERT INTO sales (invoice_number, num, quantity, price, buy_price,"
                    + " price_foreign) VALUES (8998, " + itemId + ", 1, 10, 5, 0.2)");
        } finally {
            execute("DELETE FROM total_sales WHERE invoice_number = 8998");
        }
        dollarCustomer = customer("C-USD", usd);
        dinarCustomer = customer("C-KWD", kwd);
        assertEquals(usd, scalar("SELECT currency_id FROM custom WHERE id = " + dollarCustomer));
    }

    @Test
    @Order(3)
    @DisplayName("a dollar invoice is stored in the base line by line, with what was typed beside each figure")
    void aDollarInvoice() throws Exception {
        long counter = counter();
        invoiceA = sales().save(dollars(sale(InvoiceType.DEFER, MAIN_TREASURY, "0.11", "1.00",
                line(3, "2.07", "0.10")))).invoiceNumber();
        assertEquals(counter + 1, counter());

        assertEquals("295.55|5.32|48.37|" + usd + "|48.3700000000|6.110|0.110|1.000",
                text("SELECT CONCAT(total, '|', discount, '|', paid_up, '|', currency_id, '|', exchange_rate, '|',"
                        + " total_foreign, '|', discount_foreign, '|', paid_foreign) FROM total_sales"
                        + " WHERE invoice_number = " + invoiceA));
        assertEquals("100.13|4.84|3.000|2.070|0.100|40.00",
                text("SELECT CONCAT(price, '|', discount, '|', quantity, '|', price_foreign, '|',"
                        + " discount_foreign, '|', buy_price) FROM sales WHERE invoice_number = " + invoiceA));
        assertEquals("2.070|0.100", text("SELECT CONCAT(price_foreign, '|', discount_foreign)"
                + " FROM sales_names_table WHERE invoice_number = " + invoiceA),
                "the line view carries what was typed");

        // The ledger reads the typed figures as the customer's own, and the base as the book.
        PartyStatementService statements = new PartyStatementService();
        assertEquals(0, new BigDecimal("5.00").compareTo(
                statements.currentBalanceOwn(PartyKind.CUSTOMER, dollarCustomer)));
        assertEquals(0, new BigDecimal("241.86").compareTo(
                statements.currentBalance(PartyKind.CUSTOMER, dollarCustomer)));
        MovementBalance after = statements.balanceAfterMovement(PartyKind.CUSTOMER, dollarCustomer,
                PartyMovementKind.INVOICE, invoiceA);
        assertEquals(0, new BigDecimal("241.86").compareTo(after.base()));
        assertEquals(0, new BigDecimal("5.00").compareTo(after.own()));
        // The main treasury is in the base: the dollar that came in is 48.37 there, in both columns.
        assertEquals(0, new BigDecimal("48.37").compareTo(decimal("SELECT balance FROM treasury_current_balance"
                + " WHERE id = 1")));
        assertEquals(0, new BigDecimal("48.37").compareTo(decimal("SELECT balance_own FROM treasury_current_balance"
                + " WHERE id = 1")));
    }

    @Test
    @Order(4)
    @DisplayName("a cash dollar invoice into the dollar drawer: 2.07 dollars there, 100.13 in the book")
    void aCashInvoiceIntoTheDollarDrawer() throws Exception {
        invoiceB = sales().save(dollars(sale(InvoiceType.CASH, STAMP + "-USD", "0", "2.07",
                line(1, "2.07", "0")))).invoiceNumber();

        assertEquals("100.13|100.13|2.070|2.070", text("SELECT CONCAT(total, '|', paid_up, '|', total_foreign, '|',"
                + " paid_foreign) FROM total_sales WHERE invoice_number = " + invoiceB));
        assertEquals("100.13|2.070", text("SELECT CONCAT(balance, '|', balance_own) FROM treasury_current_balance"
                + " WHERE id = " + dollarDrawer));
        assertEquals(0, new BigDecimal("5.00").compareTo(
                new PartyStatementService().currentBalanceOwn(PartyKind.CUSTOMER, dollarCustomer)),
                "a cash invoice leaves nothing on the account, in either currency");
    }

    @Test
    @Order(5)
    @DisplayName("refused before a number is taken: no rate for the day, a drawer in a third currency, a screen in the base")
    void theRefusals() throws Exception {
        long counter = counter();

        InvoiceValidationException noRate = assertThrows(InvoiceValidationException.class,
                () -> sales().save(dollars(withDate(sale(InvoiceType.DEFER, MAIN_TREASURY, "0", "0",
                        line(1, "2.07", "0")), TODAY.minusDays(1)))));
        assertEquals(InvoiceSaveValidator.Target.DATE, noRate.target());

        InvoiceValidationException riyals = assertThrows(InvoiceValidationException.class,
                () -> sales().save(dollars(sale(InvoiceType.CASH, STAMP + "-SAR", "0", "2.07",
                        line(1, "2.07", "0")))));
        assertEquals(InvoiceSaveValidator.Target.TREASURY, riyals.target());

        InvoiceValidationException inTheBase = assertThrows(InvoiceValidationException.class,
                () -> sales().save(sale(InvoiceType.DEFER, MAIN_TREASURY, "0", "0", line(1, "100", "0"))));
        assertEquals(InvoiceSaveValidator.Target.ACCOUNT, inTheBase.target());

        assertEquals(counter, counter(), "the counter does not roll back, so nothing may take a number first");
    }

    @Test
    @Order(6)
    @DisplayName("a return picked from the dollar invoice takes its base figures, passes every base guard, and its rate")
    void aReturn() throws Exception {
        int sourceLine = scalar("SELECT id FROM sales WHERE invoice_number = " + invoiceA);
        Sales_Return line = new Sales_Return();
        fill(line, 1, "2.07", "0.03");
        line.setSourceLineId(sourceLine);
        InvoiceSaveCommand command = new InvoiceSaveCommand(0, TODAY, InvoiceType.DEFER, new BigDecimal("0.04"),
                DiscountType.AMOUNT, BigDecimal.ZERO, STAMP, dollarCustomer, STAMP + "-C-USD", MAIN_TREASURY,
                DELEGATE, false, invoiceA, null, List.of(line), 1, null, null, usd);

        returnR = returns().save(command).invoiceNumber();

        assertEquals("98.52|1.77|0.00|" + usd + "|48.3700000000|2.040|0.040|0.000",
                text("SELECT CONCAT(total, '|', discount, '|', paid_from_treasury, '|', currency_id, '|',"
                        + " exchange_rate, '|', total_foreign, '|', discount_foreign, '|', paid_foreign)"
                        + " FROM total_sales_re WHERE id = " + returnR));
        assertEquals("100.13|1.61|2.070|0.030|" + sourceLine,
                text("SELECT CONCAT(price, '|', discount, '|', price_foreign, '|', discount_foreign, '|',"
                        + " source_line_id) FROM sales_re WHERE invoice_number = " + returnR));

        PartyStatementService statements = new PartyStatementService();
        assertEquals(0, new BigDecimal("3.00").compareTo(
                statements.currentBalanceOwn(PartyKind.CUSTOMER, dollarCustomer)), "5.00 less 2.00 returned");
        assertEquals(0, new BigDecimal("145.11").compareTo(
                statements.currentBalance(PartyKind.CUSTOMER, dollarCustomer)), "241.86 less 96.75");
    }

    @Test
    @Order(7)
    @DisplayName("a dinar customer's invoice is written in the base and translated to the dinar's three places")
    void aDinarInvoice() throws Exception {
        int dinarInvoice = sales().save(sale(dinarCustomer, InvoiceType.DEFER, MAIN_TREASURY, "0", "0",
                line(1, "500", "0"))).invoiceNumber();

        assertEquals("500.00|NULL|158.2000000000|3.161",
                text("SELECT CONCAT(total, '|', COALESCE(currency_id, 'NULL'), '|', exchange_rate, '|',"
                        + " total_foreign) FROM total_sales WHERE invoice_number = " + dinarInvoice));
        assertNull(text("SELECT price_foreign FROM sales WHERE invoice_number = " + dinarInvoice),
                "nothing was typed in dinars");
    }

    @Test
    @Order(8)
    @DisplayName("the paper reads the dollar invoice back in dollars, with the customer's balance in dollars")
    void thePaper() throws Exception {
        PartyStatementService statements = new PartyStatementService();
        InvoicePrintDocumentBuilder builder = new InvoicePrintDocumentBuilder(
                () -> InvoicePrintDocument.Letterhead.EMPTY,
                (kind, partyId, isReturn, number) -> statements.balanceAfterMovement(kind, partyId,
                        PartyMovementKind.INVOICE, number).base(),
                (kind, partyId, isReturn, number) -> statements.balanceAfterMovement(kind, partyId,
                        PartyMovementKind.INVOICE, number).own(),
                (type, number, partyId) -> InvoicePrintCurrency.read(PartyCurrencies.jdbc(),
                        new InvoicePrintCurrency.Catalogue() {
                            @Override
                            public Currency find(int currencyId) throws com.hamza.controlsfx.database.DaoException {
                                return currencies.find(currencyId);
                            }

                            @Override
                            public Currency base() throws com.hamza.controlsfx.database.DaoException {
                                return currencies.base();
                            }
                        }, type, number, partyId));
        Total_Sales header = DaoFactory.INSTANCE.totalsSalesDao().getDataById(invoiceA);

        InvoicePrintDocument paper = builder.build(DocumentType.SALES, header, "customer", dollarCustomer, DELEGATE,
                0, "", List.of(), "now");

        assertEquals(0, new BigDecimal("6.11").compareTo(paper.total()));
        assertEquals(0, new BigDecimal("0.11").compareTo(paper.discount()));
        assertEquals(0, new BigDecimal("1.00").compareTo(paper.paid()));
        assertEquals(0, new BigDecimal("5.00").compareTo(paper.rest()));
        assertEquals(0, new BigDecimal("5.00").compareTo(paper.balance().after()),
                "the balance on the invoice's own row, in dollars - before the return");
        assertEquals(0, BigDecimal.ZERO.compareTo(paper.balance().before()));
        assertTrue(paper.currency().written());
        assertEquals("USD", paper.currency().figuresIn().code());
        assertEquals(0, new BigDecimal("290.23").compareTo(paper.currency().otherNet()), "the base net");
        assertFalse(paper.lines().iterator().hasNext());
    }

    // --- the save the invoice screens call -------------------------------------------------------------------

    private static InvoiceSaveService<Sales, Total_Sales, Customers, CustomerAccount> sales() {
        return new InvoiceSaveService<>(new SalesInvoice(), repository(DaoFactory.INSTANCE.totalsSalesDao()),
                DocumentType.SALES, name -> new TreasuryService(DaoFactory.INSTANCE).getTreasuryByName(name),
                name -> new Employees(1, name));
    }

    private static InvoiceSaveService<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> returns() {
        return new InvoiceSaveService<>(new SalesInvoiceReturn(),
                repository(DaoFactory.INSTANCE.totalsSalesReturnDao()), DocumentType.SALES_RETURN,
                name -> new TreasuryService(DaoFactory.INSTANCE).getTreasuryByName(name),
                name -> new Employees(1, name));
    }

    /** The one method the save reads of the repository; the rest belong to the list screens. */
    private static <T1 extends BasePurchasesAndSales, T2 extends BaseTotals> TotalsAndPurchaseList<T1, T2> repository(
            DaoList<T2> dao) {
        return new TotalsAndPurchaseList<>() {
            @Override
            public DaoList<T2> totalDao() {
                return dao;
            }

            @Override
            public List<T2> totalList(String dateFrom, String dateTo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<T1> purchaseOrSalesList(int from, int to) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getMaxId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public TotalsPage<T2> searchTotals(TotalsSearchCriteria criteria, int page, int pageSize) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TotalsSummaryRow summarizeTotals(TotalsSearchCriteria criteria) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static InvoiceSaveCommand sale(InvoiceType type, String treasury, String discount, String paid,
                                           Sales line) {
        return sale(dollarCustomer, type, treasury, discount, paid, line);
    }

    private static InvoiceSaveCommand sale(int customer, InvoiceType type, String treasury, String discount,
                                           String paid, Sales line) {
        return new InvoiceSaveCommand(0, TODAY, type, new BigDecimal(discount), DiscountType.AMOUNT,
                new BigDecimal(paid), STAMP, customer, "customer", treasury, DELEGATE, false, 0, null,
                List.of(line), 1, null, null, null);
    }

    /** The screen says its figures are in dollars. */
    private static InvoiceSaveCommand dollars(InvoiceSaveCommand c) {
        return new InvoiceSaveCommand(c.existingInvoiceId(), c.invoiceDate(), c.invoiceType(), c.invoiceDiscount(),
                c.discountType(), c.enteredPaid(), c.notes(), c.partyId(), c.partyName(), c.treasuryName(),
                c.delegateName(), c.allowInsufficientStock(), c.sourceInvoiceNumber(), c.returnReason(), c.lines(),
                c.stockId(), c.correctionReason(), c.expectedUpdatedAt(), usd);
    }

    private static InvoiceSaveCommand withDate(InvoiceSaveCommand c, LocalDate day) {
        return new InvoiceSaveCommand(c.existingInvoiceId(), day, c.invoiceType(), c.invoiceDiscount(),
                c.discountType(), c.enteredPaid(), c.notes(), c.partyId(), c.partyName(), c.treasuryName(),
                c.delegateName(), c.allowInsufficientStock(), c.sourceInvoiceNumber(), c.returnReason(), c.lines(),
                c.stockId(), c.correctionReason(), c.expectedUpdatedAt(), c.documentCurrencyId());
    }

    private static Sales line(double quantity, String price, String discount) {
        Sales line = new Sales();
        fill(line, quantity, price, discount);
        return line;
    }

    private static void fill(BasePurchasesAndSales line, double quantity, String price, String discount) {
        ItemsModel item = new ItemsModel(itemId, STAMP, STAMP);
        item.setBuyPrice(40);
        item.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setQuantity(quantity);
        line.setPrice(Double.parseDouble(price));
        line.setDiscount(Double.parseDouble(discount));
        InvoiceLineService.recalculate(line);
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private static Treasury drawer(String suffix, int currencyId) {
        Treasury treasury = new Treasury();
        treasury.setName(STAMP + "-" + suffix);
        treasury.setType(TreasuryType.CASH);
        treasury.setActive(true);
        treasury.setCurrencyId(currencyId);
        treasury.setOpeningForeign(BigDecimal.ZERO);
        treasury.setOpeningDate(TODAY);
        treasury.setUserId(OPERATOR);
        return treasury;
    }

    private static int customer(String suffix, int currencyId) throws Exception {
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id) VALUES ('"
                + STAMP + "-" + suffix + "', 0, 0, 1, 1, " + OPERATOR + ")");
        int id = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-" + suffix + "'");
        CustomerService customers = new CustomerService(DaoFactory.INSTANCE);
        Customers customer = customers.getCustomerById(id);
        customer.setCurrency_id(currencyId);
        customer.setOpening_foreign(BigDecimal.ZERO);
        customers.save(customer);
        return id;
    }

    private static int insertItem() throws Exception {
        Connection connection = ConnectionManager.acquire();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items(barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2, sel_price3,"
                        + " unit_id, mini_quantity, user_id) VALUES (?, ?, 1, 40, 100, 100, 100, 1, 0, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, STAMP);
            statement.setString(2, STAMP);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                try (Statement stock = connection.createStatement()) {
                    stock.executeUpdate("UPDATE items_stock SET first_balance = 100 WHERE item_id = " + id
                            + " AND stock_id = 1");
                    if (stock.getUpdateCount() == 0) {
                        stock.executeUpdate("INSERT INTO items_stock (item_id, stock_id, first_balance) VALUES ("
                                + id + ", 1, 100)");
                    }
                }
                return id;
            }
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private static long counter() throws Exception {
        return scalar("SELECT current_value FROM document_sequences WHERE document_type = 'SALES'");
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
