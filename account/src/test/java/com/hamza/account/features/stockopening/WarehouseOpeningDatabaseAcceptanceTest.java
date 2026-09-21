package com.hamza.account.features.stockopening;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.features.stockcount.StockCountService;
import com.hamza.account.features.stocktransfer.StockTransferCommand;
import com.hamza.account.features.stocktransfer.StockTransferLine;
import com.hamza.account.features.stocktransfer.StockTransferService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Users;
import com.hamza.account.service.ItemsService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An opening balance per warehouse (V78), against a real MySQL, through the services the screens call.
 * <p>
 * What only a database can say: that "has this item moved in this warehouse" - one condition built
 * from the balance's own terms, read through the document views - is true after a sale, a transfer
 * and a draft count in <em>that</em> warehouse and not after one in another; that the save refuses
 * what it must inside its transaction; that an edit of the item no longer rewrites warehouse 1's
 * opening, which the dropped trigger did on every update; and that V78 left one column and no
 * trigger behind.
 * <p>
 * Signed in as an ordinary user: user 1 bypasses every permission. Opt in with
 * {@code -Daccount.db.acceptance=true}; one rolled-back transaction per case, and {@code @AfterAll}
 * queries for this class's {@code WOB-} stamp rather than trusting the rollback. The config is
 * {@code ACCOUNT_DB_ACCEPTANCE_CONFIG} when set, else {@code config.xml} beside the module.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class WarehouseOpeningDatabaseAcceptanceTest {

    private static final int USER = 1;
    private static final int OPERATOR = 4246;

    private static WarehouseOpeningService openings;
    private static StockTransferService transfers;
    private static StockCountService counts;
    private static ItemsService items;

    @BeforeAll
    static void connect() throws Exception {
        String named = System.getenv("ACCOUNT_DB_ACCEPTANCE_CONFIG");
        File configFile = named != null && !named.isBlank() ? new File(named) : new File("config.xml");
        if (!configFile.isFile()) {
            configFile = new File("../config.xml");
        }
        HashMap<String, String> config = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey())
                .loadAndDecryptConfig(configFile.getAbsolutePath());
        DataSourceProvider.initialize(
                config.get(CryptoDatabaseConfig.HOST),
                config.get(CryptoDatabaseConfig.PORT),
                config.get(CryptoDatabaseConfig.DBNAME),
                config.get(CryptoDatabaseConfig.USERNAME),
                config.get(CryptoDatabaseConfig.PASSWORD));
        signIn(AppPermissions.STOCK_SHOW, AppPermissions.ITEMS_UPDATE, AppPermissions.STOCK_TRANSFER_POST,
                AppPermissions.STOCK_COUNT_SHOW, AppPermissions.STOCK_COUNT_CREATE);
        openings = new WarehouseOpeningService(DaoFactory.INSTANCE);
        transfers = new StockTransferService(DaoFactory.INSTANCE);
        counts = new StockCountService(DaoFactory.INSTANCE);
        items = new ItemsService(DaoFactory.INSTANCE);
    }

    @AfterAll
    static void leaveNothingBehind() throws Exception {
        Connection connection = ConnectionManager.acquire();
        try {
            assertNoResidue(connection, "stocks", "stock_name LIKE 'WOB-%'");
            assertNoResidue(connection, "items", "barcode LIKE 'WOB-%'");
            assertNoResidue(connection, "stock_count", "notes = 'WOB-count'");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    @Test
    @DisplayName("V78 leaves one opening column and no trigger copying another over it")
    void theSchemaHasOneOpening() throws Exception {
        inTransaction(connection -> {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = 'items' AND COLUMN_NAME = 'first_balance'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                    + " AND TABLE_NAME = 'items_stock' AND COLUMN_NAME = 'current_quantity'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()"
                    + " AND TRIGGER_NAME = 'after_items_update'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()"
                    + " AND TRIGGER_NAME = 'audit_items_stock_opening'"));
            // The item audit triggers were dropped with the column and must be back, without it.
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()"
                    + " AND TRIGGER_NAME IN ('audit_items_insert', 'audit_items_update', 'audit_items_delete')"));
        });
    }

    /** The case the screen exists for: a warehouse joining a shop, given what is on its shelves. */
    @Test
    @DisplayName("an item nothing has moved in a warehouse takes an opening there, and the balance and the log say so")
    void anOpeningIsEntered() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection, 0);
            WarehouseOpeningRow row = rowOf(fixture.branch(), fixture.itemId());
            assertFalse(row.moved());

            WarehouseOpeningDraft draft = new WarehouseOpeningDraft();
            draft.set(row, 7);
            assertEquals(1, openings.save(fixture.branch(), draft.changes()));

            assertEquals(7.0, balance(connection, fixture.itemId(), fixture.branch()));
            assertEquals(0.0, balance(connection, fixture.itemId(), DefaultStock.ID),
                    "an opening entered for a branch reached the main warehouse");
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT notes, JSON_EXTRACT(old_data, '$.first_balance'), JSON_EXTRACT(new_data, '$.first_balance')
                    FROM audit_log WHERE table_name = 'items_stock' AND action_type = 'UPDATE'
                      AND JSON_EXTRACT(new_data, '$.item_id') = ? AND JSON_EXTRACT(new_data, '$.stock_id') = ?
                    ORDER BY id DESC LIMIT 1""")) {
                statement.setInt(1, fixture.itemId());
                statement.setInt(2, fixture.branch());
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "entering an opening left no audit row");
                    assertEquals("Opening balance changed", rows.getString(1));
                    assertEquals(0.0, rows.getDouble(2));
                    assertEquals(7.0, rows.getDouble(3));
                }
            }
        });
    }

    /** Each kind of line locks the warehouse it moved in - and only that one. */
    @Test
    @DisplayName("a sale, a transfer and a draft count each close the opening of the warehouse they moved in, not another")
    void eachMovementClosesItsOwnWarehouse() throws Exception {
        inTransaction(connection -> {
            Fixture sold = seed(connection, 0);
            insertSale(connection, sold.itemId(), sold.branch());
            assertTrue(rowOf(sold.branch(), sold.itemId()).moved(), "a sale in the branch did not close its opening");
            assertFalse(rowOf(DefaultStock.ID, sold.itemId()).moved(), "a sale in the branch closed the main warehouse's");

            Fixture counted = seed(connection, 0);
            counts.save(sheet(counted.branch(), counted.itemId()));
            assertTrue(rowOf(counted.branch(), counted.itemId()).moved(), "a draft count did not close the opening");
            assertFalse(rowOf(DefaultStock.ID, counted.itemId()).moved());

            Fixture moved = seed(connection, 10);
            transfers.transfer(new StockTransferCommand(DefaultStock.ID, moved.branch(), LocalDate.now(),
                    List.of(new StockTransferLine(moved.itemId(), 2)), USER));
            assertTrue(rowOf(moved.branch(), moved.itemId()).moved(), "a transfer in did not close the destination's");
            assertTrue(rowOf(DefaultStock.ID, moved.itemId()).moved(), "a transfer out did not close the source's");
            assertTrue(items.isOpeningBalanceLocked(moved.itemId()),
                    "the item screen's field is open on an item that has left its warehouse");
            assertFalse(items.isOpeningBalanceLocked(sold.itemId()),
                    "an item sold only in a branch locked the main warehouse's opening");
        });
    }

    @Test
    @DisplayName("a changed opening on an item that has moved is refused, and nothing of the batch is written")
    void aMovedItemIsRefusedWhole() throws Exception {
        inTransaction(connection -> {
            Fixture sold = seed(connection, 0);
            Fixture open = seed(connection, 0);
            insertItemStock(connection, open.itemId(), sold.branch(), 0);
            WarehouseOpeningDraft draft = new WarehouseOpeningDraft();
            draft.set(rowOf(sold.branch(), open.itemId()), 4);
            draft.set(rowOf(sold.branch(), sold.itemId()), 9);
            // Typed while nothing had moved, then a till sells one of them - the screen is out of date.
            insertSale(connection, sold.itemId(), sold.branch());

            assertThrows(BusinessRuleException.class, () -> openings.save(sold.branch(), draft.changes()));
            assertEquals(0.0, opening(connection, open.itemId(), sold.branch()),
                    "an item the batch did not refuse was written although the batch was");
            assertEquals(0.0, opening(connection, sold.itemId(), sold.branch()));
        });
    }

    @Test
    @DisplayName("a figure somebody else changed since the screen read it is not written over")
    void aStaleFigureIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection, 0);
            executeUpdate(connection, "UPDATE items_stock SET first_balance = 3 WHERE item_id = ? AND stock_id = ?",
                    fixture.itemId(), fixture.branch());

            assertThrows(BusinessRuleException.class, () -> openings.save(fixture.branch(),
                    List.of(new WarehouseOpeningDraft.Change(fixture.itemId(), fixture.stamp(), 0, 5))));
            assertEquals(3.0, opening(connection, fixture.itemId(), fixture.branch()));
        });
    }

    @Test
    @DisplayName("a switched-off warehouse takes no opening")
    void aSwitchedOffWarehouseIsRefused() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection, 0);
            executeUpdate(connection, "UPDATE stocks SET is_active = 0 WHERE stock_id = ?", fixture.branch());

            assertThrows(BusinessRuleException.class, () -> openings.save(fixture.branch(),
                    List.of(new WarehouseOpeningDraft.Change(fixture.itemId(), fixture.stamp(), 0, 5))));
            assertEquals(0.0, opening(connection, fixture.itemId(), fixture.branch()));
        });
    }

    /**
     * The defect the dropped trigger was: it copied the item row's opening over warehouse 1's on every
     * update of the item, so an opening that reached {@code items_stock} any other way was undone by
     * the next save of the item's price. Now the edit writes the item and leaves the shelf alone,
     * and the bulk editor's opening goes to warehouse 1 alone.
     */
    @Test
    @DisplayName("editing an item leaves warehouse 1's opening alone; the bulk editor's opening goes there")
    void anItemEditLeavesTheOpeningAlone() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection, 0);
            executeUpdate(connection, "UPDATE items_stock SET first_balance = 4 WHERE item_id = ? AND stock_id = ?",
                    fixture.itemId(), DefaultStock.ID);

            ItemsModel item = loaded(fixture.itemId());
            item.setBuyPrice(2);
            items.updateGroup(List.of(item), false, false);
            assertEquals(4.0, opening(connection, fixture.itemId(), DefaultStock.ID),
                    "an edit of the item rewrote warehouse 1's opening");

            item = loaded(fixture.itemId());
            item.setFirstBalanceForStock(6);
            items.updateGroup(List.of(item), false, true);
            assertEquals(6.0, opening(connection, fixture.itemId(), DefaultStock.ID));
            assertEquals(0.0, opening(connection, fixture.itemId(), fixture.branch()),
                    "the bulk editor's opening reached a warehouse other than the default");

            counts.save(sheet(DefaultStock.ID, fixture.itemId()));
            ItemsModel moved = loaded(fixture.itemId());
            moved.setFirstBalanceForStock(8);
            assertThrows(BusinessRuleException.class, () -> items.updateGroup(List.of(moved), false, true),
                    "an opening was changed under a count sheet holding the book balance");
            assertEquals(6.0, opening(connection, fixture.itemId(), DefaultStock.ID));
        });
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    private record Fixture(int itemId, int branch, String stamp) {
    }

    /** A branch and an item with a row in it and in the main warehouse, the main one holding {@code mainOpening}. */
    private static Fixture seed(Connection connection, double mainOpening) throws Exception {
        String stamp = "WOB-" + UUID.randomUUID().toString().substring(0, 8);
        int branch = insertStock(connection, stamp);
        int itemId = insertItem(connection, stamp);
        insertItemStock(connection, itemId, branch, 0);
        insertItemStock(connection, itemId, DefaultStock.ID, mainOpening);
        return new Fixture(itemId, branch, stamp);
    }

    private static WarehouseOpeningRow rowOf(int stockId, int itemId) throws Exception {
        String code = DaoFactory.INSTANCE.getItemsDao().findItemByIdAndStockId(itemId, stockId).getBarcode();
        List<WarehouseOpeningRow> rows = openings.page(WarehouseOpeningFilter.firstPage(stockId, code, false)).rows();
        assertEquals(1, rows.size(), "the item's code did not find exactly the item");
        return rows.getFirst();
    }

    /**
     * The item as the item screen reads it, in user 1's name. A model takes the signed-in user as its
     * author, and the operator this class signs in as is not a row of {@code users} - the update would
     * write it into {@code items.user_id} and fail on the key. The session stays an ordinary user's.
     */
    private static ItemsModel loaded(int itemId) throws Exception {
        ItemsModel item = DaoFactory.INSTANCE.getItemsDao().findItemByIdAndStockId(itemId, DefaultStock.ID);
        Users author = new Users();
        author.setId(USER);
        item.setUsers(author);
        return item;
    }

    private static StockCount sheet(int stockId, int itemId) {
        StockCount sheet = new StockCount();
        sheet.setStockId(stockId);
        sheet.setCountDate(LocalDate.now());
        sheet.setUserId(USER);
        sheet.setNotes("WOB-count");
        sheet.setLines(List.of(new StockCountLine(0, itemId, "counted", "WOB", 1, "unit", 1, 0, 1)));
        return sheet;
    }

    private static double balance(Connection connection, int itemId, int stockId) throws Exception {
        String sql = "SELECT first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment"
                + " - quantitySales - quantityPurchaseRe - fromStock FROM quantity_items_table"
                + " WHERE item_id = ? AND stock_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getDouble(1) : 0;
            }
        }
    }

    private static double opening(Connection connection, int itemId, int stockId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT first_balance FROM items_stock WHERE item_id = ? AND stock_id = ?")) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getDouble(1);
            }
        }
    }

    private static void insertSale(Connection connection, int itemId, int stockId) throws Exception {
        int invoiceId = count(connection, "SELECT COALESCE(MAX(invoice_number),0)+9100 FROM total_sales");
        String header = "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,delegate_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,?,1,1,'WOB-sale',?)";
        try (PreparedStatement statement = connection.prepareStatement(header)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, stockId);
            statement.setInt(3, USER);
            assertEquals(1, statement.executeUpdate());
        }
        String line = "INSERT INTO sales(invoice_number,num,type,quantity,price,buy_price,discount,"
                + "type_value,total_profit,expiration_date) VALUES (?,?,1,1,10,5,0,1,0,NULL)";
        try (PreparedStatement statement = connection.prepareStatement(line)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, itemId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int insertStock(Connection connection, String name) throws Exception {
        String sql = "INSERT INTO stocks(stock_name, stock_address, user_id) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, name);
            statement.setInt(3, USER);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int insertItem(Connection connection, String stamp) throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,user_id) VALUES (?,?,1,1,10,10,10,1,0,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, stamp);
            statement.setString(2, stamp);
            statement.setInt(3, USER);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static void insertItemStock(Connection connection, int itemId, int stockId, double opening)
            throws Exception {
        String sql = "INSERT IGNORE INTO items_stock(item_id,stock_id,first_balance) VALUES (?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            statement.setDouble(3, opening);
            statement.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void executeUpdate(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertNoResidue(Connection connection, String table, String where) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1),
                    "this class left rows behind in " + table + " - the rollback did not hold");
        }
    }

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
