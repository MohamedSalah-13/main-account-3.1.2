package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TotalsPurchaseReturnDao extends AbstractDao<Total_Buy_Re> {

    private final String DATE_INSERT = "date_insert";
    private final String TABLE_VIEW = "total_purchase_return_names_table";
    private final String TABLE_NAME = "total_buy_re";
    private final String ID = "id";
    private final String SUP_ID = "sup_id";
    private final String INVOICE_DATE = "invoice_date";
    private final String TOTAL = "total";
    private final String DISCOUNT = "discount";
    private final String DISCOUNT_TYPE = "discount_type";
    private final String PAID_TO_TREASURY = "paid_to_treasury";
    private final String STOCK_ID = "stock_id";
    private final String TREASURY_ID = "treasury_id";
    private final String INVOICE_TYPE = "invoice_type";
    private final String NOTES = "notes";
    private final String USER_ID = "user_id";
    private final PurchaseReturnDao returnPurchaseDao;
    private final DaoFactory daoFactory;

    TotalsPurchaseReturnDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
        this.returnPurchaseDao = daoFactory.purchaseReturnsDao();
    }

    @Override
    public List<Total_Buy_Re> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Total_Buy_Re> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, ID), this::map, id);
    }

    @Override
    public List<Total_Buy_Re> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_DATE + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_Buy_Re totalBuyRe) throws DaoException {
        String query = SqlStatements.insertStatement(TABLE_NAME, SUP_ID, INVOICE_DATE, INVOICE_TYPE, TOTAL, DISCOUNT, PAID_TO_TREASURY, STOCK_ID, TREASURY_ID, ID, NOTES, USER_ID);
        return insertMultiData(() -> {
            // first, insert data to total
            executeUpdateWithException(query, getData(totalBuyRe));
            // Secondly, enter the purchase data.
            returnPurchaseDao.insertList(totalBuyRe.getPurchaseReturnList());
        });
    }

    @Override
    public int update(Total_Buy_Re totalBuyRe) throws DaoException {
        String query = SqlStatements.updateStatement(TABLE_NAME, ID, SUP_ID, INVOICE_DATE, INVOICE_TYPE, TOTAL, DISCOUNT, PAID_TO_TREASURY, STOCK_ID, TREASURY_ID, NOTES);
        return insertMultiData(() -> {
            Object[] objects = {totalBuyRe.getSuppliers().getId()
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
            // first, delete data from purchase
            executeUpdateWithException(SqlStatements.deleteStatement(PurchaseReturnDao.TABLE_NAME, PurchaseReturnDao.INVOICE_NUMBER), totalBuyRe.getId());
            // finally, insert data in total
            executeUpdateWithException(query, objects);
            // Secondly, enter the purchase data.
            returnPurchaseDao.insertList(totalBuyRe.getPurchaseReturnList());
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String deleteStatement = SqlStatements.deleteStatement(TABLE_NAME, ID);
        return executeUpdate(deleteStatement, id);
    }

    @Override
    public Total_Buy_Re getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, ID), this::map, id);
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
            TreasuryModel treasury = new TreasuryModel(treasury_id, treasury_name, 0);
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
        String query = SqlStatements.deleteInRangeId(TABLE_NAME, ID, invoiceNumbers);
        return executeUpdate(query);
    }

    public List<Total_Buy_Re> getTotalBuyBySupId(int customerId) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(SUP_ID).concat(" = ?");
        return queryForObjects(query, this::map, customerId);
    }

    public List<Total_Buy_Re> getTotalBuyByYear(int year) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE YEAR(invoice_date)").concat(" = ?");
        return queryForObjects(query, this::map, year);
    }
}
