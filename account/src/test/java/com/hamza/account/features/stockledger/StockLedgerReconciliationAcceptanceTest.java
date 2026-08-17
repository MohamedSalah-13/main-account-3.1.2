package com.hamza.account.features.stockledger;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountDao;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.UnitsModel;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reference-snapshot test {@code docs/erp-roadmap.md} §5 calls "0.6": a known
 * scenario, run against a real database, whose balance the existing
 * {@code quantity_items_table} and the new {@code stock_movements} ledger must agree on
 * exactly. Real-MySQL acceptance; opt in with {@code -Daccount.db.acceptance=true}.
 * <p>
 * The scenario drives the four invoice families and a posted stock count through raw
 * inserts into their own tables (mirroring
 * {@code InvoiceStockDatabaseAcceptanceTest}'s style), and separately through
 * {@link StockMovementAssembler} + {@link StockMovementDao} - the actual dual-write code
 * this pins. Everything happens inside one transaction that is always rolled back, so
 * nothing here is ever committed against the configured database.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class StockLedgerReconciliationAcceptanceTest {

    private static final LocalDate INVOICE_DATE = LocalDate.of(2026, 8, 17);
    private static final int STOCK_ID = 1;
    private static final int UNIT_ID = 1;
    private static final int USER_ID = 1;

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
    void ledgerBalanceMatchesTheViewForAKnownScenario() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertTrue(transaction != null);
        try {
            int itemId = insertItem(transaction, marker(), 20);

            // Purchase +5, sale -3, purchase return -1, sales return +2, count +4:
            // 20 + 5 - 3 - 1 + 2 + 4 = 27.
            int purchase = nextId(transaction, "total_buy", "invoice_number");
            int sales = nextId(transaction, "total_sales", "invoice_number");
            int purchaseReturn = nextId(transaction, "total_buy_re", "id");
            int salesReturn = nextId(transaction, "total_sales_re", "id");
            insertHeaders(transaction, sales, purchase, salesReturn, purchaseReturn);
            insertLine(transaction, "purchase", "num", purchase, itemId, 5);
            insertLine(transaction, "sales", "num", sales, itemId, 3);
            insertLine(transaction, "purchase_re", "item_id", purchaseReturn, itemId, 1);
            insertLine(transaction, "sales_re", "item_id", salesReturn, itemId, 2);

            StockMovementDao movementDao = new StockMovementDao();
            movementDao.insertBatch(StockMovementAssembler.assemble(
                    DocumentType.PURCHASE, STOCK_ID, purchase, INVOICE_DATE,
                    List.of(purchaseLine(itemId, 5)), USER_ID));
            movementDao.insertBatch(StockMovementAssembler.assemble(
                    DocumentType.SALES, STOCK_ID, sales, INVOICE_DATE,
                    List.of(salesLine(itemId, 3)), USER_ID));
            movementDao.insertBatch(StockMovementAssembler.assemble(
                    DocumentType.PURCHASE_RETURN, STOCK_ID, purchaseReturn, INVOICE_DATE,
                    List.of(purchaseReturnLine(itemId, 1)), USER_ID));
            movementDao.insertBatch(StockMovementAssembler.assemble(
                    DocumentType.SALES_RETURN, STOCK_ID, salesReturn, INVOICE_DATE,
                    List.of(salesReturnLine(itemId, 2)), USER_ID));

            StockCount count = new StockCount();
            count.setStockId(STOCK_ID);
            count.setCountDate(INVOICE_DATE);
            count.setUserId(USER_ID);
            count.setLines(List.of(
                    new StockCountLine(0, itemId, marker(), marker(), UNIT_ID, "unit", 1, 8, 12)));
            StockCountDao countDao = new StockCountDao();
            countDao.save(count);
            assertEquals(1, countDao.post(count.getId()));
            movementDao.insertBatch(StockMovementAssembler.forStockCount(count));

            assertEquals(27.0, viewBalance(transaction, itemId));
            assertEquals(27.0, ledgerBalance(transaction, itemId));

            // Edit the sale from 3 to 6: the view moves because the raw row changed, and
            // the ledger must move the same way through delete-and-reinsert (the
            // deliberate phase-1 edit strategy - see StockMovementDao.deleteByReference).
            updateSalesQuantity(transaction, sales, 6);
            movementDao.deleteByReference(StockMovementAssembler.referenceTypeFor(DocumentType.SALES), sales);
            movementDao.insertBatch(StockMovementAssembler.assemble(
                    DocumentType.SALES, STOCK_ID, sales, INVOICE_DATE,
                    List.of(salesLine(itemId, 6)), USER_ID));

            assertEquals(24.0, viewBalance(transaction, itemId));
            assertEquals(24.0, ledgerBalance(transaction, itemId));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
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

    private static Purchase purchaseLine(int itemId, double quantity) {
        Purchase line = new Purchase();
        line.setItems(item(itemId));
        line.setUnitsType(new UnitsModel(UNIT_ID, "unit", 1));
        line.setQuantity(quantity);
        return line;
    }

    private static Sales salesLine(int itemId, double quantity) {
        Sales line = new Sales();
        line.setItems(item(itemId));
        line.setUnitsType(new UnitsModel(UNIT_ID, "unit", 1));
        line.setQuantity(quantity);
        return line;
    }

    private static Purchase_Return purchaseReturnLine(int itemId, double quantity) {
        Purchase_Return line = new Purchase_Return();
        line.setItems(item(itemId));
        line.setUnitsType(new UnitsModel(UNIT_ID, "unit", 1));
        line.setQuantity(quantity);
        return line;
    }

    private static Sales_Return salesReturnLine(int itemId, double quantity) {
        Sales_Return line = new Sales_Return();
        line.setItems(item(itemId));
        line.setUnitsType(new UnitsModel(UNIT_ID, "unit", 1));
        line.setQuantity(quantity);
        return line;
    }

    private static ItemsModel item(int itemId) {
        ItemsModel item = new ItemsModel();
        item.setId(itemId);
        return item;
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
            statement.setInt(11, USER_ID);
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
                     "SELECT COALESCE(MAX(" + key + "),0)+3000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void insertHeaders(Connection connection, int sales, int purchase,
                                      int salesReturn, int purchaseReturn) throws Exception {
        execute(connection, "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,'ledger-acceptance',1)", sales);
        execute(connection, "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,'ledger-acceptance',1)", purchase);
        execute(connection, "INSERT INTO total_sales_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_from_treasury,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,1,'ledger-acceptance',1)", salesReturn);
        execute(connection, "INSERT INTO total_buy_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_to_treasury,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,'ledger-acceptance',1)", purchaseReturn);
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

    private static void updateSalesQuantity(Connection connection, int invoiceNumber, double quantity)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE sales SET quantity = ? WHERE invoice_number = ?")) {
            statement.setDouble(1, quantity);
            statement.setInt(2, invoiceNumber);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String marker() {
        return "LEDGER_ACCEPTANCE_" + UUID.randomUUID();
    }
}
