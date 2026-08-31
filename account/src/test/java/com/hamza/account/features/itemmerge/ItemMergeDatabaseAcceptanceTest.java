package com.hamza.account.features.itemmerge;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one test that proves what the merge is for, against a real MySQL.
 * <p>
 * Everything else in this package is checked without a database - the declarations, the
 * statement text, the rules - and none of it can answer the question the feature exists
 * to answer: after merging, does the surviving item hold the whole of both histories, and
 * does its stock balance come out as the sum? That is a question about views
 * ({@code quantity_items_table}, {@code card_item_view}), about multi-table {@code UPDATE
 * ... JOIN} statements MySQL either accepts or does not, and about a cascade doing what
 * the ordering assumes. No amount of pinned SQL says any of it.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}, in the manner of
 * {@code PartyLedgerViewAcceptanceTest}: everything runs inside one transaction which is
 * always rolled back, so a developer's database is left exactly as it was - the merge's
 * own {@link com.hamza.controlsfx.database.TransactionTemplate} joins the open
 * transaction rather than committing inside it.
 * <p>
 * Run it after touching anything in this package, and before shipping a release that
 * carries the feature.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ItemMergeDatabaseAcceptanceTest {

    private static final int STOCK = 1;
    private static final int SUB_GROUP = 1;
    private static final int PIECE = 1;
    private static final int CARTON = 2;

    /** Distinct on purpose: a balance landing on the right number by accident has to be impossible. */
    private static final double SOURCE_OPENING = 5;
    private static final double TARGET_OPENING = 10;
    private static final double PURCHASED = 12;
    private static final double SOLD = 3;

    private static final ItemMergeService SERVICE = new ItemMergeService(DaoFactory.INSTANCE);

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
        HashMap<String, String> config = new CryptoDatabaseConfig(
                CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));

        // The merge is guarded, and DeletionService reads the same session. User 1 is the
        // system administrator, which is what a shop owner running this is.
        UserSessionContext session = new UserSessionContext();
        session.signIn(1, "admin", List.of(AppPermissions.ITEMS_MERGE, AppPermissions.ITEMS_DELETE));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    // ------------------------------------------------------------------
    // The tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the target inherits every line, and its balance is the sum of both")
    void historyMovesAndTheBalanceIsTheSum() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            assertEquals(SOURCE_OPENING + PURCHASED - SOLD, balanceOf(connection, fixture.source()), 0.001,
                    "the fixture itself is wrong before anything is merged");
            assertEquals(TARGET_OPENING, balanceOf(connection, fixture.target()), 0.001);
            assertEquals(2, cardLines(connection, fixture.source()));

            ItemMergeResult result = SERVICE.merge(fixture.source(), fixture.target());

            assertEquals(0, cardLines(connection, fixture.source()), "lines were left on the deleted item");
            assertEquals(2, cardLines(connection, fixture.target()), "the target did not inherit both lines");
            assertEquals(SOURCE_OPENING + TARGET_OPENING + PURCHASED - SOLD,
                    balanceOf(connection, fixture.target()), 0.001,
                    "the surviving balance is not the sum of the two");
            assertFalse(exists(connection, "SELECT 1 FROM items WHERE id = ?", fixture.source()),
                    "the source item is still there");
            assertEquals(2, result.preview().documentLines());
        });
    }

    @Test
    @DisplayName("every code the source answered to still finds the target")
    void barcodesAndUnitsSurvive() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            SERVICE.merge(fixture.source(), fixture.target());

            assertTrue(barcodeOf(connection, fixture.target(), fixture.sourceBarcode()),
                    "the code printed on the old packet no longer finds anything");
            assertTrue(barcodeOf(connection, fixture.target(), fixture.sourceExtraBarcode()),
                    "an extra barcode of the source was lost");
            assertTrue(exists(connection,
                            "SELECT 1 FROM items_units WHERE items_id = ? AND unit = ?", fixture.target(), CARTON),
                    "the source's carton unit did not move, so the target cannot be sold by the carton");
        });
    }

    @Test
    @DisplayName("the log says what moved, per table")
    void theLogRecordsWhatMoved() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);

            ItemMergeResult result = SERVICE.merge(fixture.source(), fixture.target());

            assertTrue(result.mergeId() > 0, "no log row was written");
            assertEquals(1, countOf(connection,
                    "SELECT COUNT(*) FROM item_merge WHERE id = ? AND source_item_id = ? AND target_item_id = ?",
                    result.mergeId(), fixture.source(), fixture.target()));
            assertEquals(1, countOf(connection,
                    "SELECT rows_moved FROM item_merge_lines WHERE merge_id = ? AND table_name = 'sales.num'",
                    result.mergeId()));
            assertEquals(1, countOf(connection,
                    "SELECT rows_moved FROM item_merge_lines WHERE merge_id = ? AND table_name = 'purchase.num'",
                    result.mergeId()));
        });
    }

    /**
     * The refusal, and - the part worth a database - that nothing moved on the way to it.
     * A merge that validated after its first {@code UPDATE} would leave the sale on the
     * target and the item still standing.
     */
    @Test
    @DisplayName("a different base unit is refused and nothing has moved")
    void aDifferentBaseUnitIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = fixture(connection);
            int cartonItem = insertItem(connection, "دمج-كرتونة-" + fixture.stamp(),
                    "MRG-C-" + fixture.stamp(), CARTON, 0, false);

            assertThrows(BusinessRuleException.class, () -> SERVICE.merge(fixture.source(), cartonItem));

            assertEquals(2, cardLines(connection, fixture.source()), "lines moved before the rule was applied");
            assertTrue(exists(connection, "SELECT 1 FROM items WHERE id = ?", fixture.source()));
        });
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    /** Two items of the same base unit, the source carrying a purchase, a sale, a spare barcode and a unit. */
    private record Fixture(int source, int target, String sourceBarcode, String sourceExtraBarcode, long stamp) {
    }

    private Fixture fixture(Connection connection) throws Exception {
        long stamp = System.nanoTime() % 1_000_000_000L;
        String sourceBarcode = "MRG-S-" + stamp;
        String extraBarcode = "MRG-X-" + stamp;

        int source = insertItem(connection, "دمج-مصدر-" + stamp, sourceBarcode, PIECE, SOURCE_OPENING, false);
        int target = insertItem(connection, "دمج-هدف-" + stamp, "MRG-T-" + stamp, PIECE, TARGET_OPENING, false);

        execute(connection, "INSERT INTO item_barcodes (item_id, barcode) VALUES (?, ?)", source, extraBarcode);
        execute(connection, """
                INSERT INTO items_units (items_id, items_barcode, unit, quantity, buy_price, sel_price, user_id)
                VALUES (?, ?, ?, ?, ?, ?, 1)""", source, "MRG-U-" + stamp, CARTON, 12, 0, 0);

        int purchase = insertHeader(connection, DocumentType.PURCHASE);
        execute(connection, """
                INSERT INTO purchase (invoice_number, num, type, quantity, price, type_value)
                VALUES (?, ?, ?, ?, ?, 1)""", purchase, source, PIECE, PURCHASED, 10);

        int sale = insertHeader(connection, DocumentType.SALES);
        execute(connection, """
                INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price, type_value)
                VALUES (?, ?, ?, ?, ?, ?, 1)""", sale, source, PIECE, SOLD, 15, 10);

        return new Fixture(source, target, sourceBarcode, extraBarcode, stamp);
    }

    private int insertItem(Connection connection, String name, String barcode,
                           int unitId, double firstBalance, boolean validity) throws Exception {
        String sql = """
                INSERT INTO items (barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2, sel_price3,
                                   unit_id, mini_quantity, first_balance, item_has_validity, user_id)
                VALUES (?, ?, ?, 10, 15, 15, 15, ?, 0, ?, ?, 1)""";
        int id;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, barcode);
            statement.setString(2, name);
            statement.setInt(3, SUB_GROUP);
            statement.setInt(4, unitId);
            statement.setDouble(5, firstBalance);
            statement.setBoolean(6, validity);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                id = keys.getInt(1);
            }
        }
        // quantity_items_table is driven by items_stock, so an item with no row there has
        // no balance to read at all - which would make every assertion here pass vacuously.
        //
        // The opening balance goes in here as well as on the item, and the two have to
        // agree. This row used to be written with a hard-coded 0, which was correct when
        // the test was written on 2026-08-20: the view read `items.first_balance` then, so
        // the item's own column was the opening. fbadd53 (2026-08-28, multi-warehouse)
        // moved the view onto `items_stock.first_balance` per warehouse and left
        // `items.first_balance` as a compatibility mirror of warehouse 1 - and from that
        // day this fixture claimed an opening of 5 while the view read 0. Nobody saw it,
        // because the class is gated and was last run on 2026-08-25, three days before.
        execute(connection, """
                INSERT INTO items_stock (item_id, stock_id, first_balance, current_quantity)
                VALUES (?, ?, ?, 0)""", id, STOCK, firstBalance);
        return id;
    }

    /** Written through the document spec, so a column added to one reaches this test. */
    private int insertHeader(Connection connection, DocumentType type) throws Exception {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        int id = nextId(connection, spec.table(), spec.key());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(spec.key(), id);
        values.put(spec.party(), 1);
        values.put(spec.paid(), 0);
        values.put("invoice_type", 1);
        values.put("invoice_date", java.sql.Date.valueOf(LocalDate.now()));
        values.put("total", 100);
        values.put("discount", 0);
        values.put("stock_id", STOCK);
        values.put("delegate_id", 1);
        values.put("treasury_id", 1);
        values.put("notes", "item-merge-acceptance");
        values.put("user_id", 1);

        try (PreparedStatement statement = connection.prepareStatement(spec.insertSql())) {
            int index = 1;
            for (String column : spec.insertColumns()) {
                Object value = values.get(column);
                assertNotNull(value, "No test value for column " + column + " of " + spec.table());
                statement.setObject(index++, value);
            }
            assertEquals(1, statement.executeUpdate());
        }
        return id;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /** The balance every screen shows, worked out from the view rather than from the fixture. */
    private double balanceOf(Connection connection, int itemId) throws Exception {
        String sql = """
                SELECT first_balance, quantityPurchase, quantitySales, quantityPurchaseRe,
                       quantitySalesRe, fromStock, toStock, adjustment
                FROM quantity_items_table
                WHERE item_id = ? AND stock_id = ?""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, STOCK);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return 0;
                }
                return rows.getDouble("first_balance")
                       + rows.getDouble("quantityPurchase")
                       - rows.getDouble("quantitySales")
                       - rows.getDouble("quantityPurchaseRe")
                       + rows.getDouble("quantitySalesRe")
                       + rows.getDouble("toStock")
                       - rows.getDouble("fromStock")
                       + rows.getDouble("adjustment");
            }
        }
    }

    /** What the item card would list - the user's own definition of "the operations on this item". */
    private int cardLines(Connection connection, int itemId) throws Exception {
        return countOf(connection, "SELECT COUNT(*) FROM card_item_view WHERE item_num = ?", itemId);
    }

    /** Whether the target answers to a code, through any of the three barcode tables. */
    private boolean barcodeOf(Connection connection, int itemId, String barcode) throws Exception {
        return countOf(connection, """
                SELECT (SELECT COUNT(*) FROM items WHERE id = ? AND barcode = ?)
                     + (SELECT COUNT(*) FROM item_barcodes WHERE item_id = ? AND barcode = ?)
                     + (SELECT COUNT(*) FROM items_units WHERE items_id = ? AND items_barcode = ?)""",
                itemId, barcode, itemId, barcode, itemId, barcode) > 0;
    }

    private boolean exists(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private int countOf(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    private static int nextId(Connection connection, String table, String key) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(" + key + "),0)+3000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private void execute(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws Exception {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    /**
     * One transaction, always rolled back. The merge's own transaction joins this one -
     * {@code ConnectionManager} binds it to the thread - so nothing it writes is ever
     * committed, and the assertions still see every row it wrote.
     */
    private void inTransaction(Work work) throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction, "no transaction was opened; another one is already running on this thread");
        try {
            work.run(transaction);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @FunctionalInterface
    private interface Work {
        void run(Connection connection) throws Exception;
    }
}
