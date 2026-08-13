package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class PurchaseReturnDao extends DocumentLineDao<Purchase_Return> {

    /** Which document these lines belong to, and every statement over them. */
    static final DocumentTableSpec SPEC = DocumentTableSpec.PURCHASE_RETURN;

    public static final String TABLE_NAME = SPEC.lineTable();
    public static final String INVOICE_NUMBER = DocumentTableSpec.LINE_DOCUMENT;
    private final String TABLE_VIEW = SPEC.lineView();
    private final String ITEM_ID = SPEC.lineItem();
    private final String QUANTITY = "quantity";
    private final String TYPE = "type";
    private final String TYPE_VALUE = "type_value";
    private final String PRICE = "price";
    private final String ID = DocumentTableSpec.LINE_KEY;
    private final String DISCOUNT = "discount";
    private final String EXPIRATION_DATE = "expiration_date";
    private final DaoFactory daofactory;

    public PurchaseReturnDao(DaoFactory daofactory) {
        super(SPEC);
        this.daofactory = daofactory;
    }

    @Override
    public List<Purchase_Return> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<Purchase_Return> loadAllById(int id) throws DaoException {
        return queryForObjects(selectByDocumentSql(), this::map, id);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }

    // ---- the statements ---------------------------------------------------------
    // Named so DocumentDaoStatementsTest can read them without a database.

    String selectAllSql() {
        return SPEC.lineSelectAllSql();
    }

    String selectByDocumentSql() {
        return SPEC.lineSelectByDocumentSql();
    }

    String selectBetweenDocumentsSql() {
        return SPEC.lineSelectBetweenDocumentsSql();
    }

    String selectByItemSql() {
        return SPEC.lineSelectByItemSql();
    }

    String deleteSql() {
        return SPEC.lineDeleteSql();
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
            unitsType.setValue(resultSet.getDouble(TYPE_VALUE));
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

    /**
     * The line insert, named so {@code DocumentDaoStatementsTest} can read it without a
     * database. Same columns as the purchase line, under a different name for the item.
     */
    String insertListSql() {
        return SPEC.lineInsertSql();
    }

    @Override
    public int insertList(List<Purchase_Return> list) throws DaoException {
        try {
            return executeUpdateListWithException(list, insertListSql(), this::setData);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    /** The parameters of {@link #insertListSql()}, in its column order. */
    @Override
    protected Object[] lineData(Purchase_Return purchaseReturn) {
        return new Object[]{purchaseReturn.getInvoiceNumber(), purchaseReturn.getItems().getId()
                , purchaseReturn.getUnitsType().getUnit_id(), purchaseReturn.getQuantity(), purchaseReturn.getPrice(), purchaseReturn.getDiscount()
                , purchaseReturn.getUnitsType().getValue(), purchaseReturn.getExpiration_date()};
    }

    private void setData(PreparedStatement statement, Purchase_Return purchaseReturn) throws SQLException {
        try {
            Object[] objects = lineData(purchaseReturn);
            for (int i = 0; i < objects.length; i++) {
                statement.setObject(i + 1, objects[i]);
            }
        } catch (SQLException e) {
            log.error(e.getClass().getName(), e);
        }
    }

    public List<Purchase_Return> loadBetweenTwoInvoiceNumber(int first, int last) throws DaoException {
        return queryForObjects(selectBetweenDocumentsSql(), this::map, first, last);
    }

    public List<Purchase_Return> findByNumItem(int numItem) throws DaoException {
        return queryForObjects(selectByItemSql(), this::map, numItem);
    }
}
