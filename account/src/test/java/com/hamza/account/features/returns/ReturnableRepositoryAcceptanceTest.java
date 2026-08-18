package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.time.LocalDate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JdbcReturnableRepository} and {@link ReturnSourceWriter} against a real
 * database - what {@link ReturnGuardTest}'s fake repository stands in for elsewhere.
 * Real-MySQL acceptance, opt in with {@code -Daccount.db.acceptance=true}, one
 * transaction always rolled back, in the manner of
 * {@code StockLedgerReconciliationAcceptanceTest}.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ReturnableRepositoryAcceptanceTest {

    private static final JdbcReturnableRepository REPOSITORY = new JdbcReturnableRepository();
    private static final ReturnSourceWriter WRITER = new ReturnSourceWriter();

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

    /**
     * Every read here has to work with no transaction open, because that is how the
     * UI calls them: the "return from invoice" picker, the returned-status badge and
     * the reasons report all run on the JavaFX thread, long before any save starts.
     * <p>
     * This is the case a {@code requireTransaction} guard in {@code withConnection}
     * broke - and that every other test in this class missed, because they all open a
     * transaction first so they can roll their fixtures back. Deliberately read-only
     * so it needs no rollback of its own.
     */
    @Test
    void everyReadWorksOutsideATransaction() throws Exception {
        assertFalse(ConnectionManager.inTransaction());

        int noSuchInvoice = 999_000_000;
        assertFalse(REPOSITORY.sourceExists(DocumentType.SALES, noSuchInvoice));
        assertTrue(REPOSITORY.sourceLines(DocumentType.SALES, noSuchInvoice).isEmpty());
        assertTrue(REPOSITORY.rawLines(DocumentType.SALES, noSuchInvoice).isEmpty());
        assertTrue(REPOSITORY.alreadyReturnedBaseQuantities(
                DocumentType.SALES_RETURN, noSuchInvoice, 0).isEmpty());
        assertTrue(REPOSITORY.lineById(DocumentType.SALES, noSuchInvoice).isEmpty());
        assertTrue(REPOSITORY.sourceExpiryBatches(
                DocumentType.SALES, noSuchInvoice, 1).isEmpty());
        assertTrue(REPOSITORY.sourceDelegateId(noSuchInvoice).isEmpty());
        // Not asserted empty: the database under test may hold real returns in range.
        assertNotNull(REPOSITORY.reasonCounts(DocumentType.SALES_RETURN,
                LocalDate.now().minusYears(50), LocalDate.now().plusYears(50)));

        assertFalse(ConnectionManager.inTransaction(),
                "reading must not leave a transaction bound to this thread");
    }

    @Test
    void aSecondReturnSeesWhatTheFirstAlreadyTookAndTheWriterLinksBoth() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            insertSalesLine(transaction, sales, itemId, 10);

            assertTrue(REPOSITORY.sourceExists(DocumentType.SALES, sales));
            assertFalse(REPOSITORY.sourceExists(DocumentType.SALES, sales + 999));

            List<ReturnableRepository.SoldLine> sold =
                    REPOSITORY.sourceLines(DocumentType.SALES, sales);
            assertEquals(1, sold.size());
            assertEquals(itemId, sold.get(0).itemId());
            assertEquals(10.0, sold.get(0).baseQuantity());

            // Nothing returned yet.
            Map<Integer, Double> before = REPOSITORY.alreadyReturnedBaseQuantities(
                    DocumentType.SALES_RETURN, sales, 0);
            assertTrue(before.isEmpty());

            int firstReturn = nextId(transaction, "total_sales_re", "id");
            insertSalesReturnHeader(transaction, firstReturn);
            insertSalesReturnLine(transaction, firstReturn, itemId, 4);
            WRITER.writeSource(DocumentType.SALES_RETURN, firstReturn, sales, null);

            assertEquals(sales, sourceInvoiceOf(transaction, firstReturn));

            // A second, not-yet-saved return must see the first one's 4 units.
            Map<Integer, Double> afterFirst = REPOSITORY.alreadyReturnedBaseQuantities(
                    DocumentType.SALES_RETURN, sales, 0);
            assertEquals(4.0, afterFirst.get(itemId));

            // Editing the first return itself must not see its own quantity.
            Map<Integer, Double> excludingItself = REPOSITORY.alreadyReturnedBaseQuantities(
                    DocumentType.SALES_RETURN, sales, firstReturn);
            assertTrue(excludingItself.isEmpty());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void lineByIdReadsTheOriginalSalesLineIncludingItsCostAtTheTime() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            int lineId = insertSalesLineReturningId(transaction, sales, itemId, 3, 12.5, 4.0);

            // The item's own cost has since moved - lineById must still answer what
            // this specific line recorded at the time, not the item's price today.
            raiseItemBuyPrice(transaction, itemId, 9.0);

            var line = REPOSITORY.lineById(DocumentType.SALES, lineId);
            assertTrue(line.isPresent());
            assertEquals(itemId, line.get().itemId());
            assertEquals(12.5, line.get().price());
            assertEquals(4.0, line.get().buyPrice());

            assertTrue(REPOSITORY.lineById(DocumentType.SALES, lineId + 999_000).isEmpty());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void aPurchaseLineHasNoCostOfItsOwnToPreserve() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int purchase = nextId(transaction, "total_buy", "invoice_number");
            try (PreparedStatement statement = transaction.prepareStatement(
                    "INSERT INTO total_buy(invoice_number,sup_code,invoice_type,invoice_date,total,"
                            + "discount,paid_up,stock_id,treasury_id,notes,user_id) "
                            + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,'returnable-repo-acceptance',1)")) {
                statement.setInt(1, purchase);
                assertEquals(1, statement.executeUpdate());
            }
            int lineId;
            try (PreparedStatement statement = transaction.prepareStatement(
                    "INSERT INTO purchase(invoice_number,num,type,quantity,price,discount,"
                            + "type_value,expiration_date) VALUES (?,?,1,3,8.5,0,1,NULL)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, purchase);
                statement.setInt(2, itemId);
                assertEquals(1, statement.executeUpdate());
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    lineId = keys.getInt(1);
                }
            }

            var line = REPOSITORY.lineById(DocumentType.PURCHASE, lineId);
            assertTrue(line.isPresent());
            assertEquals(8.5, line.get().price());
            assertEquals(0.0, line.get().buyPrice());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void sourceExpiryBatchesReadsTheSourceInvoicesOwnDatesSummedPerDate() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            LocalDate january = LocalDate.of(2027, 1, 31);
            LocalDate february = LocalDate.of(2027, 2, 28);
            insertSalesLineWithExpiry(transaction, sales, itemId, 2, january);
            // A second line of the same item and date - the two must be summed.
            insertSalesLineWithExpiry(transaction, sales, itemId, 3, january);
            insertSalesLineWithExpiry(transaction, sales, itemId, 4, february);

            List<ReturnableRepository.ExpiryBatch> batches =
                    REPOSITORY.sourceExpiryBatches(DocumentType.SALES, sales, itemId);

            assertEquals(2, batches.size());
            assertEquals(january, batches.get(0).expirationDate());
            assertEquals(5.0, batches.get(0).baseQuantity());
            assertEquals(february, batches.get(1).expirationDate());
            assertEquals(4.0, batches.get(1).baseQuantity());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void rawLinesListsEveryLineIndividuallyInOrderWithItsOwnCost() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemA = insertItem(transaction);
            int itemB = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            int firstLine = insertSalesLineReturningId(transaction, sales, itemA, 3, 10, 4);
            int secondLine = insertSalesLineReturningId(transaction, sales, itemB, 2, 20, 8);

            List<ReturnableRepository.SourceLineRow> rows =
                    REPOSITORY.rawLines(DocumentType.SALES, sales);

            assertEquals(2, rows.size());
            assertEquals(firstLine, rows.get(0).lineId());
            assertEquals(itemA, rows.get(0).itemId());
            assertEquals(10.0, rows.get(0).price());
            assertEquals(4.0, rows.get(0).buyPrice());
            assertEquals(secondLine, rows.get(1).lineId());
            assertEquals(itemB, rows.get(1).itemId());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void sourceDelegateIdReadsTheSalesInvoicesOwnDelegate() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            try (PreparedStatement statement = transaction.prepareStatement(
                    "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                            + "discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                            + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,'returnable-repo-acceptance',1)")) {
                statement.setInt(1, sales);
                assertEquals(1, statement.executeUpdate());
            }
            insertSalesLine(transaction, sales, itemId, 1);

            var delegateId = REPOSITORY.sourceDelegateId(sales);

            assertTrue(delegateId.isPresent());
            assertEquals(1, delegateId.get());
            assertTrue(REPOSITORY.sourceDelegateId(sales + 999_000).isEmpty());
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void writeSourceStampsTheReasonAndReasonCountsGroupsByIt() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            insertSalesLine(transaction, sales, itemId, 10);

            int damaged = nextId(transaction, "total_sales_re", "id");
            insertSalesReturnHeader(transaction, damaged);
            insertSalesReturnLine(transaction, damaged, itemId, 2);
            WRITER.writeSource(DocumentType.SALES_RETURN, damaged, sales, ReturnReason.DAMAGED);

            int wrongItem = nextId(transaction, "total_sales_re", "id");
            insertSalesReturnHeader(transaction, wrongItem);
            insertSalesReturnLine(transaction, wrongItem, itemId, 3);
            WRITER.writeSource(DocumentType.SALES_RETURN, wrongItem, sales, ReturnReason.WRONG_ITEM);

            int noReason = nextId(transaction, "total_sales_re", "id");
            insertSalesReturnHeader(transaction, noReason);
            insertSalesReturnLine(transaction, noReason, itemId, 1);
            // No writeSource call at all - a free return, exactly like every return
            // before this feature existed. Its reason column stays NULL.

            var damagedReason = readReturnReason(transaction, damaged);
            assertEquals("DAMAGED", damagedReason);

            List<ReturnableRepository.ReasonCount> counts = REPOSITORY.reasonCounts(
                    DocumentType.SALES_RETURN, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            var byReason = new java.util.HashMap<ReturnReason, ReturnableRepository.ReasonCount>();
            int nullReasonCount = 0;
            for (var count : counts) {
                if (count.reason() == null) {
                    nullReasonCount += count.count();
                } else {
                    byReason.put(count.reason(), count);
                }
            }
            assertEquals(1, byReason.get(ReturnReason.DAMAGED).count());
            assertEquals(1, byReason.get(ReturnReason.WRONG_ITEM).count());
            assertTrue(nullReasonCount >= 1);
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static String readReturnReason(Connection connection, int id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT return_reason FROM total_sales_re WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    /**
     * Deleting a sale that has been returned against would remove the sale's stock-out
     * and leave the return's stock-in behind, raising the item's balance by the
     * returned quantity out of nothing. The link {@code V16} added is what makes that
     * detectable; {@link ReturnLinkGuard} is what refuses it.
     */
    @Test
    void aSaleThatHasBeenReturnedAgainstCannotBeDeleted() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            insertSalesLine(transaction, sales, itemId, 5);

            // No return yet - deleting is nobody's business but the caller's.
            assertDoesNotThrow(() ->
                    ReturnLinkGuard.requireNoReturns(DocumentType.SALES, sales));

            int returnId = nextId(transaction, "total_sales_re", "id");
            insertSalesReturnHeader(transaction, returnId);
            insertSalesReturnLine(transaction, returnId, itemId, 2);
            WRITER.writeSource(DocumentType.SALES_RETURN, returnId, sales, null);

            BusinessRuleException refused = assertThrows(BusinessRuleException.class, () ->
                    ReturnLinkGuard.requireNoReturns(DocumentType.SALES, sales));
            assertTrue(refused.getMessage().contains(String.valueOf(sales)),
                    "the refusal must name the invoice: " + refused.getMessage());

            // An unrelated invoice in the same call is not blocked by this one.
            assertDoesNotThrow(() ->
                    ReturnLinkGuard.requireNoReturns(DocumentType.SALES, sales + 777));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    /**
     * The whole point of steps 1 and 2: a saved return has to come back off the
     * database still knowing what it reversed, on the header <em>and</em> on every
     * line. While it did not, every guard was silently off on the edit path -
     * {@code ReturnGuard} read a source of 0, called it a free return, and returned
     * without checking anything.
     */
    @Test
    void aSavedReturnRoundTripsItsSourceOnBothTheHeaderAndItsLines() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            int soldLineId = insertSalesLineReturningId(transaction, sales, itemId, 5, 10, 4);

            int returnId = nextId(transaction, "total_sales_re", "id");
            insertSalesReturnHeader(transaction, returnId);
            insertSalesReturnLineLinked(transaction, returnId, itemId, 2, soldLineId);
            WRITER.writeSource(DocumentType.SALES_RETURN, returnId, sales, ReturnReason.DAMAGED);

            // The header, through the view the DAO actually reads.
            try (PreparedStatement statement = transaction.prepareStatement(
                    "SELECT source_invoice_number, return_reason"
                            + " FROM total_sales_return_names_table WHERE id = ?")) {
                statement.setInt(1, returnId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "the return must be readable through its view");
                    assertEquals(sales, rows.getInt("source_invoice_number"));
                    assertEquals("DAMAGED", rows.getString("return_reason"));
                }
            }

            // And the line, through its own view.
            try (PreparedStatement statement = transaction.prepareStatement(
                    "SELECT source_line_id FROM sales_return_names_table WHERE invoice_number = ?")) {
                statement.setInt(1, returnId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(soldLineId, rows.getInt("source_line_id"));
                }
            }
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    @Test
    void theGuardRefusesASecondReturnThatWouldExceedWhatTheFirstLeft() throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            insertSalesHeader(transaction, sales);
            insertSalesLine(transaction, sales, itemId, 5);

            int firstReturn = nextId(transaction, "total_sales_re", "id");
            insertSalesReturnHeader(transaction, firstReturn);
            insertSalesReturnLine(transaction, firstReturn, itemId, 5);
            WRITER.writeSource(DocumentType.SALES_RETURN, firstReturn, sales, null);

            ReturnGuard guard = new ReturnGuard(REPOSITORY);
            com.hamza.account.model.domain.Sales_Return line =
                    new com.hamza.account.model.domain.Sales_Return();
            com.hamza.account.model.domain.ItemsModel item =
                    new com.hamza.account.model.domain.ItemsModel();
            item.setId(itemId);
            line.setItems(item);
            line.setUnitsType(new com.hamza.account.model.domain.UnitsModel(1, "unit", 1));
            line.setQuantity(1);

            org.junit.jupiter.api.Assertions.assertThrows(
                    com.hamza.controlsfx.error.BusinessRuleException.class,
                    () -> guard.validate(DocumentType.SALES_RETURN, sales, 0,
                            com.hamza.account.type.InvoiceType.CASH, 1, List.of(line)));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static int insertItem(Connection connection) throws Exception {
        String marker = "RETURNABLE_REPOSITORY_ACCEPTANCE_" + UUID.randomUUID();
        String sql = "INSERT INTO items(barcode,nameItem,sub_num,buy_price,sel_price1,sel_price2,"
                + "sel_price3,unit_id,mini_quantity,first_balance,user_id) VALUES (?,?,1,1,10,10,10,1,0,50,1)";
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, marker);
            statement.setString(2, marker);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
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

    private static void insertSalesHeader(Connection connection, int invoiceNumber) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                        + "discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                        + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,'returnable-repo-acceptance',1)")) {
            statement.setInt(1, invoiceNumber);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSalesLine(Connection connection, int invoiceNumber, int itemId,
                                        double quantity) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales(invoice_number,num,type,quantity,price,buy_price,"
                        + "total_sel_price,total_buy_price,total_profit,discount,type_value,expiration_date) "
                        + "VALUES (?,?,1,?,10,1,50,5,45,0,1,NULL)")) {
            statement.setInt(1, invoiceNumber);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int insertSalesLineReturningId(Connection connection, int invoiceNumber,
                                                   int itemId, double quantity, double price,
                                                   double buyPrice) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales(invoice_number,num,type,quantity,price,buy_price,"
                        + "total_sel_price,total_buy_price,total_profit,discount,type_value,expiration_date) "
                        + "VALUES (?,?,1,?,?,?,?,?,?,0,1,NULL)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, invoiceNumber);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            statement.setDouble(4, price);
            statement.setDouble(5, buyPrice);
            statement.setDouble(6, quantity * price);
            statement.setDouble(7, quantity * buyPrice);
            statement.setDouble(8, quantity * (price - buyPrice));
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static void insertSalesLineWithExpiry(Connection connection, int invoiceNumber,
                                                   int itemId, double quantity,
                                                   LocalDate expirationDate) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales(invoice_number,num,type,quantity,price,buy_price,"
                        + "total_sel_price,total_buy_price,total_profit,discount,type_value,expiration_date) "
                        + "VALUES (?,?,1,?,10,1,50,5,45,0,1,?)")) {
            statement.setInt(1, invoiceNumber);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            statement.setObject(4, expirationDate);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSalesReturnLineLinked(Connection connection, int invoiceNumber,
                                                     int itemId, double quantity,
                                                     int sourceLineId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales_re(invoice_number,item_id,source_line_id,type,quantity,price,"
                        + "buy_price,total_sel_price,total_buy_price,total_profit,discount,type_value,"
                        + "expiration_date) VALUES (?,?,?,1,?,10,4,?,?,?,0,1,NULL)")) {
            statement.setInt(1, invoiceNumber);
            statement.setInt(2, itemId);
            statement.setInt(3, sourceLineId);
            statement.setDouble(4, quantity);
            statement.setDouble(5, quantity * 10);
            statement.setDouble(6, quantity * 4);
            statement.setDouble(7, quantity * 6);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void raiseItemBuyPrice(Connection connection, int itemId, double buyPrice)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE items SET buy_price = ? WHERE id = ?")) {
            statement.setDouble(1, buyPrice);
            statement.setInt(2, itemId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSalesReturnHeader(Connection connection, int id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO total_sales_re(id,sup_id,invoice_date,invoice_type,total,discount,"
                        + "paid_from_treasury,stock_id,delegate_id,treasury_id,notes,user_id) "
                        + "VALUES (?,1,CURRENT_DATE,1,10,0,10,1,1,1,'returnable-repo-acceptance',1)")) {
            statement.setInt(1, id);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSalesReturnLine(Connection connection, int invoiceNumber,
                                              int itemId, double quantity) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales_re(invoice_number,item_id,type,quantity,price,buy_price,"
                        + "total_sel_price,total_buy_price,total_profit,discount,type_value,"
                        + "expiration_date) VALUES (?,?,1,?,10,1,?,?,?,0,1,NULL)")) {
            statement.setInt(1, invoiceNumber);
            statement.setInt(2, itemId);
            statement.setDouble(3, quantity);
            statement.setDouble(4, quantity * 10);
            statement.setDouble(5, quantity);
            statement.setDouble(6, quantity * 9);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static Integer sourceInvoiceOf(Connection connection, int id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT source_invoice_number FROM total_sales_re WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                int value = rows.getInt(1);
                return rows.wasNull() ? null : value;
            }
        }
    }
}
