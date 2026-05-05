package com.hamza.account.model.dao;

import com.hamza.account.model.domain.*;
import com.hamza.account.trial.TrialManager;
import com.hamza.account.type.InvoiceStatus;
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

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

public class TotalsBuyDao extends AbstractDao<Total_buy> {

    private final String DATE_INSERT = "date_insert";
    private final String TABLE_VIEW = "total_purchase_names_table";
    private final String TABLE_NAME = "total_buy";
    private final String INVOICE_NUMBER = "invoice_number";
    private final String SUP_CODE = "sup_code";
    private final String INVOICE_TYPE = "invoice_type";
    private final String INVOICE_DATE = "invoice_date";
    private final String TOTAL = "total";
    private final String DISCOUNT = "discount";
    private final String DISCOUNT_TYPE = "discount_type";
    private final String PAID_UP = "paid_up";
    private final String STOCK_ID = "stock_id";
    private final String TREASURY_ID = "treasury_id";
    private final String NOTES = "notes";
    private final String OTHER_PAID = "OtherPaid";
    private final String USER_ID = "user_id";
    private final PurchaseDao purchaseDao;
    private final DaoFactory daoFactory;

    TotalsBuyDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
        this.purchaseDao = daoFactory.purchaseDao();
    }

    @Override
    public List<Total_buy> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Total_buy> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_DATE + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_buy total_buy) throws DaoException {
        if (!new TrialManager(connection).canAddPurchase()) return 0;
        String query = SqlStatements.insertStatement(TABLE_NAME, SUP_CODE, INVOICE_TYPE, INVOICE_DATE, TOTAL, DISCOUNT, PAID_UP, STOCK_ID, TREASURY_ID, NOTES, USER_ID, INVOICE_NUMBER);
        return insertMultiData(() -> {
            Object[] data = getData(total_buy);
            // first insert data in total
            executeUpdateWithException(query, data);
            // Secondly, enter the purchase data.
            purchaseDao.insertList(total_buy.getPurchaseList());
        });
    }

    @Override
    public int update(Total_buy total_buy) throws DaoException {
        String query = SqlStatements.updateStatement(TABLE_NAME, INVOICE_NUMBER, SUP_CODE, INVOICE_TYPE, INVOICE_DATE, TOTAL, DISCOUNT, PAID_UP, STOCK_ID, TREASURY_ID, NOTES);
        return insertMultiData(() -> {
            Object[] data = new Object[]{total_buy.getSupplierData().getId()
                    , total_buy.getInvoiceType().getId()
                    , total_buy.getDate()
                    , total_buy.getTotal()
                    , total_buy.getDiscount()
                    , total_buy.getPaid()
                    , total_buy.getStockData().getId()
                    , total_buy.getTreasuryModel().getId()
                    , total_buy.getNotes()
                    , total_buy.getId()
            };

            executeUpdateWithException(query, data);
            // first, delete data from purchase
            executeUpdateWithException(SqlStatements.deleteStatement(PurchaseDao.TABLE_NAME, PurchaseDao.INVOICE_NUMBER), total_buy.getId());
            // Secondly, enter the purchase data.
            // insert if not existing
            List<Purchase> purchaseList = total_buy.getPurchaseList();
            purchaseDao.insertList(purchaseList);
            // update list if existing
//            purchaseDao.updateList(list1);
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, INVOICE_NUMBER), id);
    }

    @Override
    public Total_buy getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, INVOICE_NUMBER), this::map, id);
    }

    @Override
    public Object[] getData(Total_buy total_buy) throws DaoException {
        return new Object[]{total_buy.getSupplierData().getId()
                , total_buy.getInvoiceType().getId()
                , total_buy.getDate()
                , total_buy.getTotal()
                , total_buy.getDiscount()
                , total_buy.getPaid()
                , total_buy.getStockData().getId()
                , total_buy.getTreasuryModel().getId()
                , total_buy.getNotes()
                , total_buy.getUsers().getId()
                , total_buy.getId()
        };
    }

    @Override
    public Total_buy map(ResultSet rs) throws DaoException {
        Total_buy total_buy;
        try {
            int num = rs.getInt(INVOICE_NUMBER);
            int sup_id = rs.getInt(SUP_CODE);
            String sup_name = rs.getString(SuppliersDao.NAME);
            int type_id = rs.getInt(INVOICE_TYPE);
            String date = rs.getString(INVOICE_DATE);
            double total = rs.getDouble(TOTAL);
            double dis = rs.getDouble(DISCOUNT);
            double paid = rs.getDouble(PAID_UP);
            int stock_id = rs.getInt(STOCK_ID);
            double total_amount = total - dis;
            String stock_name = rs.getString(StockDao.STOCK_NAME);
            int treasury_id = rs.getInt(TREASURY_ID);
            String treasury_name = rs.getString(TreasuryDao.COLUMN_NAME);

            total_buy = new Total_buy();
            total_buy.setId(num);
            total_buy.setInvoiceType(type_id == 1 ? InvoiceType.CASH : InvoiceType.DEFER);
            total_buy.setDate(date);
            total_buy.setTotal(total);
            total_buy.setDiscount(dis);
            total_buy.setTotal_after_discount(total_amount);
            total_buy.setPaid(paid);
            total_buy.setRest(roundToTwoDecimalPlaces(total_amount - paid));
            total_buy.setSupplierData(new Suppliers(sup_id, sup_name));
            total_buy.setStockData(new Stock(stock_id, stock_name));
            total_buy.setTreasuryModel(new TreasuryModel(treasury_id, treasury_name, 0));
            total_buy.setNotes(rs.getString(NOTES) != null ? rs.getString(NOTES) : "");
            total_buy.setOtherPaid(rs.getDouble(OTHER_PAID));
            total_buy.setAmountAfterOtherPaid(roundToTwoDecimalPlaces(total_amount - total_buy.getOtherPaid() - total_buy.getPaid()));
            total_buy.setInvoice_status(total_buy.getAmountAfterOtherPaid() == 0 ? InvoiceStatus.CLOSE : InvoiceStatus.OPEN);
            total_buy.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            total_buy.setUsers(daoFactory.usersDao().getDataById(rs.getInt(USER_ID)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return total_buy;
    }

    public int deleteInvoicesInRange(Integer... invoiceNumbers) throws DaoException {
        String query = SqlStatements.deleteInRangeId(TABLE_NAME, INVOICE_NUMBER, invoiceNumbers);
        return executeUpdate(query);
    }

    public List<Total_buy> getTotalBuyBySupId(int customerId) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(SUP_CODE).concat(" = ?");
        return queryForObjects(query, this::map, customerId);
    }

    public List<Total_buy> getTotalBuyByYear(int year) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE YEAR(invoice_date)").concat(" = ?");
        return queryForObjects(query, this::map, year);
    }

    public List<Integer> getListYear() throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" GROUP BY YEAR(invoice_date) ORDER BY YEAR(invoice_date) DESC");
        //TODO 11/16/2025 9:47 AM Mohamed: get all years
        return List.of();
    }

    public int getMaxId() {
        return queryForInt("SELECT COALESCE(MAX(invoice_number), 0) + 1 FROM " + TABLE_NAME);
    }
}
