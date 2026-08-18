package com.hamza.account.model.dao;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.model.domain.Total_buy;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.model.domain.Users;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A golden master of what the eight invoice DAOs write, taken before they are merged
 * into one document repository.
 * <p>
 * There is no database here and none is needed: the whole contract that separates the
 * four families is the statement text and the order of the array bound to it, and both
 * are built in memory. What this pins is exactly what a merge gets wrong quietly - a
 * repository driven by a table specification produces a statement that still runs, so a
 * column swapped with its neighbour is not a crash but a discount saved as a stock id.
 * <p>
 * The values below are transcribed from the DAOs as they stand. If a change here is
 * intended, the statement it pins changed too, and the migration or the screens that
 * read the column have to change with it.
 */
class DocumentDaoStatementsTest {

    private static final DaoFactory FACTORY = DaoFactory.INSTANCE;

    /** Distinct on purpose: a value in the wrong slot has to look wrong. */
    private static final String DATE = "2026-08-11";
    private static final int PARTY_ID = 7;
    private static final int STOCK_ID = 3;
    private static final int DELEGATE_ID = 9;
    private static final int TREASURY_ID = 4;
    private static final int USER_ID = 8;
    private static final int INVOICE_ID = 55;
    private static final String NOTES = "ملاحظة";

    // ---- helpers ------------------------------------------------------------------

    /**
     * Every parameter of the statement is filled by the array, and no value in the
     * array is left over. Off by one here is the failure mode a merge introduces.
     */
    private static void assertBindsExactly(String sql, Object[] data) {
        long placeholders = sql.chars().filter(c -> c == '?').count();
        assertEquals(placeholders, data.length,
                "the statement takes " + placeholders + " parameters but " + data.length + " were bound: " + sql);
    }

    private static Total_Sales salesInvoice() {
        Total_Sales invoice = new Total_Sales();
        invoice.setId(INVOICE_ID);
        invoice.setCustomers(new Customers(PARTY_ID, "عميل"));
        invoice.setInvoiceType(InvoiceType.CASH);
        invoice.setDate(DATE);
        invoice.setTotal(100.0);
        invoice.setDiscount(5.0);
        invoice.setPaid(60.0);
        invoice.setStockData(new Stock(STOCK_ID, "مخزن"));
        invoice.setEmployeeObject(new Employees(DELEGATE_ID, "مندوب"));
        invoice.setTreasuryModel(new Treasury(TREASURY_ID, "خزينة", BigDecimal.ZERO));
        invoice.setNotes(NOTES);
        invoice.setUsers(new Users(USER_ID, "admin"));
        return invoice;
    }

    private static Total_buy purchaseInvoice() {
        Total_buy invoice = new Total_buy();
        invoice.setId(INVOICE_ID);
        invoice.setSupplierData(new Suppliers(PARTY_ID, "مورد"));
        invoice.setInvoiceType(InvoiceType.CASH);
        invoice.setDate(DATE);
        invoice.setTotal(100.0);
        invoice.setDiscount(5.0);
        invoice.setPaid(60.0);
        invoice.setStockData(new Stock(STOCK_ID, "مخزن"));
        invoice.setTreasuryModel(new Treasury(TREASURY_ID, "خزينة", BigDecimal.ZERO));
        invoice.setNotes(NOTES);
        invoice.setUsers(new Users(USER_ID, "admin"));
        return invoice;
    }

    private static Total_Sales_Re salesReturn() {
        Total_Sales_Re invoice = new Total_Sales_Re();
        invoice.setId(INVOICE_ID);
        invoice.setCustomer(new Customers(PARTY_ID, "عميل"));
        invoice.setInvoiceType(InvoiceType.CASH);
        invoice.setDate(DATE);
        invoice.setTotal(100.0);
        invoice.setDiscount(5.0);
        invoice.setPaid(60.0);
        invoice.setStockData(new Stock(STOCK_ID, "مخزن"));
        invoice.setEmployeeObject(new Employees(DELEGATE_ID, "مندوب"));
        invoice.setTreasuryModel(new Treasury(TREASURY_ID, "خزينة", BigDecimal.ZERO));
        invoice.setNotes(NOTES);
        invoice.setUsers(new Users(USER_ID, "admin"));
        return invoice;
    }

    private static Total_Buy_Re purchaseReturn() {
        Total_Buy_Re invoice = new Total_Buy_Re();
        invoice.setId(INVOICE_ID);
        invoice.setSuppliers(new Suppliers(PARTY_ID, "مورد"));
        invoice.setInvoiceType(InvoiceType.CASH);
        invoice.setDate(DATE);
        invoice.setTotal(100.0);
        invoice.setDiscount(5.0);
        invoice.setPaid(60.0);
        invoice.setStockData(new Stock(STOCK_ID, "مخزن"));
        invoice.setTreasuryModel(new Treasury(TREASURY_ID, "خزينة", BigDecimal.ZERO));
        invoice.setNotes(NOTES);
        invoice.setUsers(new Users(USER_ID, "admin"));
        return invoice;
    }

    /** A unit with a factor that is neither 1 nor the quantity, so a swap shows. */
    private static UnitsModel carton() {
        return new UnitsModel(2, "كرتونة", 12.0);
    }

    private static ItemsModel item() {
        return new ItemsModel(31, "1234567", "صنف");
    }

    // ---- the sale ------------------------------------------------------------------

    @Nested
    @DisplayName("Sales: total_sales and sales")
    class SalesFamily {

        private final TotalsSalesDao dao = FACTORY.totalsSalesDao();
        private final SalesDao lines = FACTORY.salesDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO total_sales (sup_code,invoice_type,invoice_date,total,discount,paid_up,"
                    + "stock_id,delegate_id,treasury_id,notes,invoice_number,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    dao.insertSql());
            assertEquals("UPDATE total_sales SET sup_code=?,invoice_type=?,invoice_date=?,total=?,discount=?,"
                    + "paid_up=?,stock_id=?,delegate_id=?,treasury_id=?,notes=? WHERE invoice_number=?",
                    dao.updateSql());
            assertEquals("DELETE FROM total_sales WHERE invoice_number=?", dao.deleteSql());
            assertEquals("SELECT * FROM total_sales_names_table WHERE invoice_number=?", dao.selectByIdSql());
            assertEquals("SELECT COALESCE(MAX(invoice_number), 0) + 1 FROM total_sales", dao.maxIdSql());
        }

        @Test
        void queries() {
            assertEquals("SELECT * FROM total_sales_names_table", dao.selectAllSql());
            assertEquals("SELECT * FROM total_sales_names_table WHERE invoice_date BETWEEN ? AND ?",
                    dao.selectBetweenDatesSql());
            assertEquals("SELECT * FROM total_sales_names_table WHERE sup_code = ?", dao.selectByPartySql());
            assertEquals("SELECT * FROM total_sales_names_table WHERE YEAR(invoice_date) = ?", dao.selectByYearSql());
            assertEquals("DELETE FROM total_sales WHERE invoice_number IN (?,?,?)", dao.deleteInRangeSql(3));
        }

        @Test
        void lineQueries() {
            assertEquals("SELECT * FROM sales_names_table", lines.selectAllSql());
            assertEquals("SELECT * FROM sales_names_table WHERE invoice_number=?", lines.selectByDocumentSql());
            assertEquals("SELECT * FROM sales_names_table WHERE invoice_number BETWEEN ? AND ?",
                    lines.selectBetweenDocumentsSql());
            assertEquals("SELECT * FROM sales_names_table WHERE num = ?", lines.selectByItemSql());
            assertEquals("DELETE FROM sales WHERE id=?", lines.deleteSql());
            assertEquals("SELECT id FROM sales WHERE invoice_number=? FOR UPDATE", lines.lineIdsForUpdateSql());
            assertEquals("UPDATE sales SET num=?,type=?,quantity=?,price=?,buy_price=?,total_sel_price=?,"
                    + "total_buy_price=?,total_profit=?,discount=?,type_value=?,expiration_date=? "
                    + "WHERE id=? AND invoice_number=?", lines.lineUpdateSql());
            assertEquals("DELETE FROM sales WHERE id=? AND invoice_number=?", lines.lineDeleteOwnedSql());
        }

        @Test
        void insertParameters() throws DaoException {
            Object[] data = dao.getData(salesInvoice());
            assertBindsExactly(dao.insertSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, 1, DATE, 100.0, 5.0, 60.0, STOCK_ID, DELEGATE_ID, TREASURY_ID, NOTES,
                    INVOICE_ID, USER_ID}, data);
        }

        /** The update omits the user and puts the key last - it is not the insert's order. */
        @Test
        void updateParameters() {
            Object[] data = dao.getUpdateData(salesInvoice());
            assertBindsExactly(dao.updateSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, 1, DATE, 100.0, 5.0, 60.0, STOCK_ID, DELEGATE_ID, TREASURY_ID, NOTES,
                    INVOICE_ID}, data);
        }

        @Test
        void lineStatement() {
            assertEquals("INSERT INTO sales (invoice_number,num,type,quantity,price,buy_price,total_sel_price,"
                    + "total_buy_price,total_profit,discount,type_value,expiration_date) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", lines.insertListSql());
        }

        @Test
        void lineParameters() throws DaoException {
            Sales line = new Sales();
            line.setInvoiceNumber(INVOICE_ID);
            line.setItems(item());
            line.setUnitsType(carton());
            line.setQuantity(2.0);
            line.setPrice(50.0);
            line.setBuy_price(40.0);
            line.setTotalSelPrice(BigDecimal.valueOf(100));
            line.setTotal_buy_price(80.0);
            line.setTotal_profit(BigDecimal.valueOf(20));
            line.setDiscount(5.0);
            line.setExpiration_date(LocalDate.of(2027, 1, 31));

            Object[] data = lines.getData(line);
            assertBindsExactly(lines.insertListSql(), data);
            assertArrayEquals(new Object[]{
                    INVOICE_ID, 31, 2, 2.0, 50.0, 40.0, BigDecimal.valueOf(100), 80.0, BigDecimal.valueOf(20),
                    5.0, 12.0, LocalDate.of(2027, 1, 31)}, data);
        }

        /**
         * The factor the line was written in, not the item's factor today. A later
         * change to the unit must not restate what this invoice meant.
         */
        @Test
        void theLineStoresTheFactorItUsed() {
            Sales line = new Sales();
            line.setUnitsType(carton());
            assertEquals(12.0, line.getUnitsType().getValue());
        }
    }

    // ---- the purchase ---------------------------------------------------------------

    @Nested
    @DisplayName("Purchase: total_buy and purchase")
    class PurchaseFamily {

        private final TotalsBuyDao dao = FACTORY.totalsPurchaseDao();
        private final PurchaseDao lines = FACTORY.purchaseDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO total_buy (sup_code,invoice_type,invoice_date,total,discount,paid_up,"
                    + "stock_id,treasury_id,notes,user_id,invoice_number) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    dao.insertSql());
            assertEquals("UPDATE total_buy SET sup_code=?,invoice_type=?,invoice_date=?,total=?,discount=?,"
                    + "paid_up=?,stock_id=?,treasury_id=?,notes=? WHERE invoice_number=?", dao.updateSql());
            assertEquals("DELETE FROM total_buy WHERE invoice_number=?", dao.deleteSql());
            assertEquals("SELECT * FROM total_purchase_names_table WHERE invoice_number=?", dao.selectByIdSql());
            assertEquals("SELECT COALESCE(MAX(invoice_number), 0) + 1 FROM total_buy", dao.maxIdSql());
        }

        @Test
        void queries() {
            assertEquals("SELECT * FROM total_purchase_names_table", dao.selectAllSql());
            assertEquals("SELECT * FROM total_purchase_names_table WHERE invoice_date BETWEEN ? AND ?",
                    dao.selectBetweenDatesSql());
            assertEquals("SELECT * FROM total_purchase_names_table WHERE sup_code = ?", dao.selectByPartySql());
            assertEquals("SELECT * FROM total_purchase_names_table WHERE YEAR(invoice_date) = ?", dao.selectByYearSql());
            assertEquals("DELETE FROM total_buy WHERE invoice_number IN (?,?,?)", dao.deleteInRangeSql(3));
        }

        @Test
        void lineQueries() {
            assertEquals("SELECT * FROM purchase_names_table", lines.selectAllSql());
            assertEquals("SELECT * FROM purchase_names_table WHERE invoice_number=?", lines.selectByDocumentSql());
            assertEquals("SELECT * FROM purchase_names_table WHERE invoice_number BETWEEN ? AND ?",
                    lines.selectBetweenDocumentsSql());
            assertEquals("SELECT * FROM purchase_names_table WHERE num = ?", lines.selectByItemSql());
            assertEquals("DELETE FROM purchase WHERE id=?", lines.deleteSql());
            assertEquals("SELECT id FROM purchase WHERE invoice_number=? FOR UPDATE", lines.lineIdsForUpdateSql());
            assertEquals("UPDATE purchase SET num=?,type=?,quantity=?,price=?,discount=?,type_value=?,"
                    + "expiration_date=? WHERE id=? AND invoice_number=?", lines.lineUpdateSql());
            assertEquals("DELETE FROM purchase WHERE id=? AND invoice_number=?", lines.lineDeleteOwnedSql());
        }

        /**
         * Every year any document was written in, which names all four header tables and
         * happens to live on this DAO. Pinned because a merge has to keep answering it.
         */
        @Test
        void theYearListSpansAllFourTables() {
            String sql = TotalsBuyDao.yearsSql();
            for (String table : new String[]{"total_sales", "total_sales_re", "total_buy", "total_buy_re"}) {
                assertEquals(true, sql.contains("FROM " + table + "\n") || sql.contains("FROM " + table + ";"),
                        table + " is missing from the year list");
            }
        }

        /**
         * The same twelve facts as the sale, less the delegate, and with the user and
         * the invoice number in the opposite order. Nothing makes that difference
         * necessary - it is the kind of drift a single document table would end.
         */
        @Test
        void insertParameters() throws DaoException {
            Object[] data = dao.getData(purchaseInvoice());
            assertBindsExactly(dao.insertSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, 1, DATE, 100.0, 5.0, 60.0, STOCK_ID, TREASURY_ID, NOTES, USER_ID, INVOICE_ID}, data);
        }

        @Test
        void updateParameters() {
            Object[] data = dao.getUpdateData(purchaseInvoice());
            assertBindsExactly(dao.updateSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, 1, DATE, 100.0, 5.0, 60.0, STOCK_ID, TREASURY_ID, NOTES, INVOICE_ID}, data);
        }

        @Test
        void lineStatement() {
            assertEquals("INSERT INTO purchase (invoice_number,num,type,quantity,price,discount,type_value,"
                    + "expiration_date) VALUES (?,?,?,?,?,?,?,?)", lines.insertListSql());
        }

        @Test
        void lineParameters() throws DaoException {
            Purchase line = new Purchase();
            line.setInvoiceNumber(INVOICE_ID);
            line.setItems(item());
            line.setUnitsType(carton());
            line.setQuantity(2.0);
            line.setPrice(50.0);
            line.setDiscount(5.0);
            line.setExpiration_date(LocalDate.of(2027, 1, 31));

            Object[] data = lines.getData(line);
            assertBindsExactly(lines.insertListSql(), data);
            assertArrayEquals(new Object[]{
                    INVOICE_ID, 31, 2, 2.0, 50.0, 5.0, 12.0, LocalDate.of(2027, 1, 31)}, data);
        }
    }

    // ---- the sales return -----------------------------------------------------------

    @Nested
    @DisplayName("Sales return: total_sales_re and sales_re")
    class SalesReturnFamily {

        private final TotalsSalesReturnDao dao = FACTORY.totalsSalesReturnDao();
        private final SalesReturnDao lines = FACTORY.salesReturnsDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO total_sales_re (sup_id,invoice_date,invoice_type,total,discount,"
                    + "paid_from_treasury,stock_id,delegate_id,treasury_id,id,notes,user_id) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", dao.insertSql());
            assertEquals("UPDATE total_sales_re SET sup_id=?,invoice_date=?,invoice_type=?,total=?,discount=?,"
                    + "paid_from_treasury=?,stock_id=?,delegate_id=?,treasury_id=?,notes=? WHERE id=?",
                    dao.updateSql());
            assertEquals("DELETE FROM total_sales_re WHERE id=?", dao.deleteSql());
            assertEquals("SELECT * FROM total_sales_return_names_table WHERE id=?", dao.selectByIdSql());
            assertEquals("SELECT COALESCE(MAX(id), 0) + 1 FROM total_sales_re", dao.maxIdSql());
        }

        @Test
        void queries() {
            assertEquals("SELECT * FROM total_sales_return_names_table", dao.selectAllSql());
            assertEquals("SELECT * FROM total_sales_return_names_table WHERE invoice_date BETWEEN ? AND ?",
                    dao.selectBetweenDatesSql());
            assertEquals("SELECT * FROM total_sales_return_names_table WHERE sup_id = ?", dao.selectByPartySql());
            assertEquals("SELECT * FROM total_sales_return_names_table WHERE YEAR(invoice_date) = ?",
                    dao.selectByYearSql());
            assertEquals("DELETE FROM total_sales_re WHERE id IN (?,?,?)", dao.deleteInRangeSql(3));
        }

        @Test
        void lineQueries() {
            assertEquals("SELECT * FROM sales_return_names_table", lines.selectAllSql());
            assertEquals("SELECT * FROM sales_return_names_table WHERE invoice_number=?", lines.selectByDocumentSql());
            assertEquals("SELECT * FROM sales_return_names_table WHERE invoice_number BETWEEN ? AND ?",
                    lines.selectBetweenDocumentsSql());
            assertEquals("SELECT * FROM sales_return_names_table WHERE item_id = ?", lines.selectByItemSql());
            assertEquals("DELETE FROM sales_re WHERE id=?", lines.deleteSql());
            assertEquals("SELECT id FROM sales_re WHERE invoice_number=? FOR UPDATE", lines.lineIdsForUpdateSql());
            assertEquals("UPDATE sales_re SET item_id=?,type=?,quantity=?,price=?,buy_price=?,total_sel_price=?,"
                    + "total_buy_price=?,total_profit=?,discount=?,type_value=?,expiration_date=?,"
                    + "source_line_id=? WHERE id=? AND invoice_number=?", lines.lineUpdateSql());
            assertEquals("DELETE FROM sales_re WHERE id=? AND invoice_number=?", lines.lineDeleteOwnedSql());
        }

        /**
         * The date and the type are the other way round from the sale, and the key sits
         * between the treasury and the notes rather than after them.
         */
        @Test
        void insertParameters() throws DaoException {
            Object[] data = dao.getData(salesReturn());
            assertBindsExactly(dao.insertSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, DATE, 1, 100.0, 5.0, 60.0, STOCK_ID, DELEGATE_ID, TREASURY_ID, INVOICE_ID,
                    NOTES, USER_ID}, data);
        }

        @Test
        void updateParameters() {
            Object[] data = dao.getUpdateData(salesReturn());
            assertBindsExactly(dao.updateSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, DATE, 1, 100.0, 5.0, 60.0, STOCK_ID, DELEGATE_ID, TREASURY_ID, NOTES,
                    INVOICE_ID}, data);
        }

        @Test
        void lineStatement() {
            assertEquals("INSERT INTO sales_re (invoice_number,item_id,type,quantity,price,buy_price,"
                    + "total_sel_price,total_buy_price,total_profit,discount,type_value,expiration_date,"
                    + "source_line_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", lines.insertListSql());
        }

        @Test
        void lineParameters() {
            Sales_Return line = new Sales_Return();
            line.setInvoiceNumber(INVOICE_ID);
            line.setItems(item());
            line.setUnitsType(carton());
            line.setQuantity(2.0);
            line.setPrice(50.0);
            line.setBuy_price(40.0);
            line.setTotalSelPrice(BigDecimal.valueOf(100));
            line.setTotal_buy_price(80.0);
            line.setTotal_profit(BigDecimal.valueOf(20));
            line.setDiscount(5.0);
            line.setExpiration_date(LocalDate.of(2027, 1, 31));

            Object[] data = lines.lineData(line);
            assertBindsExactly(lines.insertListSql(), data);
            assertArrayEquals(new Object[]{
                    INVOICE_ID, 31, 2, 2.0, 50.0, 40.0, BigDecimal.valueOf(100), 80.0, BigDecimal.valueOf(20),
                    5.0, 12.0, LocalDate.of(2027, 1, 31),
                    // A free return: sourceLineId is 0, and the column is a foreign
                    // key, so it must reach the database as NULL, not line zero.
                    null}, data);
        }
    }

    // ---- the purchase return ---------------------------------------------------------

    @Nested
    @DisplayName("Purchase return: total_buy_re and purchase_re")
    class PurchaseReturnFamily {

        private final TotalsPurchaseReturnDao dao = FACTORY.totalsBuyReturnDao();
        private final PurchaseReturnDao lines = FACTORY.purchaseReturnsDao();

        @Test
        void statements() {
            assertEquals("INSERT INTO total_buy_re (sup_id,invoice_date,invoice_type,total,discount,"
                    + "paid_to_treasury,stock_id,treasury_id,id,notes,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    dao.insertSql());
            assertEquals("UPDATE total_buy_re SET sup_id=?,invoice_date=?,invoice_type=?,total=?,discount=?,"
                    + "paid_to_treasury=?,stock_id=?,treasury_id=?,notes=? WHERE id=?", dao.updateSql());
            assertEquals("DELETE FROM total_buy_re WHERE id=?", dao.deleteSql());
            assertEquals("SELECT * FROM total_purchase_return_names_table WHERE id=?", dao.selectByIdSql());
            assertEquals("SELECT COALESCE(MAX(id), 0) + 1 FROM total_buy_re", dao.maxIdSql());
        }

        @Test
        void queries() {
            assertEquals("SELECT * FROM total_purchase_return_names_table", dao.selectAllSql());
            assertEquals("SELECT * FROM total_purchase_return_names_table WHERE invoice_date BETWEEN ? AND ?",
                    dao.selectBetweenDatesSql());
            assertEquals("SELECT * FROM total_purchase_return_names_table WHERE sup_id = ?", dao.selectByPartySql());
            assertEquals("SELECT * FROM total_purchase_return_names_table WHERE YEAR(invoice_date) = ?",
                    dao.selectByYearSql());
            assertEquals("DELETE FROM total_buy_re WHERE id IN (?,?,?)", dao.deleteInRangeSql(3));
        }

        @Test
        void lineQueries() {
            assertEquals("SELECT * FROM purchase_return_names_table", lines.selectAllSql());
            assertEquals("SELECT * FROM purchase_return_names_table WHERE invoice_number=?", lines.selectByDocumentSql());
            assertEquals("SELECT * FROM purchase_return_names_table WHERE invoice_number BETWEEN ? AND ?",
                    lines.selectBetweenDocumentsSql());
            assertEquals("SELECT * FROM purchase_return_names_table WHERE item_id = ?", lines.selectByItemSql());
            assertEquals("DELETE FROM purchase_re WHERE id=?", lines.deleteSql());
            assertEquals("SELECT id FROM purchase_re WHERE invoice_number=? FOR UPDATE", lines.lineIdsForUpdateSql());
            assertEquals("UPDATE purchase_re SET item_id=?,type=?,quantity=?,price=?,discount=?,type_value=?,"
                    + "expiration_date=?,source_line_id=? WHERE id=? AND invoice_number=?", lines.lineUpdateSql());
            assertEquals("DELETE FROM purchase_re WHERE id=? AND invoice_number=?", lines.lineDeleteOwnedSql());
        }

        @Test
        void insertParameters() throws DaoException {
            Object[] data = dao.getData(purchaseReturn());
            assertBindsExactly(dao.insertSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, DATE, 1, 100.0, 5.0, 60.0, STOCK_ID, TREASURY_ID, INVOICE_ID, NOTES, USER_ID}, data);
        }

        @Test
        void updateParameters() {
            Object[] data = dao.getUpdateData(purchaseReturn());
            assertBindsExactly(dao.updateSql(), data);
            assertArrayEquals(new Object[]{
                    PARTY_ID, DATE, 1, 100.0, 5.0, 60.0, STOCK_ID, TREASURY_ID, NOTES, INVOICE_ID}, data);
        }

        @Test
        void lineStatement() {
            assertEquals("INSERT INTO purchase_re (invoice_number,item_id,type,quantity,price,discount,"
                    + "type_value,expiration_date,source_line_id) VALUES (?,?,?,?,?,?,?,?,?)", lines.insertListSql());
        }

        @Test
        void lineParameters() {
            Purchase_Return line = new Purchase_Return();
            line.setInvoiceNumber(INVOICE_ID);
            line.setItems(item());
            line.setUnitsType(carton());
            line.setQuantity(2.0);
            line.setPrice(50.0);
            line.setDiscount(5.0);
            line.setExpiration_date(LocalDate.of(2027, 1, 31));

            Object[] data = lines.lineData(line);
            assertBindsExactly(lines.insertListSql(), data);
            assertArrayEquals(new Object[]{
                    INVOICE_ID, 31, 2, 2.0, 50.0, 5.0, 12.0, LocalDate.of(2027, 1, 31),
                    // A free return: sourceLineId is 0, and the column is a foreign
                    // key, so it must reach the database as NULL, not line zero.
                    null}, data);
        }
    }

    // ---- what the families have in common, and what they do not ----------------------

    @Nested
    @DisplayName("Across the four families")
    class AcrossFamilies {

        /**
         * Each DAO writes the table its document type declares to the period lock. That
         * is the only place the two agree today, and the repository that replaces them
         * has to keep them agreeing.
         */
        @Test
        void eachDaoWritesTheTableItsDocumentTypeLocks() {
            assertEquals(DocumentType.SALES.periodLock().table(), tableOf(FACTORY.totalsSalesDao().insertSql()));
            assertEquals(DocumentType.PURCHASE.periodLock().table(), tableOf(FACTORY.totalsPurchaseDao().insertSql()));
            assertEquals(DocumentType.SALES_RETURN.periodLock().table(), tableOf(FACTORY.totalsSalesReturnDao().insertSql()));
            assertEquals(DocumentType.PURCHASE_RETURN.periodLock().table(), tableOf(FACTORY.totalsBuyReturnDao().insertSql()));
        }

        /**
         * The key each family is numbered by, which is also the column its lines point
         * at. {@code invoice_number} on the two invoices, {@code id} on the two returns.
         */
        @Test
        void eachDaoIsKeyedByTheColumnItsDocumentTypeDeclares() {
            assertEquals("DELETE FROM total_sales WHERE " + DocumentType.SALES.periodLock().idColumn() + "=?",
                    FACTORY.totalsSalesDao().deleteSql());
            assertEquals("DELETE FROM total_buy WHERE " + DocumentType.PURCHASE.periodLock().idColumn() + "=?",
                    FACTORY.totalsPurchaseDao().deleteSql());
            assertEquals("DELETE FROM total_sales_re WHERE " + DocumentType.SALES_RETURN.periodLock().idColumn() + "=?",
                    FACTORY.totalsSalesReturnDao().deleteSql());
            assertEquals("DELETE FROM total_buy_re WHERE " + DocumentType.PURCHASE_RETURN.periodLock().idColumn() + "=?",
                    FACTORY.totalsBuyReturnDao().deleteSql());
        }

        /**
         * Only the sales side writes a delegate, which is what
         * {@link DocumentType#hasDelegate()} says without having to read four
         * statements.
         */
        @Test
        void onlyTheSalesSideWritesADelegate() {
            assertEquals(DocumentType.SALES.hasDelegate(),
                    FACTORY.totalsSalesDao().insertSql().contains("delegate_id"));
            assertEquals(DocumentType.SALES_RETURN.hasDelegate(),
                    FACTORY.totalsSalesReturnDao().insertSql().contains("delegate_id"));
            assertEquals(DocumentType.PURCHASE.hasDelegate(),
                    FACTORY.totalsPurchaseDao().insertSql().contains("delegate_id"));
            assertEquals(DocumentType.PURCHASE_RETURN.hasDelegate(),
                    FACTORY.totalsBuyReturnDao().insertSql().contains("delegate_id"));
        }

        /**
         * The paid column is spelled three ways for one fact - what the invoice settled
         * in cash. Pinned so the merge has to decide deliberately what the single column
         * is called, rather than discovering the difference at runtime.
         */
        @Test
        void thePaidColumnIsSpelledThreeWays() {
            assertContainsColumn(FACTORY.totalsSalesDao().insertSql(), "paid_up");
            assertContainsColumn(FACTORY.totalsPurchaseDao().insertSql(), "paid_up");
            assertContainsColumn(FACTORY.totalsSalesReturnDao().insertSql(), "paid_from_treasury");
            assertContainsColumn(FACTORY.totalsBuyReturnDao().insertSql(), "paid_to_treasury");
        }

        /** And the item column two ways: {@code num} on the invoices, {@code item_id} on the returns. */
        @Test
        void theItemColumnIsSpelledTwoWays() {
            assertContainsColumn(FACTORY.salesDao().insertListSql(), "num");
            assertContainsColumn(FACTORY.purchaseDao().insertListSql(), "num");
            assertContainsColumn(FACTORY.salesReturnsDao().insertListSql(), "item_id");
            assertContainsColumn(FACTORY.purchaseReturnsDao().insertListSql(), "item_id");
        }

        /**
         * Every line table carries the factor the row was written in. The stock balance
         * is {@code quantity * type_value}, so a line insert that dropped it would read
         * back as a movement of the wrong size.
         */
        @Test
        void everyLineCarriesItsFactor() {
            assertContainsColumn(FACTORY.salesDao().insertListSql(), "type_value");
            assertContainsColumn(FACTORY.purchaseDao().insertListSql(), "type_value");
            assertContainsColumn(FACTORY.salesReturnsDao().insertListSql(), "type_value");
            assertContainsColumn(FACTORY.purchaseReturnsDao().insertListSql(), "type_value");
        }

        private static String tableOf(String insertStatement) {
            int start = "INSERT INTO ".length();
            return insertStatement.substring(start, insertStatement.indexOf(' ', start));
        }

        private static void assertContainsColumn(String sql, String column) {
            String columns = sql.substring(sql.indexOf('(') + 1, sql.indexOf(')'));
            boolean found = false;
            for (String each : columns.split(",")) {
                found |= each.equals(column);
            }
            assertEquals(true, found, column + " is not a column of: " + sql);
        }
    }
}
