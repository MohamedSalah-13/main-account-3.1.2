package com.hamza.account.model.dao;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.document.DocumentWriteGuard;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.model.domain.*;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class TotalsSalesReturnDao extends AbstractDao<Total_Sales_Re> {

    /** Which document this DAO writes. The period lock it must respect follows from it. */
    static final DocumentType DOCUMENT_TYPE = DocumentType.SALES_RETURN;

    /** Where it lives, and every statement that reads or writes it. */
    static final DocumentTableSpec SPEC = DocumentTableSpec.of(DOCUMENT_TYPE);

    // The names map() reads the result set by - see TotalsSalesDao.
    public static final String TABLE_VIEW = SPEC.view();
    public static final String TABLE_NAME = SPEC.table();
    public static final String ID = SPEC.key();
    public static final String SUP_ID = SPEC.party();
    public static final String INVOICE_DATE = SPEC.dateColumn();
    public static final String PAID_FROM_TREASURY = SPEC.paid();
    public static final String STOCK_ID = "stock_id";
    public static final String TOTAL = "total";
    public static final String INVOICE_TYPE = "invoice_type";
    public static final String DELEGATE_ID = "delegate_id";
    public static final String TREASURY_ID = "treasury_id";
        /** Added by V16__return_source.sql; written by ReturnSourceWriter, not by this DAO. */
    static final String SOURCE_INVOICE_NUMBER = "source_invoice_number";
    static final String RETURN_REASON = "return_reason";
    public static final String NOTES = "notes";
    public static final String DISCOUNT = "discount";
    //    public static final String DISCOUNT_TYPE = "discount_type";
    public static final String USER_ID = "user_id";
    public static final String DATE_INSERT = "date_insert";
    private final String TOTAL_PROFIT = "total_profit";
    private final String PROFIT_PERCENT = "profit_percent";
    private final DaoFactory daoFactory;

    TotalsSalesReturnDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Total_Sales_Re> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<Total_Sales_Re> loadAllById(int id) throws DaoException {
        return queryForObjects(selectByIdSql(), this::map, id);
    }

    @Override
    public List<Total_Sales_Re> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        return queryForObjects(selectBetweenDatesSql(), this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_Sales_Re totalSalesRe) throws DaoException {
        // See TotalsSalesDao.insert: enforced here so it holds for every caller.
        PeriodLock.require(totalSalesRe.getDate(), DOCUMENT_TYPE.periodLock().label());
        String query = insertSql();
        return insertMultiData(() -> {
            try {
                // insert into total return
                DocumentWriteGuard.requireSingleHeaderRow(
                        executeUpdateWithException(query, getData(totalSalesRe)), DOCUMENT_TYPE);
                // insert to sales return
                daoFactory.salesReturnsDao().insertList(totalSalesRe.getSalesReturnList());

            } catch (SQLIntegrityConstraintViolationException e) {
                throw new UserValidationException("يجب إدخال جميع البيانات", e);
            } catch (DaoException e) {
                throw e;
            }
        });
    }

    @Override
    public int update(Total_Sales_Re totalSalesRe) throws DaoException {
        PeriodLock.requireMove(DOCUMENT_TYPE.periodLock(), totalSalesRe.getId(), totalSalesRe.getDate());
        String query = updateSql();
        return insertMultiData(() -> {
            Object[] objects = getUpdateData(totalSalesRe);
            DocumentWriteGuard.requireSingleHeaderRow(
                    executeUpdateWithException(query, objects), DOCUMENT_TYPE);
            daoFactory.salesReturnsDao().synchronizeLines(
                    totalSalesRe.getId(), totalSalesRe.getSalesReturnList());
        });

    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }

    @Override
    public Total_Sales_Re getDataById(int id) throws DaoException {
        return queryForObject(selectByIdSql(), this::map, id);
    }

    // ---- the statements, and the order their parameters go in -------------------
    // See TotalsSalesDao. The return family keys on id rather than invoice_number and
    // spells the party column sup_id, so nothing here can be shared with the sales
    // family by name alone - which is the whole reason these four DAOs exist.

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
    Object[] getUpdateData(Total_Sales_Re totalSalesRe) {
        return new Object[]{totalSalesRe.getCustomer().getId()
                , totalSalesRe.getDate()
                , totalSalesRe.getInvoiceType().getId()
                , totalSalesRe.getTotal()
                , totalSalesRe.getDiscount()
                , totalSalesRe.getPaid()
                , totalSalesRe.getStockData().getId()
                , totalSalesRe.getEmployeeObject().getId()
                , totalSalesRe.getTreasuryModel().getId()
                , totalSalesRe.getNotes()
//                    , totalSalesRe.getTotalSalesId()
                , totalSalesRe.getId()};
    }

    @Override
    public Object[] getData(Total_Sales_Re totalSalesRe) throws DaoException {
        return new Object[]{totalSalesRe.getCustomer().getId()
                , totalSalesRe.getDate()
                , totalSalesRe.getInvoiceType().getId()
                , totalSalesRe.getTotal()
                , totalSalesRe.getDiscount()
                , totalSalesRe.getPaid()
                , totalSalesRe.getStockData().getId()
                , totalSalesRe.getEmployeeObject().getId()
                , totalSalesRe.getTreasuryModel().getId()
                , totalSalesRe.getId(), totalSalesRe.getNotes()
//                , totalSalesRe.getTotalSalesId()
                , totalSalesRe.getUsers().getId()};
    }

    @Override
    public Total_Sales_Re map(ResultSet rs) throws DaoException {
        Total_Sales_Re totalSalesRe;
        try {
            String date = rs.getString(INVOICE_DATE);
            int id = rs.getInt(ID);
            double total = rs.getDouble(TOTAL);
            double discount = rs.getDouble(DISCOUNT);
            int sup_id = rs.getInt(SUP_ID);
            int stock_id = rs.getInt(STOCK_ID);
            int treasury_id = rs.getInt(TREASURY_ID);
//            int total_sales_id = rs.getInt(TOTAL_SALES_ID);
            String delegate_name = rs.getString(EmployeesDao.COLUMN_NAME);
            String sup_name = rs.getString(SuppliersDao.NAME);
            String stock_name = rs.getString(StockDao.STOCK_NAME);
            String treasury_name = rs.getString(TreasuryDao.COLUMN_NAME);
            String notes = rs.getString(NOTES) != null ? rs.getString(NOTES) : "";
            int type_id = rs.getInt(INVOICE_TYPE);
            Customers customer = new Customers(sup_id, sup_name);
            Stock stock = new Stock(stock_id, stock_name);
            Treasury treasury = new Treasury(treasury_id, treasury_name, BigDecimal.valueOf(0));
            double paidFromTreasury = rs.getDouble(PAID_FROM_TREASURY);
            totalSalesRe = new Total_Sales_Re();
            totalSalesRe.setId(id);
            totalSalesRe.setDate(date);
            totalSalesRe.setTotal(total);
            totalSalesRe.setDiscount(discount);
            totalSalesRe.setPaid(paidFromTreasury);
            totalSalesRe.setNotes(notes);
            totalSalesRe.setCustomer(customer);
            totalSalesRe.setStockData(stock);
            totalSalesRe.setEmployeeObject(new Employees(rs.getInt(DELEGATE_ID), delegate_name));
            totalSalesRe.setTreasuryModel(treasury);
            totalSalesRe.setInvoiceType(InvoiceType.getInvoiceTypeById(type_id));
            totalSalesRe.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            totalSalesRe.setUsers(daoFactory.usersDao().getDataById(rs.getInt(USER_ID)));
            totalSalesRe.setSourceInvoiceNumber(rs.getInt(SOURCE_INVOICE_NUMBER));
            totalSalesRe.setReturnReason(rs.getString(RETURN_REASON));
            totalSalesRe.setTotal_profit(rs.getDouble(TOTAL_PROFIT));
            totalSalesRe.setProfit_percent(rs.getDouble(PROFIT_PERCENT));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return totalSalesRe;
    }

    public int deleteInvoicesInRange(Integer... invoiceNumbers) throws DaoException {
        if (invoiceNumbers.length == 0) return 0;
        return executeUpdate(deleteInRangeSql(invoiceNumbers.length), (Object[]) invoiceNumbers);
    }

    public List<Total_Sales_Re> getTotalSalesByCustomerId(int customerId) throws DaoException {
        return queryForObjects(selectByPartySql(), this::map, customerId);
    }

    public List<Total_Sales_Re> getTotalSalesByYear(int year) throws DaoException {
        return queryForObjects(selectByYearSql(), this::map, year);
    }

    public int getMaxId() throws DaoException {
        return queryForInt(maxIdSql());
    }

    public List<Total_Sales_Re> searchTotals(TotalsSearchCriteria criteria) throws DaoException {
        List<Object> params = new ArrayList<>();
        String sql = SPEC.searchSql(criteria, params);
        return queryForObjects(sql, this::map, params.toArray());
    }
}
