package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class SalesReturnDao extends AbstractDao<Sales_Return> {

    public static final String TABLE_NAME = "sales_re";
    public static final String INVOICE_NUMBER = "invoice_number";
    private final DaoFactory daofactory;
    private final String TABLE_VIEW = "sales_return_names_table";
    private final String ID = "id";
    private final String ITEM_ID = "item_id";
    private final String QUANTITY = "quantity";
    private final String TYPE = "type";
    private final String TYPE_VALUE = "type_value";
    private final String PRICE = "price";
    private final String DISCOUNT = "discount";
    private final String EXPIRATION_DATE = "expiration_date";

    public SalesReturnDao(Connection connection, DaoFactory daofactory) {
        super(connection);
        this.daofactory = daofactory;
    }

    @Override
    public List<Sales_Return> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Sales_Return> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, INVOICE_NUMBER), this::map, id);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Sales_Return map(ResultSet resultSet) throws DaoException {
        Sales_Return salesReturn;
        try {
            int numItem = resultSet.getInt(ITEM_ID);
            int id = resultSet.getInt(ID);
            int invoiceNumber = resultSet.getInt(INVOICE_NUMBER);
            double quantity = resultSet.getDouble(QUANTITY);
            double discount = resultSet.getDouble(DISCOUNT);
            double price = resultSet.getDouble(PRICE);
            double total = roundToTwoDecimalPlaces(quantity * price);
            UnitsModel unitsType = daofactory.unitsDao().getDataById(resultSet.getInt(TYPE));
//            Sales salesObject = new Sales(resultSet.getInt(SALES_ID));
            salesReturn = new Sales_Return();
            salesReturn.setNumItem(numItem);
            salesReturn.setUnitsType(unitsType);

            ItemsModel items = new ItemsModel(numItem, resultSet.getString(ItemsDao.BARCODE), resultSet.getString(ItemsDao.NAME_ITEM));
            salesReturn.setItems(items);
            salesReturn.setInvoiceNumber(invoiceNumber);
            salesReturn.setQuantity(quantity);
            salesReturn.setPrice(price);
            salesReturn.setDiscount(discount);
            salesReturn.setTotal(total);
            salesReturn.setTotal_after_discount(total - discount);
            salesReturn.setId(id);

            var date = resultSet.getDate(EXPIRATION_DATE);
            if (date != null) {
                salesReturn.setExpiration_date(date.toLocalDate());
            }


        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return salesReturn;
    }

    @Override
    public int insertList(List<Sales_Return> list) throws DaoException {
        try {
            return executeUpdateListWithException(list, SqlStatements.insertStatement(TABLE_NAME, INVOICE_NUMBER
                    , ITEM_ID, TYPE, QUANTITY, PRICE, "buy_price", "total_sel_price", "total_buy_price", "total_profit"
                    , DISCOUNT, TYPE_VALUE, EXPIRATION_DATE), this::setData);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private void setData(PreparedStatement statement, Sales_Return salesReturn) throws SQLException {
        try {
            Object[] objects = new Object[]{salesReturn.getInvoiceNumber(), salesReturn.getItems().getId()
                    , salesReturn.getUnitsType().getUnit_id(), salesReturn.getQuantity(), salesReturn.getPrice()
                    , salesReturn.getBuy_price(), salesReturn.getTotalSelPrice()
                    , salesReturn.getTotal_buy_price(), salesReturn.getTotal_profit()
                    , salesReturn.getDiscount()
                    , salesReturn.getUnitsType().getValue(), salesReturn.getExpiration_date()};
            for (int i = 0; i < objects.length; i++) {
                statement.setObject(i + 1, objects[i]);
            }
        } catch (SQLException e) {
            log.error(e.getClass().getName(), e.getCause());
        }
    }

    public List<Sales_Return> loadBetweenTwoInvoiceNumber(int first, int last) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_NUMBER + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, first, last);
    }

    public List<Sales_Return> findByNumItem(int numItem) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(ITEM_ID).concat(" = ?");
        return queryForObjects(query, this::map, numItem);
    }

}