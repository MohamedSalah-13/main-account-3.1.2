package com.hamza.account.model.dao;

import com.hamza.account.model.domain.*;
import com.hamza.account.trial.TrialManager;
import com.hamza.account.type.InvoiceStatus;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class TotalsSalesDao extends AbstractDao<Total_Sales> {

    private final String TABLE_VIEW = "total_sales_names_table";
    private final String TABLE_NAME = "total_sales";
    private final String SUP_CODE = "sup_code";
    private final String INVOICE_TYPE = "invoice_type";
    private final String INVOICE_DATE = "invoice_date";
    private final String TOTAL = "total";
    private final String DISCOUNT = "discount";
    //    private final String DISCOUNT_TYPE = "discount_type";
    private final String STOCK_ID = "stock_id";
    private final String PAID_UP = "paid_up";
    private final String DELEGATE_ID = "delegate_id";
    private final String INVOICE_NUMBER = "invoice_number";
    private final String TREASURY_ID = "treasury_id";
    private final String NOTES = "notes";
    private final String OTHER_PAID = "OtherPaid";
    private final String USER_ID = "user_id";
    private final String DATE_INSERT = "date_insert";
    private final String TOTAL_PROFIT = "total_profit";
    private final String PROFIT_PERCENT = "profit_percent";
    private final DaoFactory daoFactory;
    private final SalesDao salesDao;

    TotalsSalesDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
        this.salesDao = daoFactory.salesDao();
    }

    @Override
    public List<Total_Sales> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Total_Sales> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_DATE + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_Sales totalSales) throws DaoException {
        if (!new TrialManager(connection).canAddSale()) return 0;
        String query = SqlStatements.insertStatement(TABLE_NAME, SUP_CODE, INVOICE_TYPE, INVOICE_DATE, TOTAL, DISCOUNT
                , PAID_UP, STOCK_ID, DELEGATE_ID, TREASURY_ID, NOTES, INVOICE_NUMBER, USER_ID);
        return insertMultiData(() -> {
            Object[] data = getData(totalSales);
            // first insert data in total
            executeUpdateWithException(query, data);
            // Secondly, enter the sales data.
            salesDao.insertList(totalSales.getSalesList());
        });

    }

    @Override
    public int update(Total_Sales totalSales) throws DaoException {
        String query = SqlStatements.updateStatement(TABLE_NAME, INVOICE_NUMBER, SUP_CODE, INVOICE_TYPE, INVOICE_DATE
                , TOTAL, DISCOUNT, PAID_UP, STOCK_ID, DELEGATE_ID, TREASURY_ID, NOTES);
        return insertMultiData(() -> {
            Object[] data = new Object[]{totalSales.getCustomers().getId()
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

            //TODO 11/24/2025 6:14 AM Mohamed: update without delete
            // first, delete data from sales
            executeUpdateWithException(SqlStatements.deleteStatement(SalesDao.TABLE_NAME, SalesDao.INVOICE_NUMBER)
                    , totalSales.getId());
            // Secondly, enter the sales data.
            salesDao.insertList(totalSales.getSalesList());
            // insert item package
            totalSales.getSalesList().stream().filter(Sales::isItem_has_package)
                    .forEach(sales -> {
                        try {
                            sales.setItem_has_package(true);
                            insertPackage(sales);
                        } catch (DaoException e) {
                            log.error(e.getMessage(), e.getCause());
                        }
                    });

            // finally, insert data in total
            executeUpdateWithException(query, data);
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, INVOICE_NUMBER), id);
    }

    @Override
    public Total_Sales getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, INVOICE_NUMBER), this::map, id);
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
            double total_amount = total - dis;

            totalSales = new Total_Sales();
            totalSales.setId(num);
            totalSales.setInvoiceType(InvoiceType.getInvoiceTypeById(type_id));
            totalSales.setDate(date);
            totalSales.setTotal(total);
            totalSales.setDiscount(dis);
//            totalSales.setDiscountType(DiscountType.getDiscountTypeById(rs.getInt(DISCOUNT_TYPE)));
            totalSales.setTotal_after_discount(total_amount);
            totalSales.setPaid(paid);
            totalSales.setRest(roundToTwoDecimalPlaces(total_amount - paid));
            totalSales.setCustomers(new Customers(custom_id, custom_name));
            totalSales.setStockData(new Stock(stock_id, stock_name));
            totalSales.setEmployeeObject(new Employees(delegate_id, delegate_name));
            totalSales.setTreasuryModel(new TreasuryModel(treasury_id, treasury_name, 0));
            totalSales.setNotes(rs.getString(NOTES) != null ? rs.getString(NOTES) : " ");
            totalSales.setOtherPaid(rs.getDouble(OTHER_PAID));
            totalSales.setAmountAfterOtherPaid(roundToTwoDecimalPlaces(total_amount - totalSales.getOtherPaid() - totalSales.getPaid()));
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

    private void insertPackage(Sales sales) throws DaoException {
        List<Sales_Package> salesPackageList = new ArrayList<>();
        var itemsPackageByPackageId = daoFactory.getItemsPackageDao().getItemsPackageByPackageId(sales.getNumItem());
        if (!itemsPackageByPackageId.isEmpty()) {
            itemsPackageByPackageId.forEach(itemsPackage -> {
                try {
                    Sales_Package salesPackage = new Sales_Package();
                    var itemsModel = daoFactory.getItemsDao().getDataById(itemsPackage.getItems_id());

                    salesPackage.setSales_id(sales.getNumItem());
                    salesPackage.setItems_id(itemsModel.getId());
                    salesPackage.setUnit_id(itemsModel.getUnitsType().getUnit_id());
                    salesPackage.setQuantity(itemsPackage.getQuantity());
                    salesPackage.setSelling_price(itemsModel.getSelPrice1());
                    salesPackage.setBuying_price(itemsModel.getBuyPrice());
                    salesPackage.setTotal_sales(itemsPackage.getQuantity() * itemsModel.getSelPrice1());
                    salesPackage.setTotal_buying(itemsPackage.getQuantity() * itemsModel.getBuyPrice());
                    salesPackage.setTotal_profit(salesPackage.getTotal_sales() - salesPackage.getTotal_buying());
                    salesPackage.setUnit_value(itemsModel.getUnitsType().getValue());
                    salesPackageList.add(salesPackage);
                } catch (DaoException e) {
                    log.error(e.getMessage(), e.getCause());
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public Total_Sales getMaxId() throws DaoException {
        String query = "SELECT MAX(" + INVOICE_NUMBER + ") FROM " + TABLE_NAME;
        return queryForObject(query, resultSet -> {
//            if (!resultSet.next()) {
//                return new Total_Sales(0); // or appropriate default value
//            }
            int maxInvoiceNumber = resultSet.getInt(1);
            return new Total_Sales(maxInvoiceNumber);

        });
    }

    public int deleteInvoicesInRange(Integer... invoiceNumbers) throws DaoException {
        String query = SqlStatements.deleteInRangeId(TABLE_NAME, INVOICE_NUMBER, invoiceNumbers);
        return executeUpdate(query);
    }

    public List<Total_Sales> getTotalSalesByCustomerId(int customerId) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(SUP_CODE).concat(" = ?");
        return queryForObjects(query, this::map, customerId);
    }

    public List<Total_Sales> getTotalSalesByYear(int year) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE YEAR(invoice_date)").concat(" = ?");
        return queryForObjects(query, this::map, year);
    }
}
