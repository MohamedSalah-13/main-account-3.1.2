package com.hamza.account.model.dao;

import com.hamza.account.config.DefaultStock;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the item card's balance to the one the inventory sheet reads, for a warehouse that has
 * sent or received a transfer.
 * <p>
 * There is one definition of a balance in this system - {@code quantity_items_table}, which counts
 * transfers out and transfers in among its eight components - and the card has been answering a
 * different one: {@code CardItemDao.balanceSql} adds the opening balance, the four document
 * families and posted counts, and nothing at all from {@code stock_transfer_list}. So on any
 * warehouse a transfer has touched, the card and the sheet beside it show two numbers for one
 * shelf, and which one a person reads is decided by which screen they opened. That is the defect
 * the party and treasury work exists to have removed elsewhere.
 * <p>
 * It asserts no arithmetic of its own: it reads the view and asserts the card agrees with it, so
 * the two cannot be made to agree by teaching this test the same mistake.
 * <p>
 * Opt in with {@code -Daccount.db.acceptance=true}. One transaction, rolled back in a
 * {@code finally} - safe on a database with data in it.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ItemCardBalanceAcceptanceTest {

    private static final double SOURCE_OPENING = 10;
    private static final double DESTINATION_OPENING = 3;
    private static final double MOVED = 4;

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
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    /**
     * Both ends of one transfer. The destination is the louder half - goods arrive and the card
     * does not know - but the source is wrong by the same quantity in the other direction, so
     * both are asserted.
     */
    @Test
    void theCardAgreesWithTheInventorySheetAfterATransfer() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            String marker = "CARDBAL_" + UUID.randomUUID().toString().substring(0, 8);
            int destination = insertStock(transaction, marker);
            int itemId = insertItem(transaction, marker);
            insertItemStock(transaction, itemId, DefaultStock.ID, SOURCE_OPENING);
            insertItemStock(transaction, itemId, destination, DESTINATION_OPENING);
            insertTransfer(transaction, itemId, DefaultStock.ID, destination, MOVED, LocalDate.now());

            CardItemDao dao = new CardItemDao();
            LocalDate today = LocalDate.now();

            assertEquals(SOURCE_OPENING - MOVED, viewBalance(transaction, itemId, DefaultStock.ID), 0.0001,
                    "the fixture did not move what it meant to move out of the source");
            assertEquals(DESTINATION_OPENING + MOVED, viewBalance(transaction, itemId, destination), 0.0001,
                    "the fixture did not move what it meant to move into the destination");

            assertEquals(viewBalance(transaction, itemId, destination),
                    dao.balanceOn(destination, itemId, today, true), 0.0001,
                    "the card does not count a transfer into the warehouse it arrived in");
            assertEquals(viewBalance(transaction, itemId, DefaultStock.ID),
                    dao.balanceOn(DefaultStock.ID, itemId, today, true), 0.0001,
                    "the card does not count a transfer out of the warehouse it left");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * The opening balance is the same statement with {@code inclusive = false}, so a transfer
     * dated before the period has to be inside it - otherwise a card whose period starts after
     * the transfer opens on a balance that never existed.
     */
    @Test
    void aTransferBeforeThePeriodIsInTheOpeningBalance() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            String marker = "CARDOPEN_" + UUID.randomUUID().toString().substring(0, 8);
            int destination = insertStock(transaction, marker);
            int itemId = insertItem(transaction, marker);
            insertItemStock(transaction, itemId, DefaultStock.ID, SOURCE_OPENING);
            insertItemStock(transaction, itemId, destination, DESTINATION_OPENING);
            insertTransfer(transaction, itemId, DefaultStock.ID, destination, MOVED,
                    LocalDate.now().minusDays(30));

            CardItemDao dao = new CardItemDao();

            assertEquals(DESTINATION_OPENING + MOVED,
                    dao.balanceOn(destination, itemId, LocalDate.now(), false), 0.0001,
                    "a transfer that arrived before the period is not in the opening balance");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * The card's three figures against each other and against the view: the opening balance, the
     * movements it lists, and the closing balance. The screen shows all three on one row - opening,
     * net movement, closing - so a movement that is not a row makes them contradict each other with
     * nothing on the screen to say which is wrong.
     * <p>
     * The period holds one of each kind that used to be missing: a transfer out and a posted count,
     * beside an ordinary purchase.
     */
    @Test
    void theRowsBetweenTwoDatesAddUpToTheClosingBalance() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            String marker = "CARDROWS_" + UUID.randomUUID().toString().substring(0, 8);
            int destination = insertStock(transaction, marker);
            int itemId = insertItem(transaction, marker);
            insertItemStock(transaction, itemId, DefaultStock.ID, SOURCE_OPENING);
            insertItemStock(transaction, itemId, destination, DESTINATION_OPENING);

            LocalDate from = LocalDate.now().minusDays(7);
            LocalDate to = LocalDate.now();
            insertPurchase(transaction, itemId, DefaultStock.ID, 6, LocalDate.now().minusDays(5));
            insertTransfer(transaction, itemId, DefaultStock.ID, destination, MOVED,
                    LocalDate.now().minusDays(3));
            insertPostedCount(transaction, itemId, DefaultStock.ID, 12, 11, LocalDate.now().minusDays(1));

            CardItemDao dao = new CardItemDao();
            double opening = dao.balanceOn(DefaultStock.ID, itemId, from, false);
            var rows = dao.cardRows(DefaultStock.ID, itemId, from, to, null);
            double closingFromRows = com.hamza.account.features.itemcard.ItemCardRunningBalance
                    .apply(rows, opening);

            assertEquals(3, rows.size(), "the card left out a movement of this warehouse");
            assertEquals(dao.balanceOn(DefaultStock.ID, itemId, to, true), closingFromRows, 0.0001,
                    "the rows and the closing balance describe different movements");
            assertEquals(viewBalance(transaction, itemId, DefaultStock.ID), closingFromRows, 0.0001,
                    "the card and the inventory sheet describe different shelves");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /** The view's own balance: its eight components, written the way every caller writes them. */
    private static double viewBalance(Connection connection, int itemId, int stockId) throws Exception {
        String sql = "SELECT first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment"
                + " - quantitySales - quantityPurchaseRe - fromStock AS balance"
                + " FROM quantity_items_table WHERE item_id = ? AND stock_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, stockId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "the view has no row for this item and warehouse");
                return rows.getDouble(1);
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

    private static int insertItem(Connection connection, String marker) throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,user_id) VALUES (?,?,1,1,10,10,10,1,0,1)";
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

    private static void insertPurchase(Connection connection, int itemId, int stockId,
                                       double quantity, LocalDate date) throws Exception {
        int invoiceId;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(invoice_number),0)+7000 FROM total_buy")) {
            assertTrue(rows.next());
            invoiceId = rows.getInt(1);
        }
        String header = "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,?,10,0,10,?,1,'item-card-balance-acceptance',1)";
        try (PreparedStatement statement = connection.prepareStatement(header)) {
            statement.setInt(1, invoiceId);
            statement.setObject(2, date);
            statement.setInt(3, stockId);
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

    /** A posted count whose line found {@code counted} where the system said {@code system}. */
    private static void insertPostedCount(Connection connection, int itemId, int stockId,
                                          double system, double counted, LocalDate date) throws Exception {
        String header = "INSERT INTO stock_count(stock_id,count_date,status,notes,posted_at,user_id) "
                + "VALUES (?,?,'POSTED','item-card-balance-acceptance',NOW(),1)";
        long countId;
        try (PreparedStatement statement = connection.prepareStatement(header, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, stockId);
            statement.setObject(2, date);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                countId = keys.getLong(1);
            }
        }
        String line = "INSERT INTO stock_count_lines(count_id,item_id,unit_id,type_value,system_qty,counted_qty) "
                + "VALUES (?,?,1,1,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(line)) {
            statement.setLong(1, countId);
            statement.setInt(2, itemId);
            statement.setDouble(3, system);
            statement.setDouble(4, counted);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertTransfer(Connection connection, int itemId, int from, int to,
                                       double quantity, LocalDate date) throws Exception {
        String header = "INSERT INTO stock_transfer(transfer_date, stock_from, stock_to, user_id) VALUES (?,?,?,1)";
        long transferId;
        try (PreparedStatement statement = connection.prepareStatement(header, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, date);
            statement.setInt(2, from);
            statement.setInt(3, to);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                transferId = keys.getLong(1);
            }
        }
        String line = "INSERT INTO stock_transfer_list(stock_transfer_id,item_id,type,quantity,type_value) "
                + "VALUES (?,?,1,?,1)";
        try (PreparedStatement statement = connection.prepareStatement(line)) {
            statement.setLong(1, transferId);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            assertEquals(1, statement.executeUpdate());
        }
    }
}
