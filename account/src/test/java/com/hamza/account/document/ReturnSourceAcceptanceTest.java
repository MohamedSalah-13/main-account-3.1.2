package com.hamza.account.document;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code V16__return_source.sql}'s keys behave the way
 * {@link ReturnSourceMigrationTest} pins them as text: deleting the invoice a return
 * points at clears the link rather than deleting the return or refusing the delete.
 * <p>
 * Real-MySQL acceptance, opt in with {@code -Daccount.db.acceptance=true}, one
 * transaction always rolled back - in the manner of
 * {@code StockLedgerReconciliationAcceptanceTest}.
 */
@EnabledIfSystemProperty(named = "account.db.acceptance", matches = "true")
class ReturnSourceAcceptanceTest {

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
    void deletingTheSourceInvoiceClearsTheReturnsLinkRatherThanBlockingOrCascading()
            throws Exception {
        Connection transaction = ConnectionManager.beginTransaction();
        assertNotNull(transaction);
        try {
            int itemId = insertItem(transaction);
            int sales = nextId(transaction, "total_sales", "invoice_number");
            int salesReturn = nextId(transaction, "total_sales_re", "id");
            insertSalesHeader(transaction, sales);
            int lineId = insertSalesLine(transaction, sales, itemId);
            insertSalesReturnHeader(transaction, salesReturn, sales);
            insertSalesReturnLine(transaction, salesReturn, itemId, lineId);

            assertEquals(sales, sourceInvoiceOf(transaction, salesReturn));
            assertEquals(lineId, sourceLineOf(transaction, salesReturn));

            deleteSalesInvoiceAndItsLine(transaction, sales);

            // The return itself must still be there...
            assertTrue(returnHeaderExists(transaction, salesReturn),
                    "the return must survive the deletion of what it reversed");
            // ...and its link must have gone with the row it pointed at.
            assertNull(sourceInvoiceOf(transaction, salesReturn));
            assertNull(sourceLineOf(transaction, salesReturn));
        } finally {
            transaction.rollback();
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static int insertItem(Connection connection) throws Exception {
        String marker = "RETURN_SOURCE_ACCEPTANCE_" + java.util.UUID.randomUUID();
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
                     "SELECT COALESCE(MAX(" + key + "),0)+4000 FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void insertSalesHeader(Connection connection, int invoiceNumber) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO total_sales(invoice_number,sup_code,invoice_type,invoice_date,total,"
                        + "discount,paid_up,stock_id,delegate_id,treasury_id,notes,user_id) "
                        + "VALUES (?,1,1,CURRENT_DATE,10,0,10,1,1,1,'return-source-acceptance',1)")) {
            statement.setInt(1, invoiceNumber);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int insertSalesLine(Connection connection, int invoiceNumber, int itemId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales(invoice_number,num,type,quantity,price,buy_price,"
                        + "total_sel_price,total_buy_price,total_profit,discount,type_value,expiration_date) "
                        + "VALUES (?,?,1,5,10,1,50,5,45,0,1,NULL)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, invoiceNumber);
            statement.setInt(2, itemId);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static void insertSalesReturnHeader(Connection connection, int id, int sourceInvoice)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO total_sales_re(id,source_invoice_number,sup_id,invoice_date,invoice_type,"
                        + "total,discount,paid_from_treasury,stock_id,delegate_id,treasury_id,notes,user_id) "
                        + "VALUES (?,?,1,CURRENT_DATE,1,10,0,10,1,1,1,'return-source-acceptance',1)")) {
            statement.setInt(1, id);
            statement.setInt(2, sourceInvoice);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void insertSalesReturnLine(Connection connection, int invoiceNumber,
                                              int itemId, int sourceLineId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sales_re(invoice_number,item_id,source_line_id,type,quantity,price,"
                        + "buy_price,total_sel_price,total_buy_price,total_profit,discount,type_value,"
                        + "expiration_date) VALUES (?,?,?,1,2,10,1,20,2,18,0,1,NULL)")) {
            statement.setInt(1, invoiceNumber);
            statement.setInt(2, itemId);
            statement.setInt(3, sourceLineId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void deleteSalesInvoiceAndItsLine(Connection connection, int invoiceNumber)
            throws Exception {
        // sales.invoice_number -> total_sales is itself ON DELETE CASCADE (V1), so
        // deleting the header is enough - the line goes with it, and it is the line's
        // disappearance that must clear sales_re.source_line_id.
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM total_sales WHERE invoice_number = ?")) {
            statement.setInt(1, invoiceNumber);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static boolean returnHeaderExists(Connection connection, int id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM total_sales_re WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
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

    private static Integer sourceLineOf(Connection connection, int returnInvoiceNumber)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT source_line_id FROM sales_re WHERE invoice_number = ?")) {
            statement.setInt(1, returnInvoiceNumber);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                int value = rows.getInt(1);
                return rows.wasNull() ? null : value;
            }
        }
    }
}
