package com.hamza.account.model.dao;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.model.domain.ItemsModel;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the catalogue's all-warehouse meaning against the real MySQL views. The
 * legacy {@code items.first_balance} carries warehouse 1 only, while the two
 * {@code items_stock} rows below carry the complete opening. A purchase in the second
 * warehouse makes it impossible for the expected current balance to come from either
 * warehouse row by accident.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. The fixture is always rolled back.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ItemsCatalogBalanceAcceptanceTest {

    private static final double DEFAULT_OPENING = 10;
    private static final double SECOND_OPENING = 7;
    private static final double SECOND_PURCHASE = 5;

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
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    @Test
    void openingAndCurrentBalanceIncludeEveryWarehouse() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            String marker = "CATBAL_" + UUID.randomUUID().toString().substring(0, 8);
            int secondStock = insertStock(transaction, marker);
            int itemId = insertItem(transaction, marker);
            insertItemStock(transaction, itemId, DefaultStock.ID, DEFAULT_OPENING);
            insertItemStock(transaction, itemId, secondStock, SECOND_OPENING);
            insertPurchase(transaction, itemId, secondStock, SECOND_PURCHASE);

            ItemsModel row = DaoFactory.INSTANCE.getItemsDao().getCatalogItem(itemId);

            assertNotNull(row);
            assertEquals(DEFAULT_OPENING + SECOND_OPENING,
                    row.getFirstBalanceForStock(), 0.0001,
                    "the catalogue omitted one warehouse's opening balance");
            assertEquals(DEFAULT_OPENING + SECOND_OPENING + SECOND_PURCHASE,
                    row.getSumAllBalance(), 0.0001,
                    "the catalogue did not combine every opening and movement exactly once");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
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

    private static int insertItem(Connection connection, String marker) throws Exception {
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
            statement.setInt(8, 1);
            statement.setDouble(9, 0);
            statement.setDouble(10, DEFAULT_OPENING);
            statement.setInt(11, 1);
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

    private static void insertPurchase(Connection connection, int itemId, int stockId, double quantity)
            throws Exception {
        int invoiceId = nextInvoiceId(connection);
        String header = "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,?,1,'catalog-balance-acceptance',1)";
        try (PreparedStatement statement = connection.prepareStatement(header)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, stockId);
            assertEquals(1, statement.executeUpdate());
        }

        String line = "INSERT INTO purchase(invoice_number,num,type,quantity,price,discount,type_value,"
                + "expiration_date) VALUES (?,?,1,?,10,0,1,NULL)";
        try (PreparedStatement statement = connection.prepareStatement(line)) {
            statement.setInt(1, invoiceId);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int nextInvoiceId(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(invoice_number),0)+7000 FROM total_buy")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
