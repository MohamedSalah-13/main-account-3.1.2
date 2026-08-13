package com.hamza.account.model.dao;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;


@Log4j2
public class SalesDao extends DocumentLineDao<Sales> {

    /** Which document these lines belong to, and every statement over them. */
    static final DocumentTableSpec SPEC = DocumentTableSpec.SALES;

    public static final String TABLE_NAME = SPEC.lineTable();
    public static final String INVOICE_NUMBER = DocumentTableSpec.LINE_DOCUMENT;
    // for returned
    private final String TABLE_VIEW = SPEC.lineView();
    // for sales
    private final String ID = DocumentTableSpec.LINE_KEY;
    private final String NUM = SPEC.lineItem();
    private final String TYPE = "type";
    private final String TYPE_VALUE = "type_value";
    private final String QUANTITY = "quantity";
    private final String PRICE = "price";
    private final String buyPrice = "buy_price";
    private final String discount = "discount";

    private final String totalSales = "total_sales";
    private final String totalBuy = "total_buy";
    // for name items
    private final String name = "name";
    // for totals
    private final String invoiceDate = "invoice_date";
    private final String stockId = "stock_id";
    private final String expirationDate = "expiration_date";
    private final DaoFactory daoFactory;

    public SalesDao(DaoFactory daoFactory) {
        super(SPEC);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Sales> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<Sales> loadAllById(int id) throws DaoException {
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
    public Object[] getData(Sales sales) throws DaoException {
        return new Object[]{sales.getInvoiceNumber(), sales.getItems().getId(), sales.getUnitsType().getUnit_id()
                , sales.getQuantity(), sales.getPrice(), sales.getBuy_price(), sales.getTotalSelPrice()
                , sales.getTotal_buy_price(), sales.getTotal_profit(), sales.getDiscount()
                , sales.getUnitsType().getValue(), sales.getExpiration_date()};
    }

    @Override
    protected Object[] lineData(Sales sales) throws DaoException {
        return getData(sales);
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
            sales.setTotal_after_discount(MoneyMath.asDouble(MoneyMath.subtract(
                    MoneyMath.decimal(round), MoneyMath.decimal(aDoubleDiscount))));
            var unitsModel = daoFactory.unitsDao().getDataById(rs.getInt(TYPE));
            unitsModel.setValue(rs.getDouble(TYPE_VALUE));
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

            var date = rs.getDate(expirationDate);
            if (date != null) {
                sales.setExpiration_date(date.toLocalDate());
            }


        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return sales;
    }

    /**
     * The line insert, named so {@code DocumentDaoStatementsTest} can read it without a
     * database. The item column is {@code num} here and {@code item_id} on the returns,
     * which is the difference that stops one statement serving all four line tables.
     */
    String insertListSql() {
        return SPEC.lineInsertSql();
    }

    @Override
    public int insertList(List<Sales> list) throws DaoException {
        try {
            String query = insertListSql();
            return executeUpdateListWithException(list, query, (statement, sales) -> setData(statement, getData(sales)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public List<Sales> loadBetweenTwoInvoiceNumber(int first, int last) throws DaoException {
        return queryForObjects(selectBetweenDocumentsSql(), this::map, first, last);
    }

    public List<Sales> findByNumItem(int numItem) throws DaoException {
        return queryForObjects(selectByItemSql(), this::map, numItem);
    }

}
