package com.hamza.account.model.dao;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.document.DocumentWriteGuard;
import com.hamza.account.model.domain.*;
import com.hamza.account.trial.TrialManager;
import com.hamza.account.type.InvoiceStatus;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Log4j2
public class TotalsSalesDao extends AbstractDao<Total_Sales> {

    /** Which document this DAO writes. The period lock it must respect follows from it. */
    static final DocumentType DOCUMENT_TYPE = DocumentType.SALES;

    /** Where it lives, and every statement that reads or writes it. */
    static final DocumentTableSpec SPEC = DocumentTableSpec.of(DOCUMENT_TYPE);

    // The names map() reads the result set by. Taken from the spec rather than repeated,
    // so the column a row is read from cannot drift from the column it was written to.
    private final String TABLE_VIEW = SPEC.view();
    private final String SUP_CODE = SPEC.party();
    private final String INVOICE_DATE = SPEC.dateColumn();
    private final String PAID_UP = SPEC.paid();
    private final String INVOICE_NUMBER = SPEC.key();
    private final String INVOICE_TYPE = "invoice_type";
    private final String TOTAL = "total";
    private final String DISCOUNT = "discount";
    //    private final String DISCOUNT_TYPE = "discount_type";
    private final String STOCK_ID = "stock_id";
    private final String DELEGATE_ID = "delegate_id";
    private final String TREASURY_ID = "treasury_id";
    private final String NOTES = "notes";
    private final String OTHER_PAID = "OtherPaid";
    private final String USER_ID = "user_id";
    private final String DATE_INSERT = "date_insert";
    private final String TOTAL_PROFIT = "total_profit";
    private final String PROFIT_PERCENT = "profit_percent";
    private final DaoFactory daoFactory;
    private final SalesDao salesDao;

    TotalsSalesDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
        this.salesDao = daoFactory.salesDao();
    }

    @Override
    public List<Total_Sales> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<Total_Sales> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        return queryForObjects(selectBetweenDatesSql(), this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_Sales totalSales) throws DaoException {
        // Here rather than in the service, so it holds for every caller - the invoice
        // invoice screen and the return that writes one on its way through. A closed
        // period has been reported, and an invoice dated into it changes a figure that
        // has already been signed.
        PeriodLock.require(totalSales.getDate(), DOCUMENT_TYPE.periodLock().label());
        if (!withConnection(c -> new TrialManager(c).canAddSale())) return 0;
        String query = insertSql();
        return insertMultiData(() -> {
            Object[] data = getData(totalSales);
            // first insert data in total
            DocumentWriteGuard.requireSingleHeaderRow(
                    executeUpdateWithException(query, data), DOCUMENT_TYPE);
            // Secondly, enter the sales data.
            salesDao.insertList(totalSales.getSalesList());
        });

    }

    @Override
    public int update(Total_Sales totalSales) throws DaoException {
        // Both ends: where the invoice is now, and where it is being moved to.
        PeriodLock.requireMove(DOCUMENT_TYPE.periodLock(), totalSales.getId(), totalSales.getDate());
        String query = updateSql();
        return insertMultiData(() -> {
            Object[] data = getUpdateData(totalSales);
            DocumentWriteGuard.requireSingleHeaderRow(
                    executeUpdateWithException(query, data), DOCUMENT_TYPE);
            salesDao.synchronizeLines(totalSales.getId(), totalSales.getSalesList());
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }

    @Override
    public Total_Sales getDataById(int id) throws DaoException {
        return queryForObject(selectByIdSql(), this::map, id);
    }

    // ---- the statements, and the order their parameters go in -------------------
    //
    // The statements come from the spec, which is the only place the column names are
    // written. What stays here is the array bound to each - the part that has to know
    // this family's model. DocumentDaoStatementsTest pins both, and that the two agree.

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
    Object[] getUpdateData(Total_Sales totalSales) {
        return new Object[]{totalSales.getCustomers().getId()
                , totalSales.getInvoiceType().getId()
                , totalSales.getDate()
                , totalSales.getTotal()
                , totalSales.getDiscount()
//                    , totalSales.getDiscountType().getId()
                , totalSales.getPaid()
                , totalSales.getStockData().getId()
                , totalSales.getEmployeeObject().getId()
                , totalSales.getTreasuryModel().getId()
                , totalSales.getNotes()
                , totalSales.getId()};
    }

    @Override
    public Object[] getData(Total_Sales totalSales) throws DaoException {
        return new Object[]{totalSales.getCustomers().getId()
                , totalSales.getInvoiceType().getId()
                , totalSales.getDate()
                , totalSales.getTotal()
                , totalSales.getDiscount()
//                , totalSales.getDiscountType().getId()
                , totalSales.getPaid()
                , totalSales.getStockData().getId()
                , totalSales.getEmployeeObject().getId()
                , totalSales.getTreasuryModel().getId()
                , totalSales.getNotes()
                , totalSales.getId()
                , totalSales.getUsers().getId()};
    }

    @Override
    public Total_Sales map(ResultSet rs) throws DaoException {
        Total_Sales totalSales;
        try {
            int num = rs.getInt(INVOICE_NUMBER);
            int custom_id = rs.getInt(SUP_CODE);
            String custom_name = rs.getString(CustomerDao.NAME);
            int type_id = rs.getInt(INVOICE_TYPE);
            String date = rs.getString(INVOICE_DATE);
            double total = rs.getDouble(TOTAL);
            double dis = rs.getDouble(DISCOUNT);
            double paid = rs.getDouble(PAID_UP);
            int stock_id = rs.getInt(STOCK_ID);
            String stock_name = rs.getString(StockDao.STOCK_NAME);
            int delegate_id = rs.getInt(DELEGATE_ID);
            String delegate_name = rs.getString(EmployeesDao.COLUMN_NAME);
            int treasury_id = rs.getInt(TREASURY_ID);
            String treasury_name = rs.getString(TreasuryDao.COLUMN_NAME);
            var netAmount = MoneyMath.subtract(
                    MoneyMath.decimal(total), MoneyMath.decimal(dis));
            double total_amount = MoneyMath.asDouble(netAmount);

            totalSales = new Total_Sales();
            totalSales.setId(num);
            totalSales.setInvoiceType(InvoiceType.getInvoiceTypeById(type_id));
            totalSales.setDate(date);
            totalSales.setTotal(total);
            totalSales.setDiscount(dis);
//            totalSales.setDiscountType(DiscountType.getDiscountTypeById(rs.getInt(DISCOUNT_TYPE)));
            totalSales.setTotal_after_discount(total_amount);
            totalSales.setPaid(paid);
            totalSales.setRest(MoneyMath.asDouble(MoneyMath.subtract(
                    netAmount, MoneyMath.decimal(paid))));
            totalSales.setCustomers(new Customers(custom_id, custom_name));
            totalSales.setStockData(new Stock(stock_id, stock_name));
            totalSales.setEmployeeObject(new Employees(delegate_id, delegate_name));
            totalSales.setTreasuryModel(new Treasury(treasury_id, treasury_name, BigDecimal.valueOf(0)));
            totalSales.setNotes(rs.getString(NOTES) != null ? rs.getString(NOTES) : " ");
            totalSales.setOtherPaid(rs.getDouble(OTHER_PAID));
            totalSales.setAmountAfterOtherPaid(MoneyMath.asDouble(MoneyMath.subtract(
                    MoneyMath.subtract(netAmount, MoneyMath.decimal(totalSales.getOtherPaid())),
                    MoneyMath.decimal(totalSales.getPaid()))));
            totalSales.setInvoice_status(totalSales.getAmountAfterOtherPaid() == 0 ? InvoiceStatus.CLOSE : InvoiceStatus.OPEN);
            totalSales.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            totalSales.setUsers(daoFactory.usersDao().getDataById(rs.getInt(USER_ID)));
            totalSales.setTotal_profit(rs.getDouble(TOTAL_PROFIT));
            totalSales.setProfit_percent(rs.getDouble(PROFIT_PERCENT));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return totalSales;
    }

    public int deleteInvoicesInRange(Integer... invoiceNumbers) throws DaoException {
        if (invoiceNumbers.length == 0) return 0;
        return executeUpdate(deleteInRangeSql(invoiceNumbers.length), (Object[]) invoiceNumbers);
    }

    public List<Total_Sales> getTotalSalesByCustomerId(int customerId) throws DaoException {
        return queryForObjects(selectByPartySql(), this::map, customerId);
    }

    public List<Total_Sales> getTotalSalesByYear(int year) throws DaoException {
        return queryForObjects(selectByYearSql(), this::map, year);
    }

    public int getMaxId() throws DaoException {
        return queryForInt(maxIdSql());
    }
}
