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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * manual discount alone. And phase C (V86): V86 from nothing and over a V85 database with sales on it; examples
 * 2, 3 and 4 worked by hand, the gift and a return of part of it; the limit on one invoice; and the global limit
 * with two tills in two real transactions, the second waiting on the first's lock. And phase D (V87): V87 from
 * nothing and over a V86 database with offers on it; examples 5 and 6 worked by hand; a bundle's barcode refused
 * where another bundle or an item holds it, and seen by the item screen's own checks; and an invoice offer's
 * limit counting invoices.
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

    // --- phase C: the quantity, the gift and the limits (V86) ------------------------------------------------

    private static int water;
    private static int juice;
    private static int shampoo;
    private static int conditioner;
    private static int giftSale;

    /** "{@code size} for {@code price}" on one item, written through the service and switched on. */
    private static int quantityOffer(String name, int item, String size, String price, String perInvoice,
                                     String total) throws Exception {
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        return offers().create(new Offer(0, name, OfferKind.QUANTITY_PRICE, OfferStatus.ACTIVE, TODAY, null, null,
                0, null, null, new BigDecimal(price), null, new BigDecimal(size), null, null,
                perInvoice == null ? null : new BigDecimal(perInvoice), total == null ? null : new BigDecimal(total),
                null, List.of(OfferTarget.item(item)), Set.of(), null));
    }

    @Test
    @Order(10)
    @DisplayName("V86 from nothing: the new kinds' CHECK, the gift's, the covered units', and the limits as terms")
    void thePhaseCSchema() throws Exception {
        for (String column : List.of("buy_quantity", "get_quantity", "get_percent", "max_per_invoice",
                "quantity_limit")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = 'offer' AND COLUMN_NAME = '" + column + "'"), column);
        }
        // A buy-and-get with nothing given, and a percentage carrying a quantity: the rewritten CHECK.
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, buy_quantity) VALUES ('c1',"
                + " 'BUY_GET', 'DRAFT', CURRENT_DATE, 2)");
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, percent, buy_quantity) VALUES"
                + " ('c2', 'PERCENT', 'DRAFT', CURRENT_DATE, 10, 3)");
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, percent, max_per_invoice) VALUES"
                + " ('c3', 'PERCENT', 'DRAFT', CURRENT_DATE, 10, 0)");
        // A gift that is a group: the role's CHECK.
        assertSqlRefused(3819, "INSERT INTO offer_target (offer_id, role, scope, sub_group_id) VALUES ("
                + tenPercent + ", 'REWARD', 'SUB_GROUP', " + detergents + ")");
        // Units covered on a line naming no offer: the line's CHECK.
        assertSqlRefused(3819, "UPDATE sales SET offer_quantity = 1 WHERE invoice_number = " + invoiceA
                + " AND num = " + rice);
        // Invoice A's soap line - saved, then edited, through the save - records the three units its offer covered.
        assertEquals("3.000", text("SELECT offer_quantity FROM sales WHERE invoice_number = " + invoiceA
                + " AND num = " + soap));
        // A used offer's limit is a term: the trigger refuses it as it refuses the percentage.
        assertSqlRefused(1644, "UPDATE offer SET max_per_invoice = 5 WHERE id = " + tenPercent);
    }

    @Test
    @Order(11)
    @DisplayName("example 2: 3 for 100, seven at 40 - two groups, 40 off, six units covered; document_profit 240 - 175")
    void exampleTwo() throws Exception {
        juice = insertItem("J", 1, 25, 40);
        int threeFor100 = quantityOffer(STAMP + " 3 for 100", juice, "3", "100", null, null);
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales seven = line(new Sales(), juice, 25, 7, "40", "0");
        offerLines(List.of(seven));
        int saved = sales().save(sale(List.of(seven), "0")).invoiceNumber();

        assertEquals("40.00|" + threeFor100 + "|40.00|6.000", text("SELECT CONCAT(discount, '|', offer_id, '|',"
                + " offer_discount, '|', offer_quantity) FROM sales WHERE invoice_number = " + saved));
        assertEquals("240.00|175.00|65.00", text("SELECT CONCAT(net_revenue, '|', cost_of_sales, '|', profit)"
                + " FROM document_profit WHERE document_kind = 'sales' AND document_id = " + saved),
                "280 less the offer's 40, against seven at 25");
    }

    @Test
    @Order(12)
    @DisplayName("example 4: shampoo 60 and the conditioner 40 a gift - 24 and 16, a profit of -15; the shampoo back"
            + " refunds 36")
    void exampleFour() throws Exception {
        execute("INSERT INTO sub_group (name, main_id) VALUES ('" + STAMP + "-H', 1)");
        int hair = scalar("SELECT id FROM sub_group WHERE name = '" + STAMP + "-H'");
        shampoo = insertItem("SH", hair, 45, 60);
        conditioner = insertItem("CO", hair, 30, 40);
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        int gift = offers().create(new Offer(0, STAMP + " gift", OfferKind.BUY_GET, OfferStatus.ACTIVE, TODAY, null,
                null, 0, null, null, null, null, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100"), null, null,
                null, List.of(OfferTarget.item(shampoo), OfferTarget.reward(conditioner)), Set.of(), null));
        assertEquals("REWARD", text("SELECT role FROM offer_target WHERE offer_id = " + gift + " AND item_id = "
                + conditioner));

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales shampooLine = line(new Sales(), shampoo, 45, 1, "60", "0");
        Sales conditionerLine = line(new Sales(), conditioner, 30, 1, "40", "0");
        offerLines(List.of(shampooLine, conditionerLine));
        giftSale = sales().save(sale(List.of(shampooLine, conditionerLine), "0")).invoiceNumber();

        assertEquals("24.00|1.000", text("SELECT CONCAT(offer_discount, '|', offer_quantity) FROM sales"
                + " WHERE invoice_number = " + giftSale + " AND num = " + shampoo));
        assertEquals("16.00|1.000", text("SELECT CONCAT(offer_discount, '|', offer_quantity) FROM sales"
                + " WHERE invoice_number = " + giftSale + " AND num = " + conditioner), "no line at zero (ق-ع٣)");
        assertEquals("60.00|75.00|-15.00", text("SELECT CONCAT(net_revenue, '|', cost_of_sales, '|', profit)"
                + " FROM document_profit WHERE document_kind = 'sales' AND document_id = " + giftSale),
                "the gift cost the shop goods, and the profit says so");

        // The shampoo alone back: 60 less its 24 - the rule the returns already had (ق-ع٢), nothing new.
        signIn(AppPermissions.SALES_RE_CREATE, AppPermissions.ITEMS_SHOW);
        Sales_Return back = line(new Sales_Return(), shampoo, 45, 1, "60", "24.00");
        back.setSourceLineId(scalar("SELECT id FROM sales WHERE invoice_number = " + giftSale + " AND num = "
                + shampoo));
        int returned = returns().save(new InvoiceSaveCommand(0, TODAY, InvoiceType.CASH, BigDecimal.ZERO,
                DiscountType.AMOUNT, new BigDecimal("36.00"), STAMP, CASH_CUSTOMER, "customer", MAIN_TREASURY,
                DELEGATE, false, giftSale, null, List.of(back), 1, null, null, null, 1)).invoiceNumber();
        assertEquals("24.00|" + gift + "|24.00|1.000", text("SELECT CONCAT(discount, '|', offer_id, '|',"
                + " offer_discount, '|', offer_quantity) FROM sales_re WHERE invoice_number = " + returned));
        assertEquals("-36.00", text("SELECT net_revenue FROM document_profit WHERE document_kind = 'sales_return'"
                + " AND document_id = " + returned), "the customer keeps a conditioner paid 24 for");
    }

    @Test
    @Order(13)
    @DisplayName("example 3 and the invoice limit: buy 2 get 1 on five is 30 off; a limit of one group refuses a"
            + " screen claiming two, the counter unmoved")
    void exampleThreeAndTheInvoiceLimit() throws Exception {
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        int biscuits = insertItem("B", 1, 20, 30);
        int twoPlusOne = offers().create(new Offer(0, STAMP + " 2+1", OfferKind.BUY_GET, OfferStatus.ACTIVE, TODAY,
                null, null, 0, null, null, null, null, new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("100"),
                null, null, null, List.of(OfferTarget.item(biscuits)), Set.of(), null));
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales five = line(new Sales(), biscuits, 20, 5, "30", "0");
        offerLines(List.of(five));
        int saved = sales().save(sale(List.of(five), "0")).invoiceNumber();
        assertEquals("30.00|" + twoPlusOne + "|3.000", text("SELECT CONCAT(offer_discount, '|', offer_id, '|',"
                + " offer_quantity) FROM sales WHERE invoice_number = " + saved), "one group: three of the five");

        water = insertItem("W", 1, 25, 40);
        int onceEach = quantityOffer(STAMP + " once", water, "3", "100", "1", null);
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales seven = line(new Sales(), water, 25, 7, "40", "0");
        offerLines(List.of(seven));
        assertEquals(20.0, seven.getDiscount(), "the screen gives one group, not two");
        Sales forged = line(new Sales(), water, 25, 7, "40", "40");
        forged.setOfferId(onceEach);
        forged.setOfferDiscount(new BigDecimal("40.00"));
        forged.setOfferQuantity(new BigDecimal("6"));
        long counter = counter();
        assertThrows(InvoiceValidationException.class, () -> sales().save(sale(List.of(forged), "0")));
        assertEquals(counter, counter(), "refused before the number was taken");
        sales().save(sale(List.of(seven), "0"));
    }

    @Test
    @Order(14)
    @DisplayName("the global limit with two tills: the second waits on the offer's lock, then sees what the first"
            + " took - where a plain read would still see its old snapshot")
    void theGlobalLimitWithTwoTills() throws Exception {
        int firstTwo = quantityOffer(STAMP + " first two", juice, "3", "100", null, "2");
        // Juice already carries example 2's offer; stop it, so this one is what reaches the juice from here.
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_UPDATE);
        int threeFor100 = scalar("SELECT id FROM offer WHERE name = '" + STAMP + " 3 for 100'");
        offers().stop(threeFor100, offers().find(threeFor100).orElseThrow().version());
        Offer limited = offers().find(firstTwo).orElseThrow();
        int host = scalar("SELECT MAX(invoice_number) FROM total_sales");

        ExecutorService tills = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<BigDecimal> first = tills.submit(() -> PriceTierService.Transactions.jdbc().execute(() -> {
                BigDecimal left = offers().timesLeft(List.of(limited), 0, true).get(firstTwo);
                firstLocked.countDown();
                assertTrue(release.await(60, TimeUnit.SECONDS));
                executeHere("INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price, total_sel_price,"
                        + " total_buy_price, total_profit, discount, type_value, offer_id, offer_discount,"
                        + " offer_quantity) VALUES (" + host + ", " + juice + ", 1, 3, 40, 25, 120, 75, 25, 20, 1, "
                        + firstTwo + ", 20, 3)");
                return left;
            }));
            assertTrue(firstLocked.await(60, TimeUnit.SECONDS));
            Future<BigDecimal[]> second = tills.submit(() -> PriceTierService.Transactions.jdbc().execute(() -> {
                // A plain read first, as the save's reads before the offers are: the snapshot is taken here.
                scalarHere("SELECT COUNT(*) FROM sales");
                BigDecimal locked = offers().timesLeft(List.of(limited), 0, true).get(firstTwo);
                BigDecimal plain = offers().timesLeft(List.of(limited), 0, false).get(firstTwo);
                return new BigDecimal[]{locked, plain};
            }));
            Thread.sleep(1500);
            assertFalse(second.isDone(), "the second till waits on the offer's row");
            release.countDown();
            assertEquals(0, new BigDecimal("2").compareTo(first.get(60, TimeUnit.SECONDS)));
            BigDecimal[] seen = second.get(60, TimeUnit.SECONDS);
            assertEquals(0, BigDecimal.ONE.compareTo(seen[0]), "the locking read sees the first till's group");
            assertEquals(0, new BigDecimal("2").compareTo(seen[1]),
                    "a plain read in the same transaction would not: its snapshot is older than the first till's commit");
        } finally {
            tills.shutdownNow();
        }

        // And through the save: one group left, seven juices take one; after it, none is left for anybody.
        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales seven = line(new Sales(), juice, 25, 7, "40", "0");
        offerLines(List.of(seven));
        assertEquals(20.0, seven.getDiscount(), "one group left, however many are on the invoice");
        Sales late = line(new Sales(), juice, 25, 3, "40", "0");
        offerLines(List.of(late));
        sales().save(sale(List.of(seven), "0"));
        long counter = counter();
        assertThrows(InvoiceValidationException.class, () -> sales().save(sale(List.of(late), "0")),
                "the screen saw a group left before the other sale took it");
        assertEquals(counter, counter());
        assertEquals(0, new BigDecimal("6").compareTo(new JdbcOfferRepository().usage(firstTwo).units()),
                "two groups of three, all the limit allows");
    }

    @Test
    @Order(15)
    @DisplayName("an install that ran V85 and sold with an offer upgrades to V86 with each line's covered units")
    void theBackfill() throws Exception {
        String older = SCHEMA_PREFIX + "v85_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + older + "` CHARACTER SET utf8mb4");
        }
        try {
            Flyway.configure().dataSource(jdbcUrl(older), username, password)
                    .locations("filesystem:" + migrationsBeforeV86().toAbsolutePath().toString().replace('\\', '/'))
                    .validateOnMigrate(false).cleanDisabled(true).load().migrate();
            try (Connection connection = DriverManager.getConnection(jdbcUrl(older), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO items (barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2,"
                        + " sel_price3, unit_id, mini_quantity, user_id) VALUES ('BF', 'BF', 1, 25, 40, 0, 0, 1, 0, 1)");
                statement.execute("INSERT INTO offer (name, kind, status, starts_on, percent) VALUES ('ten', 'PERCENT',"
                        + " 'ACTIVE', CURRENT_DATE, 10)");
                statement.execute("INSERT INTO offer (name, kind, status, starts_on, amount, unit_id) VALUES ('carton',"
                        + " 'AMOUNT', 'ACTIVE', CURRENT_DATE, 5, 2)");
                statement.execute("INSERT INTO total_sales (invoice_number, sup_code, invoice_date, total, discount,"
                        + " paid_up, delegate_id) VALUES (1, 1, CURRENT_DATE, 0, 0, 0, 1)");
                statement.execute("INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price, type_value,"
                        + " discount, offer_id, offer_discount) SELECT 1, id, 2, 2, 480, 300, 12, 96,"
                        + " (SELECT id FROM offer WHERE name = 'ten'), 96 FROM items WHERE barcode = 'BF'");
                statement.execute("INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price, type_value,"
                        + " discount, offer_id, offer_discount) SELECT 1, id, 2, 3, 480, 300, 12, 15,"
                        + " (SELECT id FROM offer WHERE name = 'carton'), 15 FROM items WHERE barcode = 'BF'");
            }
            Flyway.configure().dataSource(jdbcUrl(older), username, password).locations("classpath:db/migration")
                    .validateOnMigrate(false).cleanDisabled(true).load().migrate();
            try (Connection connection = DriverManager.getConnection(jdbcUrl(older), username, password);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT o.name, s.offer_quantity FROM sales s"
                         + " JOIN offer o ON o.id = s.offer_id ORDER BY s.id")) {
                assertTrue(rows.next());
                assertEquals("ten", rows.getString(1));
                assertEquals(0, new BigDecimal("24").compareTo(rows.getBigDecimal(2)),
                        "a percentage counts base units: two cartons of twelve");
                assertTrue(rows.next());
                assertEquals(0, new BigDecimal("3").compareTo(rows.getBigDecimal(2)),
                        "an offer written for the carton counts cartons");
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + older + "`");
            }
        }
    }

    /**
     * Every migration before V86 and {@code R__triggers.sql} cut at its V86 section - a trigger naming a column
     * V86 adds cannot be built before it - and no views, which name them too; the upgrade builds them all.
     */
    private static Path migrationsBeforeV86() throws Exception {
        return migrationsBefore(86, "-- offers, quantity and gifts (V86)");
    }

    /**
     * Every migration before {@code version} and {@code R__triggers.sql} cut at {@code triggerMarker} - a trigger
     * naming a column the version adds cannot be built before it - and no views, which name them too.
     */
    private static Path migrationsBefore(int version, String triggerMarker) throws Exception {
        Path source = Paths.get(OfferDatabaseAcceptanceTest.class.getResource("/db/migration").toURI());
        Path target = Files.createTempDirectory("offer-migrations-v" + (version - 1) + "-");
        target.toFile().deleteOnExit();
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if ((name.matches("V(\\d+)__.*")
                        && Integer.parseInt(name.substring(1, name.indexOf("__"))) >= version)
                        || name.equals("R__views.sql")) {
                    continue;
                }
                String sql = Files.readString(file, StandardCharsets.UTF_8);
                if (name.equals("R__triggers.sql")) {
                    int cut = sql.indexOf(triggerMarker);
                    assertTrue(cut > 0, "the section of R__triggers.sql this test cuts at is where it expects it");
                    sql = sql.substring(0, cut);
                }
                Files.writeString(target.resolve(name), sql, StandardCharsets.UTF_8);
                target.resolve(name).toFile().deleteOnExit();
            }
        }
        return target;
    }

    // --- phase D: the bundle and the invoice's total (V87) ------------------------------------------------

    /** Digits alone, as the invoice's barcode box takes; unique to this run. */
    private static final String BUNDLE_CODE = "62" + (System.nanoTime() % 1_000_000_000L);
    private static final String CLASH_CODE = "63" + (System.nanoTime() % 1_000_000_000L);

    private static int ramadan;
    private static int fivePercent;
    private static int ramadanSale;
    private static int invoiceSale;
    private static int oilItem;

    @Test
    @Order(16)
    @DisplayName("V87 from nothing: the threshold, the barcode and the component's quantity, and every CHECK")
    void thePhaseDSchema() throws Exception {
        for (String column : List.of("threshold", "barcode")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = 'offer' AND COLUMN_NAME = '" + column + "'"), column);
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = 'offer_target' AND COLUMN_NAME = 'quantity'"));
        // A bundle with no price; an invoice offer with a percentage and an amount, or a limit per invoice; a
        // barcode on anything but a bundle: the rewritten CHECKs.
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on) VALUES ('d1', 'BUNDLE', 'DRAFT',"
                + " CURRENT_DATE)");
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, threshold, percent, amount) VALUES"
                + " ('d2', 'INVOICE', 'DRAFT', CURRENT_DATE, 1000, 5, 50)");
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, threshold, percent, max_per_invoice)"
                + " VALUES ('d3', 'INVOICE', 'DRAFT', CURRENT_DATE, 1000, 5, 1)");
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, percent, barcode) VALUES ('d4',"
                + " 'PERCENT', 'DRAFT', CURRENT_DATE, 10, '123')");
        assertSqlRefused(3819, "INSERT INTO offer (name, kind, status, starts_on, percent, threshold) VALUES ('d5',"
                + " 'PERCENT', 'DRAFT', CURRENT_DATE, 10, 100)");
        // A component with no quantity, and a quantity on a target that earns: the role's CHECK.
        assertSqlRefused(3819, "INSERT INTO offer_target (offer_id, role, scope, item_id) VALUES (" + tenPercent
                + ", 'COMPONENT', 'ITEM', " + soap + ")");
        assertSqlRefused(3819, "INSERT INTO offer_target (offer_id, role, scope, item_id, quantity) VALUES ("
                + tenPercent + ", 'QUALIFY', 'ITEM', " + soap + ", 2)");
    }

    @Test
    @Order(17)
    @DisplayName("example 5: the Ramadan bundle at 150 - 7.28, 5.45 and 2.27 on its lines, document_profit 150 - 132;"
            + " its barcode nobody else's, and a sale without the sugar refused")
    void exampleFive() throws Exception {
        int oil = insertItem("OIL", 1, 70, 80);
        int sugar = insertItem("SUG", 1, 20, 30);
        int bran = insertItem("RIC", 1, 22, 25);
        execute("INSERT INTO items(barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2, sel_price3,"
                + " unit_id, mini_quantity, user_id) VALUES ('" + CLASH_CODE + "', '" + STAMP + " clash', 1, 1, 2, 0,"
                + " 0, 1, 0, 1)");
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        List<OfferTarget> components = List.of(OfferTarget.component(oil, null, BigDecimal.ONE),
                OfferTarget.component(sugar, null, new BigDecimal("2")), OfferTarget.component(bran, null, BigDecimal.ONE));
        java.util.function.Function<String, Offer> bundle = code -> new Offer(0, STAMP + " ramadan " + code,
                OfferKind.BUNDLE, OfferStatus.ACTIVE, TODAY, null, null, 0, null, null, new BigDecimal("150"), null,
                null, null, null, null, null, null, code, null, components, Set.of(), null);
        UserValidationException itemsCode = assertThrows(UserValidationException.class,
                () -> offers().create(bundle.apply(CLASH_CODE)));
        assertTrue(itemsCode.getMessage().contains(STAMP + " clash"), itemsCode.getMessage());

        ramadan = offers().create(bundle.apply(BUNDLE_CODE));
        assertEquals("BUNDLE|150.00|" + BUNDLE_CODE, text("SELECT CONCAT(kind, '|', offer_price, '|', barcode)"
                + " FROM offer WHERE id = " + ramadan));
        assertEquals("COMPONENT|2.000", text("SELECT CONCAT(role, '|', quantity) FROM offer_target WHERE offer_id = "
                + ramadan + " AND item_id = " + sugar));
        Offer again = new Offer(0, STAMP + " ramadan twice", OfferKind.BUNDLE, OfferStatus.ACTIVE, TODAY, null, null,
                0, null, null, new BigDecimal("150"), null, null, null, null, null, null, null, BUNDLE_CODE, null,
                components, Set.of(), null);
        assertEquals("offer.error.barcode.duplicate",
                assertThrows(UserValidationException.class, () -> offers().create(again)).getMessage());
        // And the item screen's own checks see it: the till tries a bundle's code before an item's.
        assertEquals(BUNDLE_CODE, DaoFactory.INSTANCE.getItemsDao()
                .firstBarcodeTakenByAnotherItem(List.of("none-" + STAMP, BUNDLE_CODE), 0));
        assertEquals(STAMP + " ramadan " + BUNDLE_CODE, DaoFactory.INSTANCE.getItemsDao()
                .itemNameHoldingBarcode(BUNDLE_CODE, 0));
        assertTrue(DaoFactory.INSTANCE.getItemsDao().takenBarcodesAmong(List.of(BUNDLE_CODE), 0).contains(BUNDLE_CODE));

        // Scanned, it is the bundle, and its components are the lines it puts on the invoice.
        Offer scanned = com.hamza.account.features.invoice.InvoiceBundleEntry.find(offers().inForce(), BUNDLE_CODE)
                .orElseThrow();
        assertEquals(List.of(oil, sugar, bran), com.hamza.account.features.invoice.InvoiceBundleEntry
                .requests(scanned).stream().map(com.hamza.account.features.invoice.ItemPickRequest::itemId).toList());

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales oilLine = line(new Sales(), oil, 70, 1, "80", "0");
        Sales sugarLine = line(new Sales(), sugar, 20, 2, "30", "0");
        Sales riceLine = line(new Sales(), bran, 22, 1, "25", "0");
        offerLines(List.of(oilLine, sugarLine, riceLine));
        int saved = sales().save(sale(List.of(oilLine, sugarLine, riceLine), "0")).invoiceNumber();
        ramadanSale = saved;
        oilItem = oil;

        assertEquals("7.28|1.000", lineOf(saved, oil), "80 / 165 x 15, and the piastre left on the largest");
        assertEquals("5.45|2.000", lineOf(saved, sugar));
        assertEquals("2.27|1.000", lineOf(saved, bran));
        assertEquals("150.00", text("SELECT total FROM total_sales WHERE invoice_number = " + saved),
                "the bundle's price, the lines after their shares");
        assertEquals("150.00|132.00|18.00", text("SELECT CONCAT(net_revenue, '|', cost_of_sales, '|', profit)"
                + " FROM document_profit WHERE document_kind = 'sales' AND document_id = " + saved),
                "each component carries its own cost: 70 + 2 x 20 + 22");

        // Oil and rice claiming the bundle with the sugar gone: nothing the offers give, refused unnumbered.
        Sales forgedOil = line(new Sales(), oil, 70, 1, "80", "7.28");
        forgedOil.setOfferId(ramadan);
        forgedOil.setOfferDiscount(new BigDecimal("7.28"));
        Sales plainRice = line(new Sales(), bran, 22, 1, "25", "0");
        long counter = counter();
        assertThrows(InvoiceValidationException.class, () -> sales().save(sale(List.of(forgedOil, plainRice), "0")));
        assertEquals(counter, counter());
    }

    @Test
    @Order(18)
    @DisplayName("example 6: 5% from 1,000 - 20 and 40 by value, one time shared 0.333 and 0.667; the first invoice"
            + " only, then nothing; and its threshold a term once used")
    void exampleSix() throws Exception {
        int cups = insertItem("CUP", 1, 25, 40);
        int pans = insertItem("PAN", 1, 60, 100);
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        fivePercent = offers().create(new Offer(0, STAMP + " 5% from 1000", OfferKind.INVOICE, OfferStatus.ACTIVE,
                TODAY, null, null, 0, new BigDecimal("5"), null, null, null, null, null, null, null, BigDecimal.ONE,
                new BigDecimal("1000"), null, null, List.of(OfferTarget.everything()), Set.of(), null));

        signIn(AppPermissions.SALES_CREATE, AppPermissions.ITEMS_SHOW);
        Sales cupLine = line(new Sales(), cups, 25, 10, "40", "0");
        Sales panLine = line(new Sales(), pans, 60, 8, "100", "0");
        offerLines(List.of(cupLine, panLine));
        int saved = sales().save(sale(List.of(cupLine, panLine), "0")).invoiceNumber();
        invoiceSale = saved;

        assertEquals("20.00|0.333", lineOf(saved, cups), "400 / 1,200 of 60");
        assertEquals("40.00|0.667", lineOf(saved, pans), "the shares come to one invoice");
        assertEquals("1140.00|0.00", text("SELECT CONCAT(total, '|', discount) FROM total_sales"
                + " WHERE invoice_number = " + saved), "on the lines, never in the invoice's own discount box");
        assertEquals("1140.00|730.00|410.00", text("SELECT CONCAT(net_revenue, '|', cost_of_sales, '|', profit)"
                + " FROM document_profit WHERE document_kind = 'sales' AND document_id = " + saved));
        assertEquals(0, BigDecimal.ONE.compareTo(new JdbcOfferRepository().usage(fivePercent).units()),
                "one invoice counted against its limit of one");

        // The limit was one invoice: the next over 1,000 is given nothing, and a screen claiming it is refused.
        Sales again = line(new Sales(), pans, 60, 12, "100", "0");
        offerLines(List.of(again));
        assertEquals(0.0, again.getDiscount());
        Sales forged = line(new Sales(), pans, 60, 12, "100", "60");
        forged.setOfferId(fivePercent);
        forged.setOfferDiscount(new BigDecimal("60.00"));
        long counter = counter();
        assertThrows(InvoiceValidationException.class, () -> sales().save(sale(List.of(forged), "0")));
        assertEquals(counter, counter());

        // Once used, its threshold is history: the trigger refuses it as it refuses a percentage.
        assertSqlRefused(1644, "UPDATE offer SET threshold = 500 WHERE id = " + fivePercent);
        execute("UPDATE offer SET name = '" + STAMP + " renamed' WHERE id = " + fivePercent);
    }

    @Test
    @Order(19)
    @DisplayName("an install at V86 with offers of every earlier kind upgrades to V87 with its rows untouched")
    void theUpgradeToV87() throws Exception {
        String older = SCHEMA_PREFIX + "v86_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + older + "` CHARACTER SET utf8mb4");
        }
        try {
            Flyway.configure().dataSource(jdbcUrl(older), username, password)
                    .locations("filesystem:" + migrationsBefore(87, "-- offers, bundles and the invoice's total (V87)")
                            .toAbsolutePath().toString().replace('\\', '/'))
                    .validateOnMigrate(false).cleanDisabled(true).load().migrate();
            try (Connection connection = DriverManager.getConnection(jdbcUrl(older), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO offer (name, kind, status, starts_on, percent) VALUES ('ten', 'PERCENT',"
                        + " 'ACTIVE', CURRENT_DATE, 10)");
                statement.execute("INSERT INTO offer (name, kind, status, starts_on, buy_quantity, get_quantity,"
                        + " get_percent) VALUES ('gift', 'BUY_GET', 'ACTIVE', CURRENT_DATE, 1, 1, 100)");
                statement.execute("INSERT INTO offer_target (offer_id, role, scope) SELECT id, 'QUALIFY', 'ALL'"
                        + " FROM offer WHERE name = 'ten'");
                statement.execute("INSERT INTO items (barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2,"
                        + " sel_price3, unit_id, mini_quantity, user_id) VALUES ('UP', 'UP', 1, 25, 40, 0, 0, 1, 0, 1)");
                statement.execute("INSERT INTO offer_target (offer_id, role, scope, item_id) SELECT o.id, 'REWARD',"
                        + " 'ITEM', i.id FROM offer o JOIN items i ON i.barcode = 'UP' WHERE o.name = 'gift'");
            }
            Flyway.configure().dataSource(jdbcUrl(older), username, password).locations("classpath:db/migration")
                    .validateOnMigrate(false).cleanDisabled(true).load().migrate();
            try (Connection connection = DriverManager.getConnection(jdbcUrl(older), username, password);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT o.name, o.threshold, o.barcode, t.role, t.quantity"
                         + " FROM offer o JOIN offer_target t ON t.offer_id = o.id ORDER BY o.id")) {
                assertTrue(rows.next());
                assertEquals("ten", rows.getString(1));
                assertEquals(null, rows.getObject(2));
                assertEquals(null, rows.getObject(3));
                assertTrue(rows.next());
                assertEquals("REWARD", rows.getString(4), "the rewritten role CHECK keeps the gift");
                assertEquals(null, rows.getObject(5));
                assertFalse(rows.next());
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + older + "`");
            }
        }
    }

    // --- phase E: what the offers did ----------------------------------------------------------------------

    @Test
    @Order(20)
    @DisplayName("the performance report: an offer's lines as document_profit says for the same invoices, a return"
            + " giving back its share, the cost only for the profit key, the period before empty")
    void thePerformanceReport() throws Exception {
        com.hamza.account.features.profitloss.statement.ProfitLossPeriod today =
                new com.hamza.account.features.profitloss.statement.ProfitLossPeriod(TODAY, TODAY);
        signIn(AppPermissions.OFFER_SHOW);
        assertThrows(BusinessRuleException.class, () -> offers().performance(today), "a sales report's key too");

        signIn(AppPermissions.OFFER_SHOW, AppPermissions.REPORTS_SHOW_SALES, AppPermissions.REPORTS_SHOW_PROFIT);
        OfferPerformanceReport report = offers().performance(today);
        OfferPerformanceRow bundleRow = rowOf(report, ramadan);
        assertEquals(1, bundleRow.now().invoices());
        assertEquals("15.00|150.00|132.00", figures(bundleRow.now()));
        assertEquals(text("SELECT CONCAT(discount_total, '|', net_revenue, '|', cost_of_sales) FROM (SELECT 15.00 AS"
                + " discount_total, net_revenue, cost_of_sales FROM document_profit WHERE document_kind = 'sales'"
                + " AND document_id = " + ramadanSale + ") p"), figures(bundleRow.now()),
                "the bundle's lines are the whole invoice: document_profit says the same");
        assertEquals(0, BigDecimal.ONE.compareTo(bundleRow.times()), "one bundle: four units over four");

        OfferPerformanceRow invoiceRow = rowOf(report, fivePercent);
        assertEquals("60.00|1140.00|730.00", figures(invoiceRow.now()));
        assertEquals(text("SELECT CONCAT('60.00|', net_revenue, '|', cost_of_sales) FROM document_profit"
                + " WHERE document_kind = 'sales' AND document_id = " + invoiceSale), figures(invoiceRow.now()));

        // Example 4's gift: 24 and 16 given, the shampoo back with its 24 - so 16 stayed given, and the net and
        // the cost are the conditioner's alone.
        int gift = scalar("SELECT id FROM offer WHERE name = '" + STAMP + " gift'");
        OfferPerformanceRow giftRow = rowOf(report, gift);
        assertEquals("16.00|24.00|30.00", figures(giftRow.now()));
        assertEquals(0, new BigDecimal("0.5").compareTo(giftRow.times()), "two units covered, one given back");
        assertTrue(report.rows().stream().allMatch(row -> row.before().lines() == 0),
                "yesterday, before the schema existed, gave nothing");
        assertEquals(scalar("SELECT COUNT(DISTINCT invoice_number) FROM sales WHERE offer_id IS NOT NULL"),
                report.invoices(), "each invoice once, however many offers it held");

        List<OfferPerformanceItem> items = offers().performanceItems(ramadan, today);
        assertEquals(3, items.size());
        assertEquals(0, new BigDecimal("150.00").compareTo(items.stream().map(OfferPerformanceItem::net)
                .reduce(BigDecimal.ZERO, BigDecimal::add)), "the drawer's items add up to the row");

        signIn(AppPermissions.OFFER_SHOW, AppPermissions.REPORTS_SHOW_SALES);
        OfferPerformanceReport withoutCost = offers().performance(today);
        assertTrue(withoutCost.profit().isEmpty());
        assertEquals(null, rowOf(withoutCost, ramadan).now().cost(), "never read for a reader who may not see it");
    }

    @Test
    @Order(21)
    @DisplayName("the reminders on MySQL: an item on offer down to its minimum, by the items list's own balance, and"
            + " an offer ending tomorrow")
    void theReminders() throws Exception {
        signIn(AppPermissions.OFFER_SHOW, AppPermissions.OFFER_CREATE);
        assertTrue(offers().shortOfStock(TODAY).stream().noneMatch(item -> item.item().itemId() == oilItem));
        execute("UPDATE items SET mini_quantity = 200 WHERE id = " + oilItem);
        OfferAlerts.ShortItem oil = offers().shortOfStock(TODAY).stream()
                .filter(item -> item.item().itemId() == oilItem).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("99").compareTo(oil.item().balance()), "100 opening, one sold in the bundle");
        assertEquals(List.of(ramadan), oil.offers().stream().map(Offer::id).toList());

        int lastDay = offers().create(new Offer(0, STAMP + " last day", OfferKind.PERCENT, OfferStatus.ACTIVE, TODAY,
                TODAY.plusDays(1), null, 0, BigDecimal.ONE, null, null, null, null,
                List.of(OfferTarget.item(oilItem)), Set.of(), null));
        OfferAlerts.Ending ending = offers().endingSoon(TODAY).stream()
                .filter(end -> end.offer().id() == lastDay).findFirst().orElseThrow();
        assertEquals(1, ending.daysLeft());
    }

    private static OfferPerformanceRow rowOf(OfferPerformanceReport report, int offerId) {
        return report.rows().stream().filter(row -> row.offer().id() == offerId).findFirst().orElseThrow();
    }

    private static String figures(OfferFigures figures) {
        return figures.discount().setScale(2, java.math.RoundingMode.HALF_UP) + "|"
                + figures.net().setScale(2, java.math.RoundingMode.HALF_UP) + "|"
                + figures.cost().setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** A line of a saved sale: its offer's discount and the units, or the share of a time, it covered. */
    private static String lineOf(int invoice, int item) throws Exception {
        return text("SELECT CONCAT(offer_discount, '|', offer_quantity) FROM sales WHERE invoice_number = " + invoice
                + " AND num = " + item);
    }

    // --- the screen's own path -------------------------------------------------------------------------------

    /**
     * The offers the till would apply, written on the lines as the invoice screen writes them - what a limit has
     * left read the way the screen reads it, plainly.
     */
    private static void offerLines(List<? extends BasePurchasesAndSales> lines) throws Exception {
        InvoiceOfferPreview preview = new InvoiceOfferPreview(new InvoiceOffers.JdbcGroups(),
                (limited, exceptInvoice) -> offers().timesLeft(limited, exceptInvoice, false));
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

    /** {@link #execute} inside a transaction open on this thread: its connection is borrowed, never closed. */
    private static void executeHere(String sql) throws Exception {
        Connection connection = ConnectionManager.acquire();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private static int scalarHere(String sql) throws Exception {
        Connection connection = ConnectionManager.acquire();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            return rows.getInt(1);
        } finally {
            ConnectionManager.release(connection);
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
