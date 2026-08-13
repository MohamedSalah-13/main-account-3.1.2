package com.hamza.account.model.dao;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.JdbcInvoiceNumberAllocator;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-MySQL acceptance; opt in with -Daccount.db.acceptance=true. */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class DocumentLineDatabaseAcceptanceTest {

    private static final String MARKER = "CODEX_LINE_SYNC_ACCEPTANCE";
    private static final DaoFactory FACTORY = DaoFactory.INSTANCE;

    @BeforeAll
    static void connect() throws Exception {
        File configFile = new File("config.xml");
        if (!configFile.isFile()) configFile = new File("../config.xml");
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

    @Test
    void allFourFamiliesKeepRetainedIdsAndRollbackEveryTemporaryRow() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertTrue(transaction != null);
        try {
            int itemId = insertItem(transaction);
            int salesDocument = nextId(transaction, "total_sales", "invoice_number");
            int purchaseDocument = nextId(transaction, "total_buy", "invoice_number");
            int salesReturnDocument = nextId(transaction, "total_sales_re", "id");
            int purchaseReturnDocument = nextId(transaction, "total_buy_re", "id");
            insertHeaders(transaction, salesDocument, purchaseDocument,
                    salesReturnDocument, purchaseReturnDocument);

            acceptSales(itemId, salesDocument);
            acceptPurchase(itemId, purchaseDocument);
            acceptSalesReturn(itemId, salesReturnDocument);
            acceptPurchaseReturn(itemId, purchaseReturnDocument);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }

        Connection connection = ConnectionManager.acquire();
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM items WHERE barcode=?")) {
                statement.setString(1, MARKER);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1), "acceptance data must be rolled back");
                }
            }
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @Test
    void invoiceNumberAllocationJoinsAndRollsBackWithTheOuterTransaction() throws Exception {
        long before;
        Connection connection = ConnectionManager.acquire();
        try {
            before = sequenceValue(connection, DocumentType.SALES);
        } finally {
            ConnectionManager.release(connection);
        }

        Connection transaction = ConnectionManager.beginTransaction();
        assertTrue(transaction != null);
        try {
            JdbcInvoiceNumberAllocator allocator = new JdbcInvoiceNumberAllocator();
            assertEquals(before + 1, allocator.next(DocumentType.SALES));
            assertEquals(before + 2, allocator.next(DocumentType.SALES));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }

        connection = ConnectionManager.acquire();
        try {
            assertEquals(before, sequenceValue(connection, DocumentType.SALES),
                    "rolled-back invoice save must not consume its number");
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private void acceptSales(int itemId, int documentId) throws Exception {
        SalesDao dao = FACTORY.salesDao();
        dao.insertList(List.of(sales(0, documentId, itemId, 1)));
        Sales loaded = dao.loadAllById(documentId).getFirst();
        assertEquals(7.5, loaded.getUnitsType().getValue());
        assertEquals(4.25, loaded.getBuy_price());

        acceptDelta(documentId, loaded.getId(),
                id -> sales(id, documentId, itemId, id == 0 ? 3 : 2), dao);
    }

    private void acceptPurchase(int itemId, int documentId) throws Exception {
        PurchaseDao dao = FACTORY.purchaseDao();
        dao.insertList(List.of(purchase(0, documentId, itemId, 1)));
        Purchase loaded = dao.loadAllById(documentId).getFirst();
        assertEquals(7.5, loaded.getUnitsType().getValue());

        acceptDelta(documentId, loaded.getId(),
                id -> purchase(id, documentId, itemId, id == 0 ? 3 : 2), dao);
    }

    private void acceptSalesReturn(int itemId, int documentId) throws Exception {
        SalesReturnDao dao = FACTORY.salesReturnsDao();
        dao.insertList(List.of(salesReturn(0, documentId, itemId, 1)));
        Sales_Return loaded = dao.loadAllById(documentId).getFirst();
        assertEquals(7.5, loaded.getUnitsType().getValue());
        assertEquals(4.25, loaded.getBuy_price());

        acceptDelta(documentId, loaded.getId(),
                id -> salesReturn(id, documentId, itemId, id == 0 ? 3 : 2), dao);
    }

    private void acceptPurchaseReturn(int itemId, int documentId) throws Exception {
        PurchaseReturnDao dao = FACTORY.purchaseReturnsDao();
        dao.insertList(List.of(purchaseReturn(0, documentId, itemId, 1)));
        Purchase_Return loaded = dao.loadAllById(documentId).getFirst();
        assertEquals(7.5, loaded.getUnitsType().getValue());

        acceptDelta(documentId, loaded.getId(),
                id -> purchaseReturn(id, documentId, itemId, id == 0 ? 3 : 2), dao);
    }

    private <T extends com.hamza.account.model.base.BasePurchasesAndSales> void acceptDelta(
            int documentId, int retainedId, IntFunction<T> line,
            DocumentLineDao<T> dao) throws Exception {
        T retained = line.apply(retainedId);
        T added = line.apply(0);
        dao.synchronizeLines(documentId, List.of(retained, added));

        List<T> afterAdd = dao.loadAllById(documentId);
        assertEquals(2, afterAdd.size());
        assertTrue(afterAdd.stream().anyMatch(row -> row.getId() == retainedId
                && row.getQuantity() == 2));
        int addedId = afterAdd.stream().filter(row -> row.getId() != retainedId)
                .findFirst().orElseThrow().getId();

        dao.synchronizeLines(documentId, List.of(retained));
        List<T> afterDelete = dao.loadAllById(documentId);
        assertEquals(1, afterDelete.size());
        assertEquals(retainedId, afterDelete.getFirst().getId());
        assertFalse(afterDelete.stream().anyMatch(row -> row.getId() == addedId));
    }

    private static Sales sales(int id, int documentId, int itemId, double quantity) {
        Sales line = new Sales();
        common(line, id, documentId, itemId, quantity);
        line.setBuy_price(4.25);
        line.setTotalSelPrice(BigDecimal.valueOf(quantity * 10));
        line.setTotal_buy_price(quantity * 4.25);
        line.setTotal_profit(BigDecimal.valueOf(quantity * 5.75));
        return line;
    }

    private static Purchase purchase(int id, int documentId, int itemId, double quantity) {
        Purchase line = new Purchase();
        common(line, id, documentId, itemId, quantity);
        return line;
    }

    private static Sales_Return salesReturn(int id, int documentId, int itemId, double quantity) {
        Sales_Return line = new Sales_Return();
        common(line, id, documentId, itemId, quantity);
        line.setBuy_price(4.25);
        line.setTotalSelPrice(BigDecimal.valueOf(quantity * 10));
        line.setTotal_buy_price(quantity * 4.25);
        line.setTotal_profit(BigDecimal.valueOf(quantity * 5.75));
        return line;
    }

    private static Purchase_Return purchaseReturn(int id, int documentId, int itemId, double quantity) {
        Purchase_Return line = new Purchase_Return();
        common(line, id, documentId, itemId, quantity);
        return line;
    }

    private static void common(com.hamza.account.model.base.BasePurchasesAndSales line,
                               int id, int documentId, int itemId, double quantity) {
        line.setId(id);
        line.setInvoiceNumber(documentId);
        line.setNumItem(itemId);
        line.setItems(new ItemsModel(itemId, MARKER, MARKER));
        line.setUnitsType(new UnitsModel(1, "acceptance", 7.5));
        line.setQuantity(quantity);
        line.setPrice(10);
        line.setDiscount(0);
        line.setTotal(quantity * 10);
    }

    private static int insertItem(Connection connection) throws Exception {
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,sel_price3,"
                + "unit_id,mini_quantity,first_balance,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, MARKER);
            statement.setString(2, MARKER);
            statement.setInt(3, 1);
            statement.setDouble(4, 4.25);
            statement.setDouble(5, 10);
            statement.setDouble(6, 10);
            statement.setDouble(7, 10);
            statement.setInt(8, 1);
            statement.setDouble(9, 0);
            statement.setDouble(10, 0);
            statement.setInt(11, 1);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                int itemId = keys.getInt(1);
                try (PreparedStatement stock = connection.prepareStatement(
                        "INSERT INTO items_stock(item_id,stock_id,first_balance,current_quantity) VALUES (?,1,0,0)")) {
                    stock.setInt(1, itemId);
                    stock.executeUpdate();
                }
                return itemId;
            }
        }
    }

    private static int nextId(Connection connection, String table, String key) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(" + key + "),0)+1000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static long sequenceValue(Connection connection, DocumentType type) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT current_value FROM document_sequences WHERE document_type=?")) {
            statement.setString(1, type.name());
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private static void insertHeaders(Connection connection, int sales, int purchase,
                                      int salesReturn, int purchaseReturn) throws Exception {
        execute(connection, "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,?,1)", sales);
        execute(connection, "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,?,1)", purchase);
        execute(connection, "INSERT INTO total_sales_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_from_treasury,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,1,?,1)", salesReturn);
        execute(connection, "INSERT INTO total_buy_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_to_treasury,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,?,1)", purchaseReturn);
    }

    private static void execute(Connection connection, String sql, int id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, MARKER);
            assertEquals(1, statement.executeUpdate());
        }
    }
}
