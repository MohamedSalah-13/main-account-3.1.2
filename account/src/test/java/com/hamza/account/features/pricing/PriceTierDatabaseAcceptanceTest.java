package com.hamza.account.features.pricing;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.document.TotalsPage;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.document.TotalsSummaryRow;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceItemSelectionService;
import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.features.invoice.InvoicePriceTier;
import com.hamza.account.features.invoice.InvoiceSaveCommand;
import com.hamza.account.features.invoice.InvoiceSaveService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoList;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The price tiers against a real MySQL (V84, docs/pricing-and-offers-plan.md phase A), through the save the
 * invoice screens call - {@link InvoiceSaveService} built by its public constructor, as
 * {@code CustomData.saveInvoice} builds it - and the line the entry form builds, through
 * {@link InvoiceItemSelectionService} over the real item. The only place these claims exist: that V84
 * applies to a schema built from nothing and its trigger and CHECK refuse what the rules refuse; that a
 * wholesale customer's line of an item with no wholesale price is sold at tier 1 and stored with its tier
 * and its list price; that pricing at another tier and selling under the list each need their permission,
 * refused before the number is taken; that the tier stays with the document once its customer has moved;
 * and that the reports and the fill read and write what the rules say.
 * <p>
 * The case: an item costing 40, at 100 retail (tier 1), 95 trade (tier 2) and no wholesale price (tier 3);
 * a customer on the wholesale tier. The session is user 9, never user 1 - who bypasses every permission.
 * Gated like the others ({@code -Daccount.db.acceptance=true}); the scratch schema is named uniquely and
 * dropped.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PriceTierDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_price_tier_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "TIER-" + System.nanoTime();
    private static final String MAIN_TREASURY = "الخزينة الرئيسية";
    private static final String DELEGATE = "بيع مباشر";
    private static final LocalDate TODAY = LocalDate.now();

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static UserSessionContext session;

    private static int itemId;
    private static int wholesaleCustomer;
    private static int invoiceA;

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
            ServiceRegistry.register(UserSessionContext.class, session);
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

    /** Signs user 9 in with exactly these keys - never user 1, who is let through everything. */
    private static void signIn(PermissionKey... keys) {
        session.signIn(OPERATOR, "operator", Set.of(keys));
    }

    @Test
    @Order(1)
    @DisplayName("from nothing: V84's columns, keys, CHECK, trigger and grants")
    void theSchema() throws Exception {
        assertEquals(5, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = 'type_price' AND COLUMN_NAME IN ('is_active', 'rule_source', 'rule_tier_id',"
                + " 'rule_percent', 'rule_rounding')"));
        for (String table : List.of("total_sales", "total_sales_re")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE"
                    + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'"
                    + " AND COLUMN_NAME = 'price_tier_id' AND REFERENCED_TABLE_NAME = 'type_price'"), table);
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = 'sales_names_table' AND COLUMN_NAME = 'list_price'"), "the line view reads it");
        assertEquals(3, scalar("SELECT COUNT(*) FROM type_price WHERE is_active = 1"), "every tier on, as before");

        // Tier 1 stays on (the trigger), and no tier fills itself.
        assertSqlRefused(1644, "UPDATE type_price SET is_active = 0 WHERE id = 1");
        assertSqlRefused(1644, "UPDATE type_price SET rule_source = 'TIER', rule_tier_id = 2, rule_percent = 1,"
                + " rule_rounding = 1 WHERE id = 2");
        // A rule missing its rounding - the CHECK, with IS NOT NULL written in each branch.
        assertSqlRefused(3819, "UPDATE type_price SET rule_source = 'COST', rule_percent = 5 WHERE id = 2");

        // Whoever may create a sale may sell under the list; nobody may change a tier.
        assertEquals(scalar("SELECT COUNT(*) FROM auth_role_permission rp JOIN auth_permission p"
                        + " ON p.id = rp.permission_id WHERE p.permission_key = 'sales.create'"),
                scalar("SELECT COUNT(*) FROM auth_role_permission rp JOIN auth_permission p"
                        + " ON p.id = rp.permission_id WHERE p.permission_key = 'sales.price.below.list'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM auth_role_permission rp JOIN auth_permission p"
                + " ON p.id = rp.permission_id WHERE p.permission_key = 'sales.price.tier.change'"));
    }

    @Test
    @Order(2)
    @DisplayName("the tiers renamed and a rule saved through the service, audited; tier 1 refused off")
    void theTiers() throws Exception {
        PriceTierService tiers = new PriceTierService(new JdbcPriceTierRepository(),
                PriceTierService.Transactions.jdbc(), ChangeAnnouncer.disabled());
        signIn(AppPermissions.SALES_CREATE);
        assertThrows(BusinessRuleException.class, () -> tiers.save(tiers.catalog().all()));

        signIn(AppPermissions.SEL_PRICE_UPDATE);
        List<PriceTier> named = new ArrayList<>();
        named.add(new PriceTier(1, "قطاعي", true, null));
        named.add(new PriceTier(2, "تجزئة", true, null));
        named.add(new PriceTier(3, "جملة", true, null));
        assertEquals(3, tiers.save(named));
        assertEquals("جملة", tiers.catalog().name(3));
        assertTrue(scalar("SELECT COUNT(*) FROM audit_log WHERE table_name = 'type_price'") >= 3,
                "who renamed a tier is recorded");

        // Two names swapped in one save: the UNIQUE index is side-stepped, not met.
        List<PriceTier> swapped = List.of(named.get(0), named.get(1).withName("جملة"), named.get(2).withName("تجزئة"));
        assertEquals(2, tiers.save(swapped));
        assertEquals(2, tiers.save(named));
    }

    @Test
    @Order(3)
    @DisplayName("a wholesale customer's line of an item with no wholesale price: tier 1's, stored with its tier and list")
    void wholesaleCustomerFallsBack() throws Exception {
        itemId = insertItem();
        execute("INSERT INTO custom (name, limit_num, first_balance, price_id, area_id, user_id) VALUES ('"
                + STAMP + "-W', 0, 0, 3, 1, " + OPERATOR + ")");
        wholesaleCustomer = scalar("SELECT id FROM custom WHERE name = '" + STAMP + "-W'");

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales line = lineAtTier(3);
        assertEquals(100, line.getPrice(), "tier 1's price stands in for the missing wholesale one");
        assertTrue(line.isFromFirstTier());

        invoiceA = sales().save(sale(3, line)).invoiceNumber();
        assertEquals("3", text("SELECT price_tier_id FROM total_sales WHERE invoice_number = " + invoiceA));
        assertEquals("100.00|100.00", text("SELECT CONCAT(price, '|', list_price) FROM sales WHERE invoice_number = "
                + invoiceA));
        assertEquals("100.00", text("SELECT list_price FROM sales_names_table WHERE invoice_number = " + invoiceA));
    }

    @Test
    @Order(4)
    @DisplayName("another tier needs sales.price.tier.change - refused before a number is taken")
    void changingTheTier() throws Exception {
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        long counter = counter();
        Sales trade = lineAtTier(2);
        assertEquals(95, trade.getPrice());
        assertThrows(BusinessRuleException.class, () -> sales().save(sale(2, trade)));
        assertEquals(counter, counter(), "the counter did not move");

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW, AppPermissions.SALES_PRICE_TIER_CHANGE);
        int invoice = sales().save(sale(2, lineAtTier(2))).invoiceNumber();
        assertEquals("2|95.00", text("SELECT CONCAT(t.price_tier_id, '|', s.price) FROM total_sales t"
                + " JOIN sales s ON s.invoice_number = t.invoice_number WHERE t.invoice_number = " + invoice));
    }

    @Test
    @Order(5)
    @DisplayName("a price under the list needs sales.price.below.list, and the report says who, when and how much")
    void belowTheList() throws Exception {
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        long counter = counter();
        Sales under = lineAtTier(3);
        under.setPrice(90);
        InvoiceLineService.recalculate(under);
        assertThrows(BusinessRuleException.class, () -> sales().save(sale(3, under)));
        assertEquals(counter, counter());

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW, AppPermissions.SALES_PRICE_BELOW_LIST,
                AppPermissions.REPORTS_SHOW_SALES);
        Sales twoUnder = lineAtTier(3);
        twoUnder.setQuantity(2);
        twoUnder.setPrice(90);
        InvoiceLineService.recalculate(twoUnder);
        int invoice = sales().save(sale(3, twoUnder)).invoiceNumber();

        TierReports.BelowListPage report = new TierReportService(new JdbcTierReportRepository())
                .belowList(TODAY, TODAY, null, 50, 0);
        assertEquals(1, report.summary().lines(), "the lines at their list are not here");
        assertEquals(0, new BigDecimal("20.00").compareTo(report.summary().given()), "(100 - 90) x 2");
        TierReports.BelowList row = report.rows().getFirst();
        assertEquals(invoice, row.invoiceNumber());
        assertEquals(STAMP, row.user());
        assertEquals(Integer.valueOf(3), row.tierId());
    }

    @Test
    @Order(6)
    @DisplayName("the customer moved to another tier: yesterday's invoice keeps its own, and an edit keeps it unasked")
    void theTierStaysWithTheDocument() throws Exception {
        execute("UPDATE custom SET price_id = 2 WHERE id = " + wholesaleCustomer);
        assertEquals(Integer.valueOf(3), InvoicePriceTier.jdbc().storedTier(DocumentType.SALES, invoiceA));

        signIn(AppPermissions.SALES_UPDATE, AppPermissions.ITEMS_SHOW);
        int lineId = scalar("SELECT id FROM sales WHERE invoice_number = " + invoiceA);
        Sales line = lineAtTier(3);
        line.setId(lineId);
        line.setInvoiceNumber(invoiceA);
        InvoiceSaveCommand edit = new InvoiceSaveCommand(invoiceA, TODAY, InvoiceType.CASH, BigDecimal.ZERO,
                DiscountType.AMOUNT, new BigDecimal("100"), STAMP + " edited", wholesaleCustomer, "customer",
                MAIN_TREASURY, DELEGATE, false, 0, null, List.of(line), 1, null,
                DaoFactory.INSTANCE.totalsSalesDao().getDataById(invoiceA).getUpdated_at(), null, 3);
        sales().save(edit);
        assertEquals("3", text("SELECT price_tier_id FROM total_sales WHERE invoice_number = " + invoiceA));
        execute("UPDATE custom SET price_id = 3 WHERE id = " + wholesaleCustomer);
    }

    @Test
    @Order(7)
    @DisplayName("the item is on the missing-prices report, and filling the tier by its rule takes it off")
    void missingAndFilled() throws Exception {
        signIn(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_UPDATE, AppPermissions.SEL_PRICE_UPDATE);
        TierReportService reports = new TierReportService(new JdbcTierReportRepository());
        TierReports.MissingPage missing = reports.missing(List.of(1, 2, 3), STAMP, 50, 0);
        assertEquals(1, missing.total());
        assertEquals(List.of(3), missing.rows().getFirst().missingTiers());

        PriceTierService tiers = new PriceTierService(new JdbcPriceTierRepository(),
                PriceTierService.Transactions.jdbc(), ChangeAnnouncer.disabled());
        List<PriceTier> withRule = new ArrayList<>(tiers.catalog().all());
        withRule.set(2, withRule.get(2).withRule(TierFillRule.fromTier(1, new BigDecimal("-10"), new BigDecimal("0.25"))));
        tiers.save(withRule);

        TierFillService fill = new TierFillService(new JdbcPriceTierRepository(), new JdbcTierFillRepository(),
                PriceTierService.Transactions.jdbc(), ChangeAnnouncer.disabled());
        List<TierFill.Change> preview = fill.preview(3, true);
        TierFill.Change ours = preview.stream().filter(change -> change.itemId() == itemId).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("90.00").compareTo(ours.after()), "100 less 10%");
        fill.apply(3, true, preview);
        assertEquals("90.00", text("SELECT sel_price3 FROM items WHERE id = " + itemId));
        assertEquals(0, reports.missing(List.of(1, 2, 3), STAMP, 50, 0).total());

        // Applied again, the rule has nothing left to write.
        assertTrue(fill.preview(3, false).stream().noneMatch(change -> change.itemId() == itemId));
    }

    @Test
    @Order(8)
    @DisplayName("a tier switched off: its customers open at tier 1, and a document saved at it keeps it")
    void switchedOff() throws Exception {
        signIn(AppPermissions.SEL_PRICE_UPDATE);
        PriceTierService tiers = new PriceTierService(new JdbcPriceTierRepository(),
                PriceTierService.Transactions.jdbc(), ChangeAnnouncer.disabled());
        List<PriceTier> off = new ArrayList<>(tiers.catalog().all());
        off.set(2, off.get(2).withActive(false));
        tiers.save(off);
        assertEquals(1, tiers.catalog().forCustomer(3));
        assertEquals(Integer.valueOf(3), InvoicePriceTier.jdbc().storedTier(DocumentType.SALES, invoiceA));

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW, AppPermissions.SALES_PRICE_TIER_CHANGE);
        assertThrows(BusinessRuleException.class, () -> sales().save(sale(3, lineAtTier(3))),
                "a tier switched off is not chosen anew, even with the key");
        assertNull(InvoicePriceTier.jdbc().storedTier(DocumentType.SALES, 0));
    }

    // --- the screen's own path -------------------------------------------------------------------------------

    /** The line the entry form builds for the item at a tier, with the list price behind it. */
    private static Sales lineAtTier(int tier) throws Exception {
        InvoiceItemSelection selection = new InvoiceItemSelectionService(DocumentType.SALES,
                new ItemsService(DaoFactory.INSTANCE), new SalesInvoice()::getItemsPrice)
                .selectByName(STAMP, 1, tier);
        List<Sales> lines = new ArrayList<>();
        return new InvoiceLineService<>(DocumentType.SALES, 0, new SalesInvoice()::object_TableData)
                .add(lines, selection.draft(), false, true).line();
    }

    private static InvoiceSaveService<Sales, Total_Sales, Customers, CustomerAccount> sales() {
        return new InvoiceSaveService<>(new SalesInvoice(), repository(DaoFactory.INSTANCE.totalsSalesDao()),
                DocumentType.SALES, name -> new TreasuryService(DaoFactory.INSTANCE).getTreasuryByName(name),
                name -> new Employees(1, name));
    }

    private static InvoiceSaveCommand sale(int tier, Sales line) {
        return new InvoiceSaveCommand(0, TODAY, InvoiceType.CASH, BigDecimal.ZERO, DiscountType.AMOUNT,
                BigDecimal.valueOf(line.getTotal()), STAMP, wholesaleCustomer, "customer", MAIN_TREASURY, DELEGATE,
                false, 0, null, List.of(line), 1, null, null, null, tier);
    }

    private static TotalsAndPurchaseList<Sales, Total_Sales> repository(DaoList<Total_Sales> dao) {
        return new TotalsAndPurchaseList<>() {
            @Override
            public DaoList<Total_Sales> totalDao() {
                return dao;
            }

            @Override
            public List<Total_Sales> totalList(String dateFrom, String dateTo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Sales> purchaseOrSalesList(int from, int to) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getMaxId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public TotalsPage<Total_Sales> searchTotals(TotalsSearchCriteria criteria, int page, int pageSize) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TotalsSummaryRow summarizeTotals(TotalsSearchCriteria criteria) {
                throw new UnsupportedOperationException();
            }
        };
    }

    // --- fixtures ---------------------------------------------------------------------------------------------

    private static int insertItem() throws Exception {
        Connection connection = ConnectionManager.acquire();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items(barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2, sel_price3,"
                        + " unit_id, mini_quantity, user_id) VALUES (?, ?, 1, 40, 100, 95, 0, 1, 0, 1)",
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
