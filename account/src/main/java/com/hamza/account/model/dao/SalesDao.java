package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class SalesDao extends AbstractDao<Sales> {

    public static final String TABLE_NAME = "sales";
    public static final String INVOICE_NUMBER = "invoice_number";
    // for returned
//    public static final String RETURNED_QUANTITY = "amount";
    private final String TABLE_VIEW = "sales_names_table";
    // for sales
    private final String ID = "id";
    private final String NUM = "num";
    private final String TYPE = "type";
    private final String TYPE_VALUE = "type_value";
    private final String QUANTITY = "quantity";
    private final String PRICE = "price";
    private final String totalSales = "total_sales";
    private final String discount = "discount";
    // for buy price
    private final String buyPrice = "buy_price";
    private final String totalBuy = "total_buy";
    // for name items
    private final String name = "name";
    // for totals
    private final String invoiceDate = "invoice_date";
    private final String stockId = "stock_id";
    private final String expirationDate = "expiration_date";
    private final String itemHasPackage = "item_has_package";
    private final String nameId = "name_id";
    private final DaoFactory daoFactory;

    public SalesDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Sales> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Sales> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, INVOICE_NUMBER), this::map, id);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Object[] getData(Sales sales) throws DaoException {
        return new Object[]{sales.getInvoiceNumber(), sales.getItems().getId(), sales.getUnitsType().getUnit_id()
                , sales.getQuantity(), sales.getPrice(), sales.getBuy_price(), sales.getDiscount()
                , sales.getUnitsType().getValue(), sales.getExpiration_date(), sales.isItem_has_package()};
    }

    @Override
    public Sales map(ResultSet rs) throws DaoException {
        Sales sales = new Sales();

        try {
            int inv_num = rs.getInt(INVOICE_NUMBER);
            int numItem = rs.getInt(NUM);
            double saleQuantity = rs.getDouble(QUANTITY);
            double aDoublePrice = rs.getDouble(PRICE);
            double aDoubleDiscount = rs.getDouble(discount);
            double round = rs.getDouble(totalSales);

            sales.setId(rs.getInt(ID));
            sales.setInvoiceNumber(inv_num);
            sales.setNumItem(numItem);
            sales.setQuantity(saleQuantity);
            sales.setPrice(aDoublePrice);
            sales.setDiscount(aDoubleDiscount);
            sales.setTotal(round);
            sales.setTotal_after_discount(roundToTwoDecimalPlaces(round - aDoubleDiscount));
            var unitsModel = daoFactory.unitsDao().getDataById(rs.getInt(TYPE));
            sales.setUnitsType(unitsModel);
            sales.setCustomers(new Customers(rs.getString(name), 0, 0));

            sales.setQuantityByUnit(rs.getDouble(TYPE_VALUE) * saleQuantity);

            var buyPrice = rs.getDouble(this.buyPrice);
            var itemByIdAndStockId = daoFactory.getItemsDao().findItemByIdAndStockId(numItem, 1);
            itemByIdAndStockId.setBuyPrice(buyPrice);
            sales.setItems(itemByIdAndStockId);
            // for buy
            sales.setBuy_price(buyPrice);
            sales.setTotal_buy_price(rs.getDouble(totalBuy));
            // for totals
            sales.setInvoiceDate(LocalDate.parse(rs.getString(invoiceDate)));
            sales.setStock_id(rs.getInt(stockId));
            sales.setItem_has_package(rs.getBoolean(itemHasPackage));

            var date = rs.getDate(expirationDate);
            if (date != null) {
                sales.setExpiration_date(date.toLocalDate());
            }


        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return sales;
    }

    @Override
    public int insertList(List<Sales> list) throws DaoException {
        try {
            String query = SqlStatements.insertStatement(TABLE_NAME, INVOICE_NUMBER
                    , NUM, TYPE, QUANTITY, PRICE, buyPrice
                    , discount, TYPE_VALUE, expirationDate, itemHasPackage);
            return executeUpdateListWithException(list, query, (statement, sales) -> setData(statement, getData(sales)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public List<Sales> loadBetweenTwoInvoiceNumber(int first, int last) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_NUMBER + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, first, last);
    }

    public List<Sales> findByNumItem(int numItem) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(NUM).concat(" = ?");
        return queryForObjects(query, this::map, numItem);
    }

}