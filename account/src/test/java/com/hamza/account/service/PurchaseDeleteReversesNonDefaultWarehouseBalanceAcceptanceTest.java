package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scenario multi-warehouse support was reported broken on in practice: a purchase
 * invoice is entered against a warehouse other than {@code DefaultStock.ID}, then
 * deleted. {@code quantity_items_table} is a view over {@code purchase}/{@code total_buy}
 * directly, not over {@code stock_movements} - {@link
 * TotalDocumentDeleteReversesStockLedgerAcceptanceTest} already pins the movement-ledger
 * side of a delete, but nothing pinned that the warehouse the invoice actually named gets
 * its balance back, as opposed to warehouse 1's.
 * <p>
 * Real-MySQL acceptance; opt in with {@code -Daccount.db.acceptance=true}. One
 * transaction, always rolled back - nothing here is left behind.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class PurchaseDeleteReversesNonDefaultWarehouseBalanceAcceptanceTest {

    private static final int UNIT_ID = 1;
    private static final DaoFactory FACTORY = DaoFactory.INSTANCE;

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
        UserSessionContext session = new UserSessionContext();
        session.signIn(1, "acceptance", Set.of(AppPermissions.PURCHASE_DELETE));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    @Test
    void deletingAPurchaseRestoresTheWarehouseItWasActuallyMadeAgainst() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertTrue(transaction != null);
        try {
            String marker = marker();
            int stockId = insertStock(transaction, marker);
            int itemId = insertItem(transaction, marker, stockId);
            int invoiceId = nextId(transaction, "total_buy", "invoice_number");
            insertHeader(transaction, invoiceId, stockId);
            insertLine(transaction, invoiceId, itemId, 50);

            assertEquals(50, balance(transaction, itemId, stockId), 0.0001,
                    "the purchase must show up in the warehouse it was actually entered against");
            assertEquals(0, balance(transaction, itemId, DefaultStock.ID), 0.0001,
                    "and nowhere else - warehouse 1 must not see a purchase made against a different one");

            new TotalBuyService(FACTORY).deleteMultiData(new Integer[]{invoiceId});

            assertEquals(0, balance(transaction, itemId, stockId), 0.0001,
                    "deleting the invoice must restore the balance of the warehouse it named");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static double balance(Connection connection, int itemId, int stockId) throws Exception {
        String sql = "SELECT first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment "
                + "- quantitySales - quantityPurchaseRe - fromStock AS balance "
                + "FROM quantity_items_table WHERE item_id = ? AND stock_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getDouble(1) : 0;
            }
        }
    }

    private static int insertStock(Connection connection, String marker) throws Exception {
        String sql = "INSERT INTO stocks(stock_name, stock_address, user_id) VALUES (?, ?, 1)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, marker);
            statement.setString(2, marker);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int insertItem(Connection connection, String marker, int stockId) throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,first_balance,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, marker);
            statement.setString(2, marker);
            statement.setInt(3, 1);
            statement.setDouble(4, 1);
            statement.setDouble(5, 10);
            statement.setDouble(6, 10);
            statement.setDouble(7, 10);
            statement.setInt(8, UNIT_ID);
            statement.setDouble(9, 0);
            statement.setDouble(10, 0);
            statement.setInt(11, 1);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                int itemId = keys.getInt(1);
                // The real application backfills items_stock for every warehouse an item or a
                // stock gains after the other already exists - see StockService.save and
                // ItemsDao.insert. Mirrored here rather than exercised, since this test is
                // about the delete path, not the backfill one.
                insertItemsStockRow(connection, itemId, DefaultStock.ID, 0);
                insertItemsStockRow(connection, itemId, stockId, 0);
                return itemId;
            }
        }
    }

    private static void insertItemsStockRow(Connection connection, int itemId, int stockId, double opening)
            throws Exception {
        String sql = "INSERT INTO items_stock(item_id,stock_id,first_balance,current_quantity) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            statement.setDouble(3, opening);
            statement.setDouble(4, opening);
            statement.executeUpdate();
        }
    }

    private static int nextId(Connection connection, String table, String key) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(" + key + "),0)+5000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void insertHeader(Connection connection, int invoiceId, int stockId) throws Exception {
        String sql = "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,?,1,'delete-warehouse-acceptance',1)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, stockId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertLine(Connection connection, int invoiceId, int itemId, double quantity)
            throws Exception {
        String sql = "INSERT INTO purchase(invoice_number,num,type,quantity,price,discount,type_value,"
                + "expiration_date) VALUES (?,?,1,?,10,0,1,NULL)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            assertEquals(1, statement.executeUpdate());
        }
    }

    /** Short enough for stocks.stock_name, which is VARCHAR(50) and unique. */
    private static String marker() {
        return "DELWH_" + UUID.randomUUID();
    }
}
