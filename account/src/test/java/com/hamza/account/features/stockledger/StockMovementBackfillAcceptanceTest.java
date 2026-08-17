package com.hamza.account.features.stockledger;

import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountDao;
import com.hamza.account.features.stockcount.StockCountLine;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §8.5 of {@code docs/erp-roadmap.md}: a backfill that regenerates {@code stock_movements}
 * from documents written before the ledger existed, proven by
 * {@link StockLedgerReconciliationReport} agreeing with {@code quantity_items_table}
 * afterward. Real-MySQL acceptance; opt in with {@code -Daccount.db.acceptance=true}.
 * <p>
 * The setup deliberately writes the four invoice families and a posted stock count
 * through raw SQL and {@link StockCountDao} directly - never through
 * {@code InvoiceSaveService}/{@code StockCountService} - so the ledger starts genuinely
 * empty for this item, matching what a real install looked like before §8.3-8.4 shipped.
 * <p>
 * Mismatches are filtered to this test's own item: the connected database may carry real
 * data with its own reconciliation state, and this test asserts nothing about that.
 * Everything happens inside one transaction that is always rolled back.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class StockMovementBackfillAcceptanceTest {

    private static final int STOCK_ID = 1;
    private static final int UNIT_ID = 1;

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
    void backfillReconcilesHistoricalDocumentsWithTheView() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertTrue(transaction != null);
        try {
            int itemId = insertItem(transaction, marker(), 15);

            // 15 + 6 - 4 - 2 + 3 + 5 = 23, none of it written to stock_movements yet.
            int purchase = nextId(transaction, "total_buy", "invoice_number");
            int sales = nextId(transaction, "total_sales", "invoice_number");
            int purchaseReturn = nextId(transaction, "total_buy_re", "id");
            int salesReturn = nextId(transaction, "total_sales_re", "id");
            insertHeaders(transaction, sales, purchase, salesReturn, purchaseReturn);
            insertLine(transaction, "purchase", "num", purchase, itemId, 6);
            insertLine(transaction, "sales", "num", sales, itemId, 4);
            insertLine(transaction, "purchase_re", "item_id", purchaseReturn, itemId, 2);
            insertLine(transaction, "sales_re", "item_id", salesReturn, itemId, 3);

            StockCount count = new StockCount();
            count.setStockId(STOCK_ID);
            count.setUserId(1);
            count.setLines(List.of(new StockCountLine(0, itemId, marker(), marker(), UNIT_ID, "unit", 1, 10, 15)));
            StockCountDao countDao = new StockCountDao();
            countDao.save(count);
            assertEquals(1, countDao.post(count.getId()));

            assertEquals(23.0, viewBalance(transaction, itemId));
            assertMismatchDifference(itemId, 23.0 - 15.0, "before backfill, the empty ledger must disagree");

            StockMovementBackfillService backfill = new StockMovementBackfillService(new StockMovementDao());
            int written = backfill.backfillAll();
            assertTrue(written > 0);
            assertNoMismatchFor(itemId);

            // Re-running must be idempotent: same reconciliation, no duplication.
            int writtenAgain = backfill.backfillAll();
            assertEquals(written, writtenAgain);
            assertNoMismatchFor(itemId);
            assertEquals(23.0, ledgerBalance(transaction, itemId));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static void assertMismatchDifference(int itemId, double expectedDifference, String message)
            throws Exception {
        StockLedgerReconciliationReport.Mismatch mismatch = mismatchFor(itemId);
        assertTrue(mismatch != null, message);
        assertEquals(expectedDifference, mismatch.difference(), 0.001, message);
    }

    private static void assertNoMismatchFor(int itemId) throws Exception {
        assertTrue(mismatchFor(itemId) == null,
                "the ledger must agree with quantity_items_table for this item after backfill");
    }

    private static StockLedgerReconciliationReport.Mismatch mismatchFor(int itemId) throws Exception {
        return new StockLedgerReconciliationReport().run().stream()
                .filter(m -> m.itemId() == itemId)
                .findFirst()
                .orElse(null);
    }

    private static double viewBalance(Connection connection, int itemId) throws Exception {
        String sql = """
                SELECT first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment
                       - quantitySales - quantityPurchaseRe - fromStock AS balance
                FROM quantity_items_table
                WHERE item_id = ? AND stock_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, STOCK_ID);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getDouble("balance");
            }
        }
    }

    private static double ledgerBalance(Connection connection, int itemId) throws Exception {
        String sql = """
                SELECT (SELECT first_balance FROM items WHERE id = ?)
                       + COALESCE(SUM(quantity_in), 0) - COALESCE(SUM(quantity_out), 0) AS balance
                FROM stock_movements
                WHERE item_id = ? AND stock_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, itemId);
            statement.setInt(2, itemId);
            statement.setInt(3, STOCK_ID);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getDouble("balance");
            }
        }
    }

    private static int insertItem(Connection connection, String marker, double opening)
            throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,first_balance,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, marker);
            statement.setString(2, marker);
            statement.setInt(3, 1);
            statement.setDouble(4, 1);
            statement.setDouble(5, 10);
            statement.setDouble(6, 10);
            statement.setDouble(7, 10);
            statement.setInt(8, UNIT_ID);
            statement.setDouble(9, 0);
            statement.setDouble(10, opening);
            statement.setInt(11, 1);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                int itemId = keys.getInt(1);
                try (PreparedStatement stock = connection.prepareStatement(
                        "INSERT INTO items_stock(item_id,stock_id,first_balance,current_quantity) VALUES (?,1,?,?)")) {
                    stock.setInt(1, itemId);
                    stock.setDouble(2, opening);
                    stock.setDouble(3, opening);
                    stock.executeUpdate();
                }
                return itemId;
            }
        }
    }

    private static int nextId(Connection connection, String table, String key) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(" + key + "),0)+4000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void insertHeaders(Connection connection, int sales, int purchase,
                                      int salesReturn, int purchaseReturn) throws Exception {
        execute(connection, "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,'backfill-acceptance',1)", sales);
        execute(connection, "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,'backfill-acceptance',1)", purchase);
        execute(connection, "INSERT INTO total_sales_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_from_treasury,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,1,'backfill-acceptance',1)", salesReturn);
        execute(connection, "INSERT INTO total_buy_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_to_treasury,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,'backfill-acceptance',1)", purchaseReturn);
    }

    private static void execute(Connection connection, String sql, int id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertLine(Connection connection, String table, String itemColumn,
                                   int documentId, int itemId, double quantity) throws Exception {
        String salesFields = table.equals("sales") || table.equals("sales_re")
                ? ",buy_price,total_sel_price,total_buy_price,total_profit" : "";
        String salesValues = salesFields.isEmpty() ? "" : ",1,10,1,9";
        String sql = "INSERT INTO " + table + "(invoice_number," + itemColumn
                + ",type,quantity,price,discount,type_value,expiration_date" + salesFields
                + ") VALUES (?,?,1,?,10,0,1,NULL" + salesValues + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, documentId);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String marker() {
        return "BACKFILL_ACCEPTANCE_" + UUID.randomUUID();
    }
}
