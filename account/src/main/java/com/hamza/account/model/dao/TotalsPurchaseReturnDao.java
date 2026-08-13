package com.hamza.account.model.dao;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.document.DocumentWriteGuard;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TotalsPurchaseReturnDao extends AbstractDao<Total_Buy_Re> {

    /** Which document this DAO writes. The period lock it must respect follows from it. */
    static final DocumentType DOCUMENT_TYPE = DocumentType.PURCHASE_RETURN;

    /** Where it lives, and every statement that reads or writes it. */
    static final DocumentTableSpec SPEC = DocumentTableSpec.of(DOCUMENT_TYPE);

    // The names map() reads the result set by - see TotalsSalesDao.
    private final String TABLE_VIEW = SPEC.view();
    private final String ID = SPEC.key();
    private final String SUP_ID = SPEC.party();
    private final String INVOICE_DATE = SPEC.dateColumn();
    private final String PAID_TO_TREASURY = SPEC.paid();
    private final String DATE_INSERT = "date_insert";
    private final String TOTAL = "total";
    private final String DISCOUNT = "discount";
    private final String DISCOUNT_TYPE = "discount_type";
    private final String STOCK_ID = "stock_id";
    private final String TREASURY_ID = "treasury_id";
    private final String INVOICE_TYPE = "invoice_type";
    private final String NOTES = "notes";
    private final String USER_ID = "user_id";
    private final PurchaseReturnDao returnPurchaseDao;
    private final DaoFactory daoFactory;

    TotalsPurchaseReturnDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
        this.returnPurchaseDao = daoFactory.purchaseReturnsDao();
    }

    @Override
    public List<Total_Buy_Re> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<Total_Buy_Re> loadAllById(int id) throws DaoException {
        return queryForObjects(selectByIdSql(), this::map, id);
    }

    @Override
    public List<Total_Buy_Re> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        return queryForObjects(selectBetweenDatesSql(), this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_Buy_Re totalBuyRe) throws DaoException {
        // See TotalsSalesDao.insert: enforced here so it holds for every caller.
        PeriodLock.require(totalBuyRe.getDate(), DOCUMENT_TYPE.periodLock().label());
        String query = insertSql();
        return insertMultiData(() -> {
            // first, insert data to total
            DocumentWriteGuard.requireSingleHeaderRow(
                    executeUpdateWithException(query, getData(totalBuyRe)), DOCUMENT_TYPE);
            // Secondly, enter the purchase data.
            returnPurchaseDao.insertList(totalBuyRe.getPurchaseReturnList());
        });
    }

    @Override
    public int update(Total_Buy_Re totalBuyRe) throws DaoException {
        PeriodLock.requireMove(DOCUMENT_TYPE.periodLock(), totalBuyRe.getId(), totalBuyRe.getDate());
        String query = updateSql();
        return insertMultiData(() -> {
            Object[] objects = getUpdateData(totalBuyRe);
            DocumentWriteGuard.requireSingleHeaderRow(
                    executeUpdateWithException(query, objects), DOCUMENT_TYPE);
            returnPurchaseDao.synchronizeLines(
                    totalBuyRe.getId(), totalBuyRe.getPurchaseReturnList());
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }

    @Override
    public Total_Buy_Re getDataById(int id) throws DaoException {
        return queryForObject(selectByIdSql(), this::map, id);
    }

    // ---- the statements, and the order their parameters go in -------------------
    // See TotalsSalesDao. Note the third spelling of the paid column: paid_up on the
    // two invoice tables, paid_from_treasury on the sales return, paid_to_treasury here.

    String selectAllSql() {
        return SPEC.selectAllSql();
    }

    String selectBetweenDatesSql() {
        return SPEC.selectBetweenDatesSql();
    }

    String selectByPartySql() {
        return SPEC.selectByPartySql();
    }

    String selectByYearSql() {
        return SPEC.selectByYearSql();
    }

    String deleteInRangeSql(int count) {
        return SPEC.deleteInRangeSql(count);
    }

    String insertSql() {
        return SPEC.insertSql();
    }

    String updateSql() {
        return SPEC.updateSql();
    }

    String deleteSql() {
        return SPEC.deleteSql();
    }

    String selectByIdSql() {
        return SPEC.selectByIdSql();
    }

    String maxIdSql() {
        return SPEC.maxIdSql();
    }

    /** The parameters of {@link #updateSql()}, which is not the order {@link #getData} uses. */
    Object[] getUpdateData(Total_Buy_Re totalBuyRe) {
        return new Object[]{totalBuyRe.getSuppliers().getId()
                , totalBuyRe.getDate()
                , totalBuyRe.getInvoiceType().getId()
                , totalBuyRe.getTotal()
                , totalBuyRe.getDiscount()
                , totalBuyRe.getPaid()
                , totalBuyRe.getStockData().getId()
                , totalBuyRe.getTreasuryModel().getId()
                , totalBuyRe.getNotes()
//                    , totalBuyRe.getTotalBuyId()
                , totalBuyRe.getId()};
    }

    @Override
    public Object[] getData(Total_Buy_Re totalBuyRe) throws DaoException {
        return new Object[]{totalBuyRe.getSuppliers().getId()
                , totalBuyRe.getDate()
                , totalBuyRe.getInvoiceType().getId()
                , totalBuyRe.getTotal()
                , totalBuyRe.getDiscount()
                , totalBuyRe.getPaid()
                , totalBuyRe.getStockData().getId()
                , totalBuyRe.getTreasuryModel().getId()
                , totalBuyRe.getId()
                , totalBuyRe.getNotes()
//                , totalBuyRe.getTotalBuyId()
                , totalBuyRe.getUsers().getId()
        };
    }

    @Override
    public Total_Buy_Re map(ResultSet rs) throws DaoException {
        Total_Buy_Re totalBuyRe;
        try {
            String date = rs.getString(INVOICE_DATE);
            int id = rs.getInt(ID);
            double discount = rs.getDouble(DISCOUNT);
            double total = rs.getDouble(TOTAL);
            int sup_id = rs.getInt(SUP_ID);
            int stock_id = rs.getInt(STOCK_ID);
            int treasury_id = rs.getInt(TREASURY_ID);
//            int total_buy_id = rs.getInt(TOTAL_BUY_ID);
            int type_id = rs.getInt(INVOICE_TYPE);
            String sup_name = rs.getString(SuppliersDao.NAME);
            String stock_name = rs.getString(StockDao.STOCK_NAME);
            String treasury_name = rs.getString(TreasuryDao.COLUMN_NAME);
            String notes = rs.getString(NOTES) != null ? rs.getString(NOTES) : "";
//            DiscountType discountType = DiscountType.getDiscountTypeById(rs.getInt(DISCOUNT_TYPE));
            Suppliers suppliers = new Suppliers(sup_id, sup_name);
            Stock stock = new Stock(stock_id, stock_name);
            Treasury treasury = new Treasury(treasury_id, treasury_name, BigDecimal.valueOf(0));
            double paidToTreasuryAmount = rs.getDouble(PAID_TO_TREASURY);
            var invoiceTypeById = InvoiceType.getInvoiceTypeById(type_id);
            totalBuyRe = new Total_Buy_Re(id, date, total, discount, paidToTreasuryAmount, notes, suppliers, stock, treasury, invoiceTypeById, null);
            totalBuyRe.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            totalBuyRe.setUsers(daoFactory.usersDao().getDataById(rs.getInt(USER_ID)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return totalBuyRe;
    }

    public int deleteInvoicesInRange(Integer... invoiceNumbers) throws DaoException {
        if (invoiceNumbers.length == 0) return 0;
        return executeUpdate(deleteInRangeSql(invoiceNumbers.length), (Object[]) invoiceNumbers);
    }

    public List<Total_Buy_Re> getTotalBuyBySupId(int customerId) throws DaoException {
        return queryForObjects(selectByPartySql(), this::map, customerId);
    }

    public List<Total_Buy_Re> getTotalBuyByYear(int year) throws DaoException {
        return queryForObjects(selectByYearSql(), this::map, year);
    }

    public int getMaxId() throws DaoException {
        return queryForInt(maxIdSql());
    }
}
