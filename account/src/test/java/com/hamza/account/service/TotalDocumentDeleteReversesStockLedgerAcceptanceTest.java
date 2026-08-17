package com.hamza.account.service;

import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.stockledger.MovementType;
import com.hamza.account.features.stockledger.StockMovement;
import com.hamza.account.features.stockledger.StockMovementAssembler;
import com.hamza.account.features.stockledger.StockMovementDao;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closes the gap {@code docs/erp-roadmap.md} §8.4 documents: deleting a document used to
 * remove its rows from {@code total_sales}/{@code total_buy}/etc via {@code ON DELETE
 * CASCADE} while leaving its {@code stock_movements} rows behind forever. Real-MySQL
 * acceptance; opt in with {@code -Daccount.db.acceptance=true}.
 * <p>
 * Each of the four {@code TotalXxxService.deleteMultiData} methods is exercised for real
 * (not mocked - none of the four record services has an injectable seam), inside one
 * transaction that is always rolled back. A second, undeleted document per type proves
 * the fix is scoped to what was actually deleted, not a blanket wipe.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class TotalDocumentDeleteReversesStockLedgerAcceptanceTest {

    private static final int STOCK_ID = 1;
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
        session.signIn(1, "acceptance", Set.of(
                AppPermissions.PURCHASE_DELETE, AppPermissions.SALES_DELETE,
                AppPermissions.SALES_RE_DELETE, AppPermissions.PURCHASE_RE_DELETE));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterAll
    static void disconnect() {
        DataSourceProvider.shutdown();
    }

    @Test
    void deletingAPurchaseRemovesOnlyItsOwnMovements() throws Exception {
        runScenario("total_buy", "invoice_number", "purchase", "num", DocumentType.PURCHASE,
                (kept, deleted) -> new TotalBuyService(FACTORY).deleteMultiData(new Integer[]{deleted}));
    }

    @Test
    void deletingASaleRemovesOnlyItsOwnMovements() throws Exception {
        runScenario("total_sales", "invoice_number", "sales", "num", DocumentType.SALES,
                (kept, deleted) -> new TotalSalesService(FACTORY).deleteMultiData(new Integer[]{deleted}));
    }

    @Test
    void deletingASalesReturnRemovesOnlyItsOwnMovements() throws Exception {
        runScenario("total_sales_re", "id", "sales_re", "item_id", DocumentType.SALES_RETURN,
                (kept, deleted) -> new TotalSalesReturnService(FACTORY).deleteMultiData(new Integer[]{deleted}));
    }

    @Test
    void deletingAPurchaseReturnRemovesOnlyItsOwnMovements() throws Exception {
        runScenario("total_buy_re", "id", "purchase_re", "item_id", DocumentType.PURCHASE_RETURN,
                (kept, deleted) -> new TotalBuyReturnService(FACTORY).deleteMultiData(new Integer[]{deleted}));
    }

    @FunctionalInterface
    private interface Delete {
        int run(int keptId, int deletedId) throws Exception;
    }

    private void runScenario(String headerTable, String headerKey, String lineTable, String itemColumn,
                             DocumentType documentType, Delete delete) throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertTrue(transaction != null);
        try {
            int itemId = insertItem(transaction, marker(), 10);
            int kept = nextId(transaction, headerTable, headerKey);
            int deleted = kept + 1;
            insertHeader(transaction, headerTable, headerKey, kept);
            insertHeader(transaction, headerTable, headerKey, deleted);
            insertLine(transaction, lineTable, itemColumn, kept, itemId, 2);
            insertLine(transaction, lineTable, itemColumn, deleted, itemId, 3);

            StockMovementDao movementDao = new StockMovementDao();
            String referenceType = StockMovementAssembler.referenceTypeFor(documentType);
            movementDao.insertBatch(java.util.List.of(
                    movement(itemId, referenceType, kept, documentType),
                    movement(itemId, referenceType, deleted, documentType)));

            assertEquals(2, countMovements(transaction, referenceType, kept, deleted));

            delete.run(kept, deleted);

            assertEquals(1, countMovements(transaction, referenceType, kept, kept),
                    "the kept document's movement must survive");
            assertEquals(0, countMovements(transaction, referenceType, deleted, deleted),
                    "the deleted document must leave no movement behind");
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static StockMovement movement(int itemId, String referenceType, int referenceId, DocumentType type) {
        boolean stockIn = type.stockDirection() == DocumentType.Direction.IN;
        return new StockMovement(itemId, STOCK_ID, LocalDate.now(), MovementType.valueOf(referenceType),
                stockIn ? 1 : 0, stockIn ? 0 : 1, UNIT_ID, 1, referenceType, referenceId, 1);
    }

    private static int countMovements(Connection connection, String referenceType, int fromId, int toId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM stock_movements WHERE reference_type = ? "
                        + "AND reference_id BETWEEN ? AND ?")) {
            statement.setString(1, referenceType);
            statement.setInt(2, fromId);
            statement.setInt(3, toId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static int insertItem(Connection connection, String marker, double opening) throws Exception {
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
                     "SELECT COALESCE(MAX(" + key + "),0)+5000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void insertHeader(Connection connection, String table, String key, int id) throws Exception {
        String sql = switch (table) {
            case "total_sales" -> "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,"
                    + "total,discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                    + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,'delete-acceptance',1)";
            case "total_buy" -> "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                    + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                    + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,'delete-acceptance',1)";
            case "total_sales_re" -> "INSERT INTO total_sales_re(id,sup_id,invoice_date,invoice_type,total,"
                    + "discount,paid_from_treasury,stock_id,delegate_id,treasury_id,notes,user_id) "
                    + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,1,'delete-acceptance',1)";
            case "total_buy_re" -> "INSERT INTO total_buy_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                    + "paid_to_treasury,stock_id,treasury_id,notes,user_id) "
                    + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,'delete-acceptance',1)";
            default -> throw new IllegalArgumentException(table);
        };
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
        return "DELETE_ACCEPTANCE_" + UUID.randomUUID();
    }
}
