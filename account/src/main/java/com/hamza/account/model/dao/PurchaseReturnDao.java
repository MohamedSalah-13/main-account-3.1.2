package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase_Return;
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

import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class PurchaseReturnDao extends AbstractDao<Purchase_Return> {

    public static final String TABLE_NAME = "purchase_re";
    public static final String INVOICE_NUMBER = "invoice_number";
    private final String TABLE_VIEW = "purchase_return_names_table";
    private final String ITEM_ID = "item_id";
    private final String QUANTITY = "quantity";
    private final String TYPE = "type";
    private final String TYPE_VALUE = "type_value";
    private final String PRICE = "price";
    private final String ID = "id";
    private final String DISCOUNT = "discount";
    private final String EXPIRATION_DATE = "expiration_date";
    private final DaoFactory daofactory;

    public PurchaseReturnDao(Connection connection, DaoFactory daofactory) {
        super(connection);
        this.daofactory = daofactory;
    }

    @Override
    public List<Purchase_Return> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Purchase_Return> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, INVOICE_NUMBER), this::map, id);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Purchase_Return map(ResultSet resultSet) throws DaoException {
        Purchase_Return purchaseReturn;
        try {
            int numItem = resultSet.getInt(ITEM_ID);
            int id = resultSet.getInt(ID);
            int invoiceNumber = resultSet.getInt(INVOICE_NUMBER);
            double price = resultSet.getDouble(PRICE);
            double quantity = resultSet.getDouble(QUANTITY);
            double discount = resultSet.getDouble(DISCOUNT);
            double total = roundToTwoDecimalPlaces(quantity * price);
            UnitsModel unitsType = daofactory.unitsDao().getDataById(resultSet.getInt(TYPE));
            ItemsModel items = new ItemsModel(numItem, resultSet.getString(ItemsDao.BARCODE), resultSet.getString(ItemsDao.NAME_ITEM));
//            Purchase purchaseObject = new Purchase(resultSet.getInt(PURCHASE_ID));
            purchaseReturn = new Purchase_Return();
            purchaseReturn.setId(id);
            purchaseReturn.setInvoiceNumber(invoiceNumber);
            purchaseReturn.setQuantity(quantity);
            purchaseReturn.setPrice(price);
            purchaseReturn.setTotal(total);
            purchaseReturn.setDiscount(discount);
            purchaseReturn.setTotal_after_discount(total - discount);
            purchaseReturn.setUnitsType(unitsType);
            purchaseReturn.setItems(items);
            purchaseReturn.setNumItem(numItem);

            var date = resultSet.getDate(EXPIRATION_DATE);
            if (date != null) {
                purchaseReturn.setExpiration_date(date.toLocalDate());
            }

        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return purchaseReturn;
    }

    @Override
    public int insertList(List<Purchase_Return> list) throws DaoException {
        try {
            return executeUpdateListWithException(list, SqlStatements.insertStatement(TABLE_NAME
                    , INVOICE_NUMBER, ITEM_ID, TYPE
                    , QUANTITY, PRICE, DISCOUNT
                    , TYPE_VALUE, EXPIRATION_DATE), this::setData);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private void setData(PreparedStatement statement, Purchase_Return purchaseReturn) throws SQLException {
        try {
            Object[] objects = new Object[]{purchaseReturn.getInvoiceNumber(), purchaseReturn.getItems().getId()
                    , purchaseReturn.getUnitsType().getUnit_id(), purchaseReturn.getQuantity(), purchaseReturn.getPrice(), purchaseReturn.getDiscount()
                    , purchaseReturn.getUnitsType().getValue(), purchaseReturn.getExpiration_date()};
            for (int i = 0; i < objects.length; i++) {
                statement.setObject(i + 1, objects[i]);
            }
        } catch (SQLException e) {
            log.error(e.getClass().getName(), e.getCause());
        }
    }

    public List<Purchase_Return> loadBetweenTwoInvoiceNumber(int first, int last) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_NUMBER + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, first, last);
    }

    public List<Purchase_Return> findByNumItem(int numItem) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(ITEM_ID).concat(" = ?");
        return queryForObjects(query, this::map, numItem);
    }
}