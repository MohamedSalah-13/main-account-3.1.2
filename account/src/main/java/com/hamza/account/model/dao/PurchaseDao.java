package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class PurchaseDao extends AbstractDao<Purchase> {

    public static final String TABLE_NAME = "purchase";
    public static final String INVOICE_NUMBER = "invoice_number";
    private final DaoFactory daoFactory;
    private final String TABLE_VIEW = "purchase_names_table";
    private final String ID = "id";
    private final String NUM = "num";
    private final String TYPE = "type";
    private final String TYPE_VALUE = "type_value";
    private final String QUANTITY = "quantity";
    private final String PRICE = "price";
    private final String DISCOUNT = "discount";
    private final String NAME = "name";
    private final String EXPIRATION_DATE = "expiration_date";

    public PurchaseDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Purchase> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public List<Purchase> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, INVOICE_NUMBER), this::map, id);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Object[] getData(Purchase purchase) throws DaoException {
        return new Object[]{purchase.getInvoiceNumber(), purchase.getItems().getId(), purchase.getUnitsType().getUnit_id()
                , purchase.getQuantity(), purchase.getPrice(), purchase.getDiscount(), purchase.getUnitsType().getValue()
                , purchase.getExpiration_date()};
    }

    @Override
    public Purchase map(ResultSet rs) throws DaoException {

        Purchase purchase;
        try {
            int id = rs.getInt(ID);
            int inv_num = rs.getInt(INVOICE_NUMBER);
            int numItem = rs.getInt(NUM);
            double quantity = rs.getDouble(QUANTITY);
            double price = rs.getDouble(PRICE);
            double discount = rs.getDouble(DISCOUNT);
            double total = roundToTwoDecimalPlaces((quantity * price));
            double totalAfterDiscount = roundToTwoDecimalPlaces(total - discount);

            UnitsModel unitsType = daoFactory.unitsDao().getDataById(rs.getInt(TYPE));
            ItemsModel items = new ItemsModel(numItem, rs.getString(ItemsDao.BARCODE), rs.getString(ItemsDao.NAME_ITEM));
            Suppliers suppliers = new Suppliers(0, rs.getString(NAME));
            purchase = new Purchase();
            purchase.setUnitsType(unitsType);
            purchase.setId(id);
            purchase.setInvoiceNumber(inv_num);
            purchase.setNumItem(numItem);
            purchase.setQuantity(quantity);
            purchase.setPrice(price);
            purchase.setDiscount(discount);
            purchase.setTotal(total);
            purchase.setTotal_after_discount(totalAfterDiscount);
            purchase.setItems(items);
            purchase.setSuppliers(suppliers);

            var date = rs.getDate(EXPIRATION_DATE);
            if (date != null) {
                purchase.setExpiration_date(date.toLocalDate());
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return purchase;
    }

    @Override
    public int insertList(List<Purchase> list) throws DaoException {
        try {
            String query = SqlStatements.insertStatement(TABLE_NAME, INVOICE_NUMBER, NUM
                    , TYPE, QUANTITY, PRICE, DISCOUNT, TYPE_VALUE, EXPIRATION_DATE);
            return executeUpdateListWithException(list, query, (statement, purchase) -> {
                try {
                    setData(statement, getData(purchase));
                } catch (DaoException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public List<Purchase> loadBetweenTwoInvoiceNumber(int first, int last) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW) + " WHERE " + INVOICE_NUMBER + " BETWEEN ? AND ?";
        return queryForObjects(query, this::map, first, last);
    }

    public List<Purchase> findByNumItem(int numItem) throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_VIEW).concat(" WHERE ").concat(NUM).concat(" = ?");
        return queryForObjects(query, this::map, numItem);
    }

}
