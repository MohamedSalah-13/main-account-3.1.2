package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class PurchaseDao extends DocumentLineDao<Purchase> {

    /** Which document these lines belong to, and every statement over them. */
    static final DocumentTableSpec SPEC = DocumentTableSpec.PURCHASE;

    public static final String TABLE_NAME = SPEC.lineTable();
    public static final String INVOICE_NUMBER = DocumentTableSpec.LINE_DOCUMENT;
    private final DaoFactory daoFactory;
    private final String TABLE_VIEW = SPEC.lineView();
    private final String ID = DocumentTableSpec.LINE_KEY;
    private final String NUM = SPEC.lineItem();
    private final String TYPE = "type";
    private final String TYPE_VALUE = "type_value";
    private final String QUANTITY = "quantity";
    private final String PRICE = "price";
    private final String DISCOUNT = "discount";
    private final String NAME = "name";
    private final String EXPIRATION_DATE = "expiration_date";

    public PurchaseDao(DaoFactory daoFactory) {
        super(SPEC);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Purchase> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<Purchase> loadAllById(int id) throws DaoException {
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
    public Object[] getData(Purchase purchase) throws DaoException {
        return new Object[]{purchase.getInvoiceNumber(), purchase.getItems().getId(), purchase.getUnitsType().getUnit_id()
                , purchase.getQuantity(), purchase.getPrice(), purchase.getDiscount(), purchase.getUnitsType().getValue()
                , purchase.getExpiration_date()};
    }

    @Override
    protected Object[] lineData(Purchase purchase) throws DaoException {
        return getData(purchase);
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
            unitsType.setValue(rs.getDouble(TYPE_VALUE));
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

    /**
     * The line insert, named so {@code DocumentDaoStatementsTest} can read it without a
     * database. A purchase line carries no profit columns - the four the sales line
     * writes have no meaning on the buying side.
     */
    String insertListSql() {
        return SPEC.lineInsertSql();
    }

    @Override
    public int insertList(List<Purchase> list) throws DaoException {
        try {
            String query = insertListSql();
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
        return queryForObjects(selectBetweenDocumentsSql(), this::map, first, last);
    }

    public List<Purchase> findByNumItem(int numItem) throws DaoException {
        return queryForObjects(selectByItemSql(), this::map, numItem);
    }

}
