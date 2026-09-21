package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.invoice.JdbcInvoiceStockRepository;
import com.hamza.account.features.items.StockScope;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.features.stockcount.StockCountService;
import com.hamza.account.features.stocktransfer.StockTransferCommand;
import com.hamza.account.features.stocktransfer.StockTransferLine;
import com.hamza.account.features.stocktransfer.StockTransferService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Stock;
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
 * Switching a warehouse off (V77), against a real MySQL, through the services the screens call.
 * <p>
 * The rules are unit-tested in {@code StockServiceActivityTest}; what only a database can say is
 * that "still holds stock" is the balance the view derives, that a switched-off warehouse is then
 * refused by the writers that do not go through {@code StockService} - the transfer, the count and
 * the invoice's guard - and that the audit trigger records the switch.
 * <p>
 * Signed in as an ordinary user: user 1 bypasses every permission. Opt in with
 * {@code -Daccount.db.acceptance=true}; one rolled-back transaction per case, and {@code @AfterAll}
 * queries for this class's {@code WHA-} stamp rather than trusting the rollback.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class WarehouseActivityAcceptanceTest {

    private static final int USER = 1;
    private static final int OPERATOR = 4245;
    private static final double OPENING = 10;

    private static StockService stocks;
    private static StockTransferService transfers;
    private static StockCountService counts;

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
        signIn(AppPermissions.STOCK_UPDATE, AppPermissions.STOCK_TRANSFER_POST,
                AppPermissions.STOCK_COUNT_SHOW, AppPermissions.STOCK_COUNT_CREATE, AppPermissions.STOCK_COUNT_POST);
        stocks = new StockService(DaoFactory.INSTANCE);
        transfers = new StockTransferService(DaoFactory.INSTANCE);
        counts = new StockCountService(DaoFactory.INSTANCE);
    }

    @AfterAll
    static void leaveNothingBehind() throws Exception {
        Connection connection = ConnectionManager.acquire();
        try {
            assertNoResidue(connection, "stocks", "stock_name LIKE 'WHA-%'");
            assertNoResidue(connection, "items", "barcode LIKE 'WHA-%'");
        } finally {
            ConnectionManager.release(connection);
            DataSourceProvider.shutdown();
        }
    }

    /**
     * The whole life of a closed branch: refused while it holds stock, switched off once the stock
     * has been transferred out, then refused by every writer of a new movement, and gone from the
     * pickers of documents while it stays in the pickers of history.
     */
    @Test
    @DisplayName("a warehouse is switched off once emptied, and then takes no new movement")
    void aClosedBranch() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            int branch = fixture.branch();

            assertThrows(BusinessRuleException.class, () -> stocks.setActive(branch, false),
                    "a warehouse still holding ten of an item was switched off");

            transfers.transfer(new StockTransferCommand(branch, DefaultStock.ID, LocalDate.now(),
                    List.of(new StockTransferLine(fixture.itemId(), OPENING)), USER));
            stocks.setActive(branch, false);

            assertFalse(isActive(connection, branch));
            assertThrows(BusinessRuleException.class, () -> transfers.transfer(new StockTransferCommand(
                    DefaultStock.ID, branch, LocalDate.now(), List.of(new StockTransferLine(fixture.itemId(), 1)),
                    USER)), "a transfer into a switched-off warehouse was accepted");
            assertThrows(BusinessRuleException.class, () -> counts.post(sheet(branch, fixture.itemId())),
                    "a count in a switched-off warehouse was accepted");
            assertEquals("WHA-" + fixture.stamp(),
                    new JdbcInvoiceStockRepository().inactiveStockName(branch).orElseThrow(),
                    "the invoice's guard does not see the warehouse as switched off");

            assertTrue(stocks.stocksForPicker(StockScope.ACTIVE_ONLY).stream().noneMatch(s -> s.getId() == branch),
                    "a document is still offered the switched-off warehouse");
            assertTrue(stocks.stocksForPicker(StockScope.EVERYONE).stream().anyMatch(s -> s.getId() == branch),
                    "history lost the switched-off warehouse");
        });
    }

    @Test
    @DisplayName("switching off is written to the audit log as such, with the flag before and after")
    void theSwitchIsAudited() throws Exception {
        inTransaction(connection -> {
            int empty = insertStock(connection, "WHA-" + UUID.randomUUID().toString().substring(0, 8));

            stocks.setActive(empty, false);

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT notes, JSON_EXTRACT(old_data, '$.is_active'), JSON_EXTRACT(new_data, '$.is_active')
                    FROM audit_log WHERE table_name = 'stocks' AND record_id = ? AND action_type = 'UPDATE'
                    ORDER BY id DESC LIMIT 1""")) {
                statement.setString(1, String.valueOf(empty));
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "switching a warehouse off left no audit row");
                    assertEquals("Warehouse switched off", rows.getString(1));
                    assertEquals(1, rows.getInt(2));
                    assertEquals(0, rows.getInt(3));
                }
            }
        });
    }

    @Test
    @DisplayName("switched back on, it takes movements again")
    void switchedBackOn() throws Exception {
        inTransaction(connection -> {
            Fixture fixture = seed(connection);
            transfers.transfer(new StockTransferCommand(fixture.branch(), DefaultStock.ID, LocalDate.now(),
                    List.of(new StockTransferLine(fixture.itemId(), OPENING)), USER));
            stocks.setActive(fixture.branch(), false);

            stocks.setActive(fixture.branch(), true);
            transfers.transfer(new StockTransferCommand(DefaultStock.ID, fixture.branch(), LocalDate.now(),
                    List.of(new StockTransferLine(fixture.itemId(), 2)), USER));

            assertTrue(isActive(connection, fixture.branch()));
        });
    }

    @Test
    @DisplayName("a warehouse with a draft count open is not switched off, and the default never is")
    void whatIsNeverSwitchedOff() throws Exception {
        inTransaction(connection -> {
            int empty = insertStock(connection, "WHA-" + UUID.randomUUID().toString().substring(0, 8));
            int itemId = insertItem(connection, "WHA-" + UUID.randomUUID().toString().substring(0, 8));
            insertItemStock(connection, itemId, empty, 0);
            counts.save(sheet(empty, itemId));

            assertThrows(BusinessRuleException.class, () -> stocks.setActive(empty, false),
                    "a warehouse was switched off with a draft count open in it");
            assertThrows(BusinessRuleException.class, () -> stocks.setActive(DefaultStock.ID, false));
            assertTrue(isActive(connection, empty));
            assertTrue(isActive(connection, DefaultStock.ID));
        });
    }

    // ------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------

    private record Fixture(int itemId, int branch, String stamp) {
    }

    /** A branch holding ten of an item, and the same item's row in the main warehouse. */
    private static Fixture seed(Connection connection) throws Exception {
        String stamp = UUID.randomUUID().toString().substring(0, 8);
        int branch = insertStock(connection, "WHA-" + stamp);
        int itemId = insertItem(connection, "WHA-" + stamp);
        insertItemStock(connection, itemId, branch, OPENING);
        insertItemStock(connection, itemId, DefaultStock.ID, 0);
        return new Fixture(itemId, branch, stamp);
    }

    private static StockCount sheet(int stockId, int itemId) {
        StockCount sheet = new StockCount();
        sheet.setStockId(stockId);
        sheet.setCountDate(LocalDate.now());
        sheet.setUserId(USER);
        sheet.setNotes("WHA-count");
        sheet.setLines(List.of(new StockCountLine(0, itemId, "counted", "WHA", 1, "unit", 1, 0, 1)));
        return sheet;
    }

    private static boolean isActive(Connection connection, int stockId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT is_active FROM stocks WHERE stock_id = ?")) {
            statement.setInt(1, stockId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getBoolean(1);
            }
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
        String sql = "INSERT INTO items_stock(item_id,stock_id,first_balance) VALUES (?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            statement.setDouble(3, opening);
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
