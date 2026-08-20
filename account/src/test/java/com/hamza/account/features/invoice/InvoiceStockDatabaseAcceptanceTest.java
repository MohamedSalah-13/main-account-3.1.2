package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.dao.CardItemDao;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-MySQL acceptance; opt in with -Daccount.db.acceptance=true. */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class InvoiceStockDatabaseAcceptanceTest {

    private static final LocalDate EXPIRY = LocalDate.of(2027, 1, 31);

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
    void readsTotalAndExpiryBalanceFromAllFourInvoiceFamilies() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertTrue(transaction != null);
        try {
            int itemId = insertItem(transaction, marker(), 10);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            int purchase = nextId(transaction, "total_buy", "invoice_number");
            int salesReturn = nextId(transaction, "total_sales_re", "id");
            int purchaseReturn = nextId(transaction, "total_buy_re", "id");
            insertHeaders(transaction, sales, purchase, salesReturn, purchaseReturn);
            insertLine(transaction, "purchase", "num", purchase, itemId, 5);
            insertLine(transaction, "sales_re", "item_id", salesReturn, itemId, 2);
            insertLine(transaction, "sales", "num", sales, itemId, 3);
            insertLine(transaction, "purchase_re", "item_id", purchaseReturn, itemId, 1);

            JdbcInvoiceStockRepository repository = new JdbcInvoiceStockRepository();
            repository.lockItems(List.of(itemId));

            assertEquals(13.0,
                    repository.currentBaseBalances(List.of(itemId)).get(itemId));
            assertEquals(3.0, repository.currentExpiryBalances(List.of(itemId))
                    .get(new InvoiceStockRepository.BatchKey(itemId, EXPIRY)));
            assertEquals(3.0,
                    new CardItemDao().expiryBalancesByItem(itemId).get(EXPIRY));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void secondTransactionWaitsForItemLockAndSeesTheCommittedBalance() throws Exception {
        String marker = marker();
        int itemId = insertCommittedItem(marker, 5);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttemptsLock = new CountDownLatch(1);
        try {
            Future<Void> first = workers.submit(() -> {
                Connection transaction = ConnectionManager.beginTransaction();
                boolean committed = false;
                try {
                    new JdbcInvoiceStockRepository().lockItems(List.of(itemId));
                    updateOpeningBalance(transaction, itemId, 1);
                    firstHasLock.countDown();
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                    transaction.commit();
                    committed = true;
                    return null;
                } finally {
                    if (!committed) {
                        transaction.rollback();
                    }
                    ConnectionManager.endTransaction(transaction);
                }
            });
            assertTrue(firstHasLock.await(5, TimeUnit.SECONDS));

            InvoiceStockRepository signalling = new SignallingRepository(
                    new JdbcInvoiceStockRepository(), secondAttemptsLock);
            Future<Void> second = workers.submit(() -> TransactionTemplate.execute(() -> {
                new InvoiceStockGuard(DocumentType.SALES, signalling)
                        .validate(command(itemId, 4));
                return null;
            }));
            assertTrue(secondAttemptsLock.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,
                    () -> second.get(250, TimeUnit.MILLISECONDS),
                    "the second save must wait while the item row is locked");

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> second.get(5, TimeUnit.SECONDS));
            assertInstanceOf(BusinessRuleException.class, rejected.getCause());
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
            deleteCommittedItem(itemId);
        }
    }

    private static InvoiceSaveCommand command(int itemId, double quantity) {
        ItemsModel item = new ItemsModel(itemId, "acceptance", "acceptance");
        Sales line = new Sales();
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "piece", 1));
        line.setQuantity(quantity);
        line.setPrice(10);
        line.setTotal(quantity * 10);
        return new InvoiceSaveCommand(0, LocalDate.now(), InvoiceType.CASH,
                0, DiscountType.AMOUNT, quantity * 10, "", 1, "party",
                "treasury", "delegate", false, List.of(line));
    }

    private static int insertCommittedItem(String marker, double opening) throws Exception {
        Connection connection = ConnectionManager.acquire();
        try {
            return insertItem(connection, marker, opening);
        } finally {
            ConnectionManager.release(connection);
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
            statement.setInt(8, 1);
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

    private static void updateOpeningBalance(
            Connection connection, int itemId, double balance) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE items SET first_balance=? WHERE id=?")) {
            statement.setDouble(1, balance);
            statement.setInt(2, itemId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void deleteCommittedItem(int itemId) throws Exception {
        Connection connection = ConnectionManager.acquire();
        try {
            try (PreparedStatement stock = connection.prepareStatement(
                    "DELETE FROM items_stock WHERE item_id=?")) {
                stock.setInt(1, itemId);
                stock.executeUpdate();
            }
            try (PreparedStatement item = connection.prepareStatement(
                    "DELETE FROM items WHERE id=?")) {
                item.setInt(1, itemId);
                item.executeUpdate();
            }
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private static int nextId(Connection connection, String table, String key)
            throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COALESCE(MAX(" + key + "),0)+2000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void insertHeaders(Connection connection, int sales, int purchase,
                                      int salesReturn, int purchaseReturn) throws Exception {
        execute(connection, "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,'acceptance',1)", sales);
        execute(connection, "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,'acceptance',1)", purchase);
        execute(connection, "INSERT INTO total_sales_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_from_treasury,stock_id,delegate_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,1,'acceptance',1)", salesReturn);
        execute(connection, "INSERT INTO total_buy_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                + "paid_to_treasury,stock_id,treasury_id,notes,user_id) "
                + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,'acceptance',1)", purchaseReturn);
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
                + ") VALUES (?,?,1,?,10,0,1,?" + salesValues + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, documentId);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            statement.setDate(4, java.sql.Date.valueOf(EXPIRY));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String marker() {
        return "CODEX_STOCK_GUARD_" + UUID.randomUUID();
    }

    private record SignallingRepository(
            InvoiceStockRepository delegate,
            CountDownLatch attemptsLock) implements InvoiceStockRepository {

        @Override
        public List<StoredLine> originalLinesForUpdate(
                DocumentType type, int documentId) throws com.hamza.controlsfx.database.DaoException {
            return delegate.originalLinesForUpdate(type, documentId);
        }

        @Override
        public Map<Integer, String> lockItems(List<Integer> itemIds)
                throws com.hamza.controlsfx.database.DaoException {
            attemptsLock.countDown();
            return delegate.lockItems(itemIds);
        }

        @Override
        public Map<Integer, Double> currentBaseBalances(List<Integer> itemIds)
                throws com.hamza.controlsfx.database.DaoException {
            return delegate.currentBaseBalances(itemIds);
        }

        @Override
        public Map<BatchKey, Double> currentExpiryBalances(List<Integer> itemIds)
                throws com.hamza.controlsfx.database.DaoException {
            return delegate.currentExpiryBalances(itemIds);
        }
    }
}
