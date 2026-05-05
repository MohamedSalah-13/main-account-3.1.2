package com.hamza.account.model.dao;

import com.hamza.account.model.domain.*;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Log4j2
public class TotalsSalesReturnDao extends AbstractDao<Total_Sales_Re> {

    public static final String TABLE_VIEW = "total_sales_return_names_table";
    public static final String TABLE_NAME = "total_sales_re";
    public static final String ID = "id";
    public static final String STOCK_ID = "stock_id";
    public static final String TOTAL = "total";
    public static final String INVOICE_DATE = "invoice_date";
    public static final String INVOICE_TYPE = "invoice_type";
    public static final String SUP_ID = "sup_id";
    public static final String DELEGATE_ID = "delegate_id";
    public static final String TREASURY_ID = "treasury_id";
    //    public static final String TOTAL_SALES_ID = "total_sales_id";
    public static final String NOTES = "notes";
    public static final String DISCOUNT = "discount";
    //    public static final String DISCOUNT_TYPE = "discount_type";
    public static final String PAID_FROM_TREASURY = "paid_from_treasury";
    public static final String USER_ID = "user_id";
    public static final String DATE_INSERT = "date_insert";
    private final String TOTAL_PROFIT = "total_profit";
    private final String PROFIT_PERCENT = "profit_percent";
    private final DaoFactory daoFactory;

    TotalsSalesReturnDao(Connection connection
            , DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Total_Sales_Re> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Total_Sales_Re> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, ID), this::map, id);
    }

    @Override
    public List<Total_Sales_Re> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_DATE + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_Sales_Re totalSalesRe) throws DaoException {
        String query = SqlStatements.insertStatement(TABLE_NAME, SUP_ID, INVOICE_DATE, INVOICE_TYPE, TOTAL
                , DISCOUNT, PAID_FROM_TREASURY, STOCK_ID, DELEGATE_ID, TREASURY_ID, ID, NOTES, USER_ID);
        return insertMultiData(() -> {
            try {
                // insert into total return
                executeUpdateWithException(query, getData(totalSalesRe));
                // insert to sales return
                daoFactory.salesReturnsDao().insertList(totalSalesRe.getSalesReturnList());

            } catch (SQLIntegrityConstraintViolationException e) {
                throw new DaoException("يجب إدخال جميع البيانات ... !", e);
            } catch (DaoException e) {
                throw new DaoException(e);
            }
        });
    }

    @Override
    public int update(Total_Sales_Re totalSalesRe) throws DaoException {
        String query = SqlStatements.updateStatement(TABLE_NAME, ID, SUP_ID, INVOICE_DATE, INVOICE_TYPE, TOTAL, DISCOUNT, PAID_FROM_TREASURY, STOCK_ID, DELEGATE_ID, TREASURY_ID, NOTES);
        return insertMultiData(() -> {
            Object[] objects = {totalSalesRe.getCustomer().getId()
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

            // delete invoice from total before update
            executeUpdateWithException(SqlStatements.deleteStatement(SalesReturnDao.TABLE_NAME, SalesReturnDao.INVOICE_NUMBER), totalSalesRe.getId());
            // insert new invoice to total
            daoFactory.salesReturnsDao().insertList(totalSalesRe.getSalesReturnList());
            //update total
            executeUpdateWithException(query, objects);
        });

    }

    @Override
    public int deleteById(int id) throws DaoException {
        String deleteStatement = SqlStatements.deleteStatement(TABLE_NAME, ID);
        return executeUpdate(deleteStatement, id);
    }

    @Override
    public Total_Sales_Re getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, ID), this::map, id);
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
            TreasuryModel treasury = new TreasuryModel(treasury_id, treasury_name, 0);
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
            totalSalesRe.setTotal_profit(rs.getDouble(TOTAL_PROFIT));
            totalSalesRe.setProfit_percent(rs.getDouble(PROFIT_PERCENT));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return totalSalesRe;
    }

    public int deleteInvoicesInRange(Integer... invoiceNumbers) throws DaoException {
        String query = SqlStatements.deleteInRangeId(TABLE_NAME, ID, invoiceNumbers);
        return executeUpdate(query);
    }

    public List<Total_Sales_Re> getTotalSalesByCustomerId(int customerId) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(SUP_ID).concat(" = ?");
        return queryForObjects(query, this::map, customerId);
    }

    public List<Total_Sales_Re> getTotalSalesByYear(int year) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE YEAR(invoice_date)").concat(" = ?");
        return queryForObjects(query, this::map, year);
    }

    public int getMaxId() {
        return queryForInt("SELECT COALESCE(MAX(id), 0) + 1 FROM " + TABLE_NAME);
    }
}
