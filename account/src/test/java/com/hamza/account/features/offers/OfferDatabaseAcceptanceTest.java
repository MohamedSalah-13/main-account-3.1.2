package com.hamza.account.features.offers;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.document.TotalsPage;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.document.TotalsSummaryRow;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.features.invoice.InvoiceOfferPreview;
import com.hamza.account.features.invoice.InvoiceOffers;
import com.hamza.account.features.invoice.InvoiceSaveCommand;
import com.hamza.account.features.invoice.InvoiceSaveService;
import com.hamza.account.features.invoice.InvoiceValidationException;
import com.hamza.account.features.pricing.PriceTierService;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoiceReturn;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoList;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The offers against a real MySQL (V85, docs/pricing-and-offers-plan.md phase B), through the save the invoice
 * screens call - {@link InvoiceSaveService} built by its public constructor, as {@code CustomData} builds it -
 * with the lines given their offers by {@link InvoiceOfferPreview}, the screen's own path. The only place these
 * claims exist: that V85 applies to a schema built from nothing and its CHECKs and trigger refuse what the rules
 * refuse; that example 1 of §5 is stored as worked by hand and {@code document_profit} says the same; that the
 * save refuses a sale the offers do not say - one started since the screen looked, one stopped since - before a
 * number is taken; that a stopped offer stays with the invoice it was given on; that part of an offer invoice
 * comes back with its share of the offer and nothing else; and example 7, a delegate's ceiling judging the
 * manual discount alone.
 * <p>
 * The case: soap in a group of its own, costing 25 and sold at 40; rice outside it, costing 60 and sold at 100;
 * "10% off the detergents" from today. The session is user 9, never user 1 - who bypasses every permission.
 * Gated like the others ({@code -Daccount.db.acceptance=true}); the scratch schema is named uniquely and dropped.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OfferDatabaseAcceptanceTest {

    private static final String SCHEMA_PREFIX = "account_offer_acceptance_";
    private static final int OPERATOR = 9;
    private static final String STAMP = "OFFER-" + System.nanoTime();
    private static final String MAIN_TREASURY = "الخزينة الرئيسية";
    private static final String DELEGATE = "بيع مباشر";
    private static final LocalDate TODAY = LocalDate.now();
    private static final int CASH_CUSTOMER = 1;

    private static String host;
    private static String port;
    private static String username;
    private static String password;
    private static String schema;
    private static UserSessionContext session;

    private static int detergents;
    private static int soap;
    private static int rice;
    private static int tenPercent;
    private static int invoiceA;
    private static int fortyOffId;

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
            // The edition carries the offers - the add-on the save and the service ask for.
            ServiceRegistry.register(ProductFeatureAccess.class, ProductFeatureAccess.allEnabled());
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
        ServiceRegistry.register(ProductFeatureAccess.class, null);
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

    private static OfferService offers() {
        return new OfferService(new JdbcOfferRepository(), PriceTierService.Transactions.jdbc(),
                ChangeAnnouncer.disabled(), () -> ProductFeatureAccess.allEnabled(), LocalDate::now);
    }

    @Test
    @Order(1)
    @DisplayName("from nothing: V85's tables, keys, CHECKs and grants")
    void theSchema() throws Exception {
        for (String table : List.of("offer", "offer_target", "offer_price_tier")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = '" + table + "'"), table);
        }
        for (String table : List.of("sales", "sales_re")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE"
                    + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'"
                    + " AND COLUMN_NAME = 'offer_id' AND REFERENCED_TABLE_NAME = 'offer'"), table);
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = 'sales_names_table' AND COLUMN_NAME = 'offer_name'"), "the line view reads it");

        // A percentage offer with no percentage, and "everything except everything": the CHECKs, with IS NOT
        // NULL written in each branch.
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on) VALUES ('x', 'PERCENT', 'DRAFT',"
                + " CURRENT_DATE)");
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, amount, percent) VALUES ('y',"
                + " 'AMOUNT', 'DRAFT', CURRENT_DATE, 5, 10)");
        // Each offer key goes to whoever held the matching item key.
        assertEquals(grants("items.show"), grants("offer.show"));
        assertEquals(grants("items.update"), grants("offer.create"));
        assertEquals(grants("items.update"), grants("offer.update"));
        assertEquals(grants("items.delete"), grants("offer.delete"));
    }

    @Test
    @Order(2)
    @DisplayName("an offer written through the service: its key asked, its start not in the past, audited")
    void theOffer() throws Exception {
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-D', 1)");
        detergents = scalar("SELECT id FROM sub_group WHERE name = '" + STAMP + "-D'");
        soap = insertItem("S", detergents, 25, 40);
        rice = insertItem("R", 1, 60, 100);

        Offer draft = new Offer(0, STAMP + " 10%", OfferKind.PERCENT, OfferStatus.ACTIVE, TODAY, null, null, 0,
                new BigDecimal("10"), null, null, null, null, List.of(OfferTarget.subGroup(detergents)), Set.of(),
                null);
        signIn(AppPermissions.OFFER_SHOW);
        assertThrows(BusinessRuleException.class, () -> offers().create(draft));
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        Offer yesterday = new Offer(0, STAMP + " old", OfferKind.PERCENT, OfferStatus.ACTIVE, TODAY.minusDays(1),
                null, null, 0, new BigDecimal("10"), null, null, null, null, List.of(OfferTarget.everything()),
                Set.of(), null);
        assertThrows(UserValidationException.class, () -> offers().create(yesterday));

        tenPercent = offers().create(draft);
        assertEquals("ACTIVE|PERCENT|10.000", text("SELECT CONCAT(status, '|', kind, '|', percent) FROM offer"
                + " WHERE id = " + tenPercent));
        assertEquals("SUB_GROUP|" + detergents, text("SELECT CONCAT(scope, '|', sub_group_id) FROM offer_target"
                + " WHERE offer_id = " + tenPercent));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_log WHERE table_name = 'offer' AND record_id = '"
                + tenPercent + "' AND action_type = 'INSERT'"), "who wrote it is recorded");
    }

    @Test
    @Order(3)
    @DisplayName("example 1: soap 3 x 40 takes 12.00, stored on the line, and document_profit is 108 - 75")
    void exampleOne() throws Exception {
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales soapLine = line(new Sales(), soap, 25, 3, "40", "0");
        Sales riceLine = line(new Sales(), rice, 60, 1, "100", "0");
        offerLines(List.of(soapLine, riceLine));
        assertEquals(12.0, soapLine.getDiscount(), "the screen's preview");

        invoiceA = sales().save(sale(List.of(soapLine, riceLine), "0")).invoiceNumber();
        assertEquals("40.00|12.00|" + tenPercent + "|12.00", text("SELECT CONCAT(price, '|', discount, '|',"
                + " offer_id, '|', offer_discount) FROM sales WHERE invoice_number = " + invoiceA + " AND num = " + soap));
        assertEquals("0.00|NULL|0.00", text("SELECT CONCAT(discount, '|', COALESCE(offer_id, 'NULL'), '|',"
                + " offer_discount) FROM sales WHERE invoice_number = " + invoiceA + " AND num = " + rice));
        assertEquals("208.00", text("SELECT total FROM total_sales WHERE invoice_number = " + invoiceA),
                "the header's total is the lines after their discounts: 108 + 100");
        assertEquals("208.00|135.00|73.00", text("SELECT CONCAT(net_revenue, '|', cost_of_sales, '|', profit)"
                + " FROM document_profit WHERE document_kind = 'sales' AND document_id = " + invoiceA),
                "108 - 75 on the soap and 100 - 60 on the rice, no view touched");
        assertEquals(STAMP + " 10%", text("SELECT offer_name FROM sales_names_table WHERE invoice_number = "
                + invoiceA + " AND num = " + soap));
        // An offer's discount on a line naming no offer: the CHECK.
        assertSqlRefused(3819, "UPDATE sales SET offer_id = NULL WHERE invoice_number = " + invoiceA
                + " AND num = " + soap);
    }

    @Test
    @Order(4)
    @DisplayName("a sale the offers do not say is refused before a number is taken; a used offer keeps its terms")
    void theSaveJudges() throws Exception {
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        long counter = counter();
        Sales noOffer = line(new Sales(), soap, 25, 3, "40", "0");
        InvoiceValidationException refused = assertThrows(InvoiceValidationException.class,
                () -> sales().save(sale(List.of(noOffer), "0")), "the offer reaches the line and it does not say so");
        assertTrue(refused.getMessage().contains(STAMP + " 10%"), refused.getMessage());

        Sales wrongFigure = line(new Sales(), soap, 25, 3, "40", "0");
        offerLines(List.of(wrongFigure));
        wrongFigure.setOfferDiscount(new BigDecimal("11.99"));
        wrongFigure.setDiscount(11.99);
        InvoiceLineService.recalculate(wrongFigure);
        assertThrows(InvoiceValidationException.class, () -> sales().save(sale(List.of(wrongFigure), "0")));
        assertEquals(counter, counter(), "the counter did not move");

        // Its terms are history now: the service refuses them, and so does the database.
        signIn(AppPermissions.OFFER_UPDATE, AppPermissions.OFFER_SHOW);
        Offer stored = offers().find(tenPercent).orElseThrow();
        Offer twenty = new Offer(stored.id(), stored.name(), stored.kind(), stored.status(), stored.startsOn(), null,
                null, 0, new BigDecimal("20"), null, null, null, null, stored.targets(), stored.priceTierIds(),
                stored.version());
        UserValidationException terms = assertThrows(UserValidationException.class, () -> offers().update(twenty));
        assertEquals("offer.error.used.terms", terms.getMessage());
        assertSqlRefused(1644, "UPDATE offer SET percent = 20 WHERE id = " + tenPercent);
        execute("UPDATE offer SET notes = 'still editable' WHERE id = " + tenPercent);
    }

    @Test
    @Order(5)
    @DisplayName("one soap of three comes back: price 40, its share of the offer 4.00, a refund of 36 - every guard passed")
    void aPartialReturn() throws Exception {
        signIn(AppPermissions.SALES_RE_CREATE, AppPermissions.ITEMS_SHOW);
        int sourceLine = scalar("SELECT id FROM sales WHERE invoice_number = " + invoiceA + " AND num = " + soap);
        Sales_Return back = line(new Sales_Return(), soap, 25, 1, "40", "4.00");
        back.setSourceLineId(sourceLine);
        InvoiceSaveCommand command = new InvoiceSaveCommand(0, TODAY, InvoiceType.CASH, BigDecimal.ZERO,
                DiscountType.AMOUNT, new BigDecimal("36.00"), STAMP, CASH_CUSTOMER, "customer", MAIN_TREASURY,
                DELEGATE, false, invoiceA, null, List.of(back), 1, null, null, null, 1);
        int returned = returns().save(command).invoiceNumber();

        assertEquals("40.00|4.00|" + tenPercent + "|4.00|25.00", text("SELECT CONCAT(price, '|', discount, '|',"
                + " offer_id, '|', offer_discount, '|', buy_price) FROM sales_re WHERE invoice_number = " + returned));
        assertEquals("-36.00|-25.00|-11.00", text("SELECT CONCAT(net_revenue, '|', cost_of_sales, '|', profit)"
                + " FROM document_profit WHERE document_kind = 'sales_return' AND document_id = " + returned));
        OfferUsage usage = new JdbcOfferRepository().usage(tenPercent);
        assertEquals(0, new BigDecimal("12.00").compareTo(usage.given()));
        assertEquals(0, new BigDecimal("4.00").compareTo(usage.returned()), "what the offer gave back is on record");
    }

    @Test
    @Order(6)
    @DisplayName("example 7: a delegate's 10% on 300 judges the manual 20 - the offer's 40 is the shop's")
    void exampleSeven() throws Exception {
        execute("UPDATE employees SET max_discount_percent = 10 WHERE id = 1");
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        Offer fortyOff = new Offer(0, STAMP + " 40", OfferKind.AMOUNT, OfferStatus.ACTIVE, TODAY, null, null, 0,
                null, new BigDecimal("40"), null, null, null, List.of(OfferTarget.item(soap)), Set.of(), null);
        fortyOffId = offers().create(fortyOff);

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales soapLine = line(new Sales(), soap, 25, 1, "200", "0");
        Sales riceLine = line(new Sales(), rice, 60, 1, "100", "0");
        offerLines(List.of(soapLine, riceLine));
        assertEquals(40.0, soapLine.getDiscount(), "40 off beats 10% (20) for the customer");

        long counter = counter();
        assertThrows(InvoiceValidationException.class,
                () -> sales().save(sale(List.of(soapLine, riceLine), "35")), "a manual 35 is above 30");
        assertEquals(counter, counter());
        int saved = sales().save(sale(List.of(soapLine, riceLine), "20")).invoiceNumber();
        assertEquals("260.00|20.00", text("SELECT CONCAT(total, '|', discount) FROM total_sales"
                + " WHERE invoice_number = " + saved), "300 less the offer's 40, and 20 by hand on top");
        execute("UPDATE employees SET max_discount_percent = NULL WHERE id = 1");
    }

    @Test
    @Order(7)
    @DisplayName("a stopped offer reaches no new sale - refused, counter unmoved - and stays with the invoice it was given on")
    void stopped() throws Exception {
        signIn(AppPermissions.OFFER_UPDATE, AppPermissions.OFFER_SHOW);
        Offer offer = offers().find(tenPercent).orElseThrow();
        offers().stop(tenPercent, offer.version());
        // And the forty off, which would otherwise reach the soap on invoice A as well: an edit is judged by the
        // offers in force on its date, and it would win there, giving the customer more.
        offers().stop(fortyOffId, offers().find(fortyOffId).orElseThrow().version());

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales stale = line(new Sales(), soap, 25, 3, "40", "12");
        stale.setOfferId(tenPercent);
        stale.setOfferName(STAMP + " 10%");
        stale.setOfferDiscount(new BigDecimal("12.00"));
        long counter = counter();
        InvoiceValidationException refused = assertThrows(InvoiceValidationException.class,
                () -> sales().save(sale(List.of(stale), "0")));
        assertTrue(refused.getMessage().contains(STAMP + " 10%"), refused.getMessage());
        assertEquals(counter, counter());

        // Correcting a note on the invoice that had it: the offer still reaches it (ق-ع٧).
        signIn(AppPermissions.SALES_UPDATE, AppPermissions.ITEMS_SHOW);
        List<Sales> lines = new ArrayList<>();
        for (int item : List.of(soap, rice)) {
            Sales line = line(new Sales(), item, item == soap ? 25 : 60, item == soap ? 3 : 1,
                    item == soap ? "40" : "100", "0");
            line.setId(scalar("SELECT id FROM sales WHERE invoice_number = " + invoiceA + " AND num = " + item));
            line.setInvoiceNumber(invoiceA);
            lines.add(line);
        }
        InvoiceOfferPreview preview = new InvoiceOfferPreview(new InvoiceOffers.JdbcGroups());
        preview.setRecorded(Set.of(tenPercent));
        preview.setOffers(offers().inForce(Set.of(tenPercent)));
        preview.run(lines, TODAY, 1);
        InvoiceSaveCommand edit = new InvoiceSaveCommand(invoiceA, TODAY, InvoiceType.CASH, BigDecimal.ZERO,
                DiscountType.AMOUNT, new BigDecimal("208.00"), STAMP + " edited", CASH_CUSTOMER, "customer",
                MAIN_TREASURY, DELEGATE, false, 0, null, lines, 1, null,
                DaoFactory.INSTANCE.totalsSalesDao().getDataById(invoiceA).getUpdated_at(), null, 1);
        sales().save(edit);
        assertEquals("12.00|" + tenPercent, text("SELECT CONCAT(offer_discount, '|', offer_id) FROM sales"
                + " WHERE invoice_number = " + invoiceA + " AND num = " + soap));
    }

    @Test
    @Order(8)
    @DisplayName("a used offer is refused a delete with the reason; an unused draft goes, with its targets")
    void deleting() throws Exception {
        signIn(AppPermissions.OFFER_DELETE, AppPermissions.OFFER_CREATE, AppPermissions.OFFER_SHOW);
        assertThrows(BusinessRuleException.class, () -> offers().delete(tenPercent));
        int draft = offers().create(new Offer(0, STAMP + " draft", OfferKind.PRICE, OfferStatus.DRAFT, TODAY, null,
                null, 0, null, null, new BigDecimal("30"), null, null, List.of(OfferTarget.item(rice)), Set.of(1),
                null));
        assertEquals(1, offers().delete(draft));
        assertEquals(0, scalar("SELECT COUNT(*) FROM offer_target WHERE offer_id = " + draft));
        assertEquals(0, scalar("SELECT COUNT(*) FROM offer_price_tier WHERE offer_id = " + draft));
        // And the item a target names cannot be deleted from under it.
        assertThrows(SQLException.class, () -> execute("DELETE FROM items WHERE id = " + soap));
    }

    @Test
    @Order(9)
    @DisplayName("the below-cost warning's two statements: a base unit, and a named unit at its item's price times twelve")
    void belowCost() throws Exception {
        execute("INSERT INTO items_units (items_id, unit, quantity, buy_price, sel_price, sel_price2, sel_price3)"
                + " VALUES (" + soap + ", 2, 12, 0, 0, 0, 0)");
        signIn(AppPermissions.OFFER_SHOW);
        Offer half = new Offer(0, STAMP + " half", OfferKind.PERCENT, OfferStatus.DRAFT, TODAY, null, null, 0,
                new BigDecimal("50"), null, null, null, null, List.of(OfferTarget.subGroup(detergents)), Set.of(),
                null);
        assertThrows(BusinessRuleException.class, () -> offers().belowCost(half, Set.of(1, 2, 3)),
                "a figure about cost asks the cost column's key");

        signIn(AppPermissions.OFFER_SHOW, AppPermissions.SHOW_COLUMN_BUY_PRICE);
        List<OfferCostCheck.BelowCost> pieces = offers().belowCost(half, Set.of(1, 2, 3));
        assertEquals(1, pieces.size(), "the soap, not the rice outside the group");
        assertEquals(soap, pieces.getFirst().itemId());
        assertEquals(0, new BigDecimal("20.00").compareTo(pieces.getFirst().net()), "40 less half");
        assertEquals(0, new BigDecimal("25.00").compareTo(pieces.getFirst().cost()));

        Offer cartonAt250 = new Offer(0, STAMP + " carton", OfferKind.PRICE, OfferStatus.DRAFT, TODAY, null, null,
                0, null, null, new BigDecimal("250"), 2, null, List.of(OfferTarget.item(soap)), Set.of(), null);
        List<OfferCostCheck.BelowCost> cartons = offers().belowCost(cartonAt250, Set.of(1, 2, 3));
        assertEquals(1, cartons.size());
        assertEquals(0, new BigDecimal("480").compareTo(cartons.getFirst().price()), "40 x 12, with no price of its own");
        assertEquals(0, new BigDecimal("300.00").compareTo(cartons.getFirst().cost()), "25 x 12");
        assertEquals(0, new BigDecimal("250.00").compareTo(cartons.getFirst().net()));
    }

    // --- the screen's own path -------------------------------------------------------------------------------

    /** The offers the till would apply, written on the lines as the invoice screen writes them. */
    private static void offerLines(List<? extends BasePurchasesAndSales> lines) throws Exception {
        InvoiceOfferPreview preview = new InvoiceOfferPreview(new InvoiceOffers.JdbcGroups());
        preview.setOffers(offers().inForce());
        preview.run(lines, TODAY, 1);
    }

    private static <T extends BasePurchasesAndSales> T line(T line, int itemId, double cost, double quantity,
                                                             String price, String discount) {
        ItemsModel item = new ItemsModel(itemId, STAMP, STAMP);
        item.setBuyPrice(cost);
        item.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setQuantity(quantity);
        line.setPrice(Double.parseDouble(price));
        line.setDiscount(Double.parseDouble(discount));
        InvoiceLineService.recalculate(line);
        return line;
    }

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

    private static InvoiceSaveCommand sale(List<Sales> lines, String headerDiscount) {
        BigDecimal net = lines.stream().map(line -> BigDecimal.valueOf(line.getTotal_after_discount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).subtract(new BigDecimal(headerDiscount));
        return new InvoiceSaveCommand(0, TODAY, InvoiceType.CASH, new BigDecimal(headerDiscount), DiscountType.AMOUNT,
                net, STAMP, CASH_CUSTOMER, "customer", MAIN_TREASURY, DELEGATE, false, 0, null, lines, 1, null, null,
                null, 1);
    }

    private static <L extends BasePurchasesAndSales, H extends com.hamza.account.model.base.BaseTotals>
    TotalsAndPurchaseList<L, H> repository(DaoList<H> dao) {
        return new TotalsAndPurchaseList<>() {
            @Override
            public DaoList<H> totalDao() {
                return dao;
            }

            @Override
            public List<H> totalList(String dateFrom, String dateTo) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<L> purchaseOrSalesList(int from, int to) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getMaxId() {
                throw new UnsupportedOperationException();
            }

            @Override
            public TotalsPage<H> searchTotals(TotalsSearchCriteria criteria, int page, int pageSize) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TotalsSummaryRow summarizeTotals(TotalsSearchCriteria criteria) {
                throw new UnsupportedOperationException();
            }
        };
    }

    // --- fixtures ---------------------------------------------------------------------------------------------

    private static int insertItem(String suffix, int subGroup, double cost, double price) throws Exception {
        Connection connection = ConnectionManager.acquire();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items(barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2, sel_price3,"
                        + " unit_id, mini_quantity, user_id) VALUES (?, ?, ?, ?, ?, 0, 0, 1, 0, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, STAMP + suffix);
            statement.setString(2, STAMP + suffix);
            statement.setInt(3, subGroup);
            statement.setDouble(4, cost);
            statement.setDouble(5, price);
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

    private static int grants(String key) throws Exception {
        return scalar("SELECT COUNT(*) FROM auth_role_permission rp JOIN auth_permission p"
                + " ON p.id = rp.permission_id WHERE p.permission_key = '" + key + "'");
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
