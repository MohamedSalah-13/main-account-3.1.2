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
    public int insert(Sales sales) throws DaoException {
        String query = SqlStatements.insertStatement(TABLE_NAME, INVOICE_NUMBER
                , NUM, TYPE, QUANTITY, PRICE, buyPrice, "total_sel_price", "total_buy_price", "total_profit"
                , discount, TYPE_VALUE, expirationDate, itemHasPackage);
        return executeUpdate(query, getData(sales));
    }

    @Override
    public int update(Sales sales) throws DaoException {
        String query = SqlStatements.updateStatement(TABLE_NAME, NUM
                , TYPE, QUANTITY, PRICE, buyPrice, "total_sel_price", "total_buy_price", "total_profit"
                , discount, TYPE_VALUE, expirationDate, itemHasPackage);
        Object[] params = new Object[]{sales.getUnitsType().getUnit_id()
                , sales.getQuantity(), sales.getPrice(), sales.getBuy_price(), sales.getTotalSelPrice()
                , sales.getTotal_buy_price(), sales.getTotal_profit(), sales.getDiscount()
                , sales.getUnitsType().getValue(), sales.getExpiration_date(), sales.isItem_has_package()
                , sales.getItems().getId()};
        return executeUpdate(query, params);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Object[] getData(Sales sales) throws DaoException {
        return new Object[]{sales.getInvoiceNumber(), sales.getItems().getId(), sales.getUnitsType().getUnit_id()
                , sales.getQuantity(), sales.getPrice(), sales.getBuy_price(), sales.getTotalSelPrice()
                , sales.getTotal_buy_price(), sales.getTotal_profit(), sales.getDiscount()
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
        // Validate stock availability before attempting insert
        validateStockAvailability(list);

        try {
            String query = SqlStatements.insertStatement(TABLE_NAME, INVOICE_NUMBER
                    , NUM, TYPE, QUANTITY, PRICE, buyPrice, "total_sel_price", "total_buy_price", "total_profit"
                    , discount, TYPE_VALUE, expirationDate, itemHasPackage);
            return executeUpdateListWithException(list, query, (statement, sales) -> setData(statement, getData(sales)));
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("items_stock_current_quantity_chk")) {
                throw new DaoException("Not enough stock quantity for one or more sold items.", e);
            }
            throw new DaoException(e);
        }
    }

    /**
     * Validates that there is sufficient stock for all items in the sales list.
     * Groups quantities by item ID and stock to check total required quantity.
     *
     * @param salesList the list of sales to validate
     * @throws DaoException if any item has insufficient stock
     */
    private void validateStockAvailability(List<Sales> salesList) throws DaoException {
        // Group sales by item ID and sum quantities (converted to base unit)
        java.util.Map<Integer, Double> requiredQuantities = new java.util.HashMap<>();

        for (Sales sale : salesList) {
            int itemId = sale.getItems().getId();
            double quantityInBaseUnit = sale.getQuantity() * sale.getUnitsType().getValue();
            requiredQuantities.merge(itemId, quantityInBaseUnit, Double::sum);
        }

        // Check each item's stock availability
        StringBuilder insufficientItems = new StringBuilder();
        for (java.util.Map.Entry<Integer, Double> entry : requiredQuantities.entrySet()) {
            int itemId = entry.getKey();
            double requiredQty = entry.getValue();

            try {
                // Query current stock quantity (assuming stock_id = 1 or get from sale)
                String stockQuery = "SELECT current_quantity FROM items_stock WHERE item_id = ? AND stock_id = ?";
                double currentQty = queryForDouble(stockQuery, itemId, 1);

                if (currentQty < requiredQty) {
                    if (insufficientItems.length() > 0) {
                        insufficientItems.append(", ");
                    }
                    insufficientItems.append(String.format("Item ID %d (available: %.2f, required: %.2f)",
                            itemId, currentQty, requiredQty));
                }
            } catch (Exception e) {
                log.warn("Could not verify stock for item {}: {}", itemId, e.getMessage());
            }
        }

        if (insufficientItems.length() > 0) {
            throw new DaoException("Insufficient stock for: " + insufficientItems);
        }
    }

    /**
     * Executes a query and returns a double value from the first column.
     */
    private double queryForDouble(String query, Object... parameters) {
        try {
            java.sql.PreparedStatement statement = connection.prepareStatement(query);
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            java.sql.ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
            return 0.0;
        } catch (java.sql.SQLException e) {
            log.error("Error executing query: {}", e.getMessage());
            return 0.0;
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