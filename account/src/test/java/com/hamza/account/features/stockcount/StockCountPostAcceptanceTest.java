package com.hamza.account.features.stockcount;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Posting a stock count, against a real MySQL, through the service the screen calls.
 * <p>
 * The count is the fourth writer of a stock balance and the last one with nothing to say it works:
 * the transfer had a class that proved nothing, and the count had none at all. It is also the
 * writer whose adjustment is the least obvious - it is a <b>difference</b> against the snapshot
 * taken when each line was scanned, not the balance to end at - so the case below counts what the
 * shelf holds after a sale made while the sheet was open, and checks the balance lands on what is
 * on the shelf rather than on what the sheet says.
 * <p>
 * <b>Signed in as an ordinary user, deliberately</b>: {@code isSystemAdministrator()} is
 * {@code currentUserId() == 1} and bypasses every permission, so the permission case would pass
 * whatever the service did.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. One transaction per case, rolled back, and
 * {@code @AfterAll} queries for this class's own {@code CNT-} stamp rather than trusting it.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class StockCountPostAcceptanceTest {

    private static final int USER = 1;
    private static final int OPERATOR = 4244;

    private static final double OPENING = 30;

    private static StockCountService service;

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
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
        signIn(AppPermissions.STOCK_COUNT_SHOW, AppPermissions.STOCK_COUNT_POST);
        service = new StockCountService(DaoFactory.INSTANCE);
    }

    @AfterAll
    static void leaveNothingBehind() throws Exception {
        Connection connection = ConnectionManager.acquire();
        try {
            assertNoResidue(connection, "items", "barcode LIKE 'CNT-%'");
            assertNoResidue(connection, "stock_count", "notes LIKE 'CNT-%'");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    @Test
    @DisplayName("a posted count moves the balance by the difference it found")
    void aPostedCountMovesTheBalance() throws Exception {
        inTransaction(connection -> {
            int itemId = seed(connection);

            post(newSheet(itemId, OPENING, 28));

            assertEquals(28, balance(connection, itemId, DefaultStock.ID), 0.0001,
                    "the shelf did not end where the sheet said it was");
        });
    }

    /**
     * The adjustment is a difference, not a target, and this is the case that says so. The sheet
     * was taken when the book said 30 and the counter found 28; a sale of 5 went through before it
     * was posted. The right answer is 23 - the two missing that the count found, and the five that
     * were sold - and it is what re-reading the snapshot at post time would get wrong.
     */
    @Test
    @DisplayName("a sale made while the sheet was open is not swallowed by the count")
    void aSaleDuringTheCountSurvivesThePost() throws Exception {
        inTransaction(connection -> {
            int itemId = seed(connection);
            StockCount sheet = newSheet(itemId, OPENING, 28);

            insertSale(connection, itemId, DefaultStock.ID, 5);
            post(sheet);

            assertEquals(23, balance(connection, itemId, DefaultStock.ID), 0.0001,
                    "the count undid the sale instead of correcting what it found");
        });
    }

    /**
     * An item whose warehouse has no {@code items_stock} row for it. The adjustment would be summed
     * into a row {@code quantity_items_table} never reads: the count posts, the screen reports the
     * lines that moved, and nothing moves.
     */
    @Test
    @DisplayName("an item with no row in the warehouse is given one, so its adjustment lands")
    void anItemWithNoRowIsGivenOne() throws Exception {
        inTransaction(connection -> {
            String stamp = "CNT-" + UUID.randomUUID().toString().substring(0, 8);
            int itemId = insertItem(connection, stamp);
            int warehouse = insertStock(connection, stamp);

            post(sheetIn(warehouse, itemId, 0, 4));

            assertEquals(4, balance(connection, itemId, warehouse), 0.0001,
                    "the adjustment was written where no balance reads it");
        });
    }

    @Test
    @DisplayName("a user without stock.count.post is refused before anything is written")
    void aUserWithoutThePermissionIsRefused() throws Exception {
        inTransaction(connection -> {
            int itemId = seed(connection);
            signIn(AppPermissions.STOCK_COUNT_SHOW);
            try {
                assertThrows(BusinessRuleException.class, () -> post(newSheet(itemId, OPENING, 28)));
                assertEquals(OPENING, balance(connection, itemId, DefaultStock.ID), 0.0001);
            } finally {
                signIn(AppPermissions.STOCK_COUNT_SHOW, AppPermissions.STOCK_COUNT_POST);
            }
        });
    }

    @Test
    @DisplayName("a posted sheet cannot be posted again")
    void aPostedSheetCannotBePostedTwice() throws Exception {
        inTransaction(connection -> {
            int itemId = seed(connection);
            StockCount sheet = newSheet(itemId, OPENING, 28);

            post(sheet);

            assertThrows(BusinessRuleException.class, () -> service.post(sheet));
            assertEquals(28, balance(connection, itemId, DefaultStock.ID), 0.0001,
                    "the second post moved the balance a second time");
        });
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    private static void post(StockCount sheet) throws Exception {
        service.post(sheet);
    }

    private static StockCount newSheet(int itemId, double systemQuantity, double counted) {
        return sheetIn(DefaultStock.ID, itemId, systemQuantity, counted);
    }

    private static StockCount sheetIn(int stockId, int itemId, double systemQuantity, double counted) {
        StockCount sheet = new StockCount();
        sheet.setStockId(stockId);
        sheet.setCountDate(LocalDate.now());
        sheet.setUserId(USER);
        sheet.setNotes("CNT-" + UUID.randomUUID().toString().substring(0, 8));
        sheet.setLines(List.of(new StockCountLine(0, itemId, "counted", "CNT",
                1, "unit", 1, systemQuantity, counted)));
        return sheet;
    }

    private static int seed(Connection connection) throws Exception {
        String stamp = "CNT-" + UUID.randomUUID().toString().substring(0, 8);
        int itemId = insertItem(connection, stamp);
        insertItemStock(connection, itemId, DefaultStock.ID, OPENING);
        return itemId;
    }

    /** The view's own balance, so the assertions read the definition every screen reads. */
    private static double balance(Connection connection, int itemId, int stockId) throws Exception {
        String sql = "SELECT first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment"
                + " - quantitySales - quantityPurchaseRe - fromStock AS balance"
                + " FROM quantity_items_table WHERE item_id = ? AND stock_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getDouble(1) : 0;
            }
        }
    }

    private static int insertStock(Connection connection, String stamp) throws Exception {
        String sql = "INSERT INTO stocks(stock_name, stock_address, user_id) VALUES (?, ?, ?)";
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

    private static int insertItem(Connection connection, String stamp) throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,first_balance,user_id) VALUES (?,?,1,1,10,10,10,1,0,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, stamp);
            statement.setString(2, stamp);
            statement.setDouble(3, OPENING);
            statement.setInt(4, USER);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static void insertItemStock(Connection connection, int itemId, int stockId, double opening)
            throws Exception {
        String sql = "INSERT INTO items_stock(item_id,stock_id,first_balance,current_quantity) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            statement.setDouble(3, opening);
            statement.setDouble(4, opening);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSale(Connection connection, int itemId, int stockId, double quantity)
            throws Exception {
        int invoiceId;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(invoice_number),0)+9000 FROM total_sales")) {
            assertTrue(rows.next());
            invoiceId = rows.getInt(1);
        }
        String header = "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,delegate_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,?,1,1,'stock-count-acceptance',?)";
        try (PreparedStatement statement = connection.prepareStatement(header)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, stockId);
            statement.setInt(3, USER);
            assertEquals(1, statement.executeUpdate());
        }
        String line = "INSERT INTO sales(invoice_number,num,type,quantity,price,buy_price,discount,"
                + "type_value,total_profit,expiration_date) VALUES (?,?,1,?,10,5,0,1,0,NULL)";
        try (PreparedStatement statement = connection.prepareStatement(line)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            assertEquals(1, statement.executeUpdate());
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
