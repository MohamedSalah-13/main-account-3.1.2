package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.database.AbstractDao;
import com.hamza.account.database.DaoException;
import com.hamza.account.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            sales.setCustomers(new Customers(0,rs.getString(name)));

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
        if (list == null || list.isEmpty()) {
            return 0;
        }

        // Validate stock availability before attempting insert
        // This now checks cumulative quantities per item
        validateStockAvailability(list);

        try {
            String query = SqlStatements.insertStatement(TABLE_NAME, INVOICE_NUMBER
                    , NUM, TYPE, QUANTITY, PRICE, buyPrice, "total_sel_price", "total_buy_price", "total_profit"
                    , discount, TYPE_VALUE, expirationDate, itemHasPackage);
            return executeUpdateListWithException(list, query, (statement, sales) -> setData(statement, getData(sales)));
        } catch (DaoException e) {
            // Re-throw DaoException as-is (including our validation exceptions)
            throw e;
        } catch (SQLException e) {
            // Check if this is a stock constraint violation
            if (isStockConstraintViolation(e)) {
                throw new DaoException("الكمية المطلوبة غير متوفرة في المخزون. يرجى التحقق من الكميات المتاحة.");
            }
            throw mapSqlExceptionForSales(e);
        }
    }

    private void validateStockAvailability(List<Sales> list) throws DaoException {
        // Group quantities by item number to check cumulative demand
        // Also keep track of the stock id per item (invoice stock)
        Map<Integer, Double> totalQuantityPerItem = new HashMap<>();
        Map<Integer, Integer> stockIdPerItem = new HashMap<>();

        for (Sales sale : list) {
            int itemNum = sale.getNumItem();
            // use quantity expressed in the base unit (quantity * unit value)
            double quantity = sale.getQuantity() * sale.getUnitsType().getValue();
            totalQuantityPerItem.merge(itemNum, quantity, Double::sum);

            int stockId = sale.getStock_id();
            if (stockId <= 0) {
                stockId = getStockIdBySalesInvoiceNumber(sale.getInvoiceNumber());
            }

            stockIdPerItem.putIfAbsent(itemNum, stockId);
        }

        // Validate each item's total requested quantity against available stock
        for (Map.Entry<Integer, Double> entry : totalQuantityPerItem.entrySet()) {
            int itemNum = entry.getKey();
            double requestedQuantity = entry.getValue();
            int stockId = stockIdPerItem.getOrDefault(itemNum, 1);

            double availableStock = getAvailableStockForItem(itemNum, stockId);

            if (requestedQuantity > availableStock) {
                String itemName = getItemName(itemNum);
                throw new DaoException(String.format(
                        "الكمية المطلوبة (%.2f) للصنف %s رقم %d أكبر من المتاح في المخزن رقم %d (%.2f)",
                        requestedQuantity,
                        itemName == null ? "" : itemName,
                        itemNum,
                        stockId,
                        availableStock));
            }
        }
    }

    private int getStockIdBySalesInvoiceNumber(long invoiceNumber) throws DaoException {
        String query = "SELECT stock_id FROM total_sales WHERE invoice_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, invoiceNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock_id");
                }
            }
            return 1;
        } catch (SQLException e) {
            throw new DaoException("خطأ في تحديد مخزن فاتورة البيع", e);
        }
    }

    /**
     * Gets the current available stock for an item in a specific stock.
     * This should query the database for the latest stock value.
     */
    private double getAvailableStockForItem(int itemNum, int stockId) throws DaoException {
        String query = "SELECT current_quantity FROM items_stock WHERE item_id = ? AND stock_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, itemNum);
            stmt.setInt(2, stockId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("current_quantity");
                }
                return 0.0;
            }
        } catch (SQLException e) {
            throw new DaoException("خطأ في التحقق من كمية المخزون", e);
        }
    }

    /**
     * Checks if the SQLException is related to stock constraint violation.
     */
    private boolean isStockConstraintViolation(SQLException e) {
        String message = e.getMessage();
        return message != null && message.contains("items_stock_current_quantity_chk");
    }

    /**
     * Maps SQLException to a user-friendly DaoException for sales operations.
     */
    private DaoException mapSqlExceptionForSales(SQLException e) {
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("items_stock_current_quantity_chk")) {
                return new DaoException("Insufficient stock: The requested quantity exceeds available stock for one or more items. Please verify stock levels and try again.", e);
            }
            if (message.contains("Duplicate entry")) {
                return new DaoException("Duplicate invoice entry detected.", e);
            }
        }
        log.error("Sales insert failed: {}", message, e);
        return new DaoException("Failed to save sales: " + message, e);
    }

    /**
     * Gets the item name by ID for better error messages.
     */
    private String getItemName(int itemId) {
        try {
            String query = "SELECT nameItem FROM items WHERE id = ?";
            java.sql.PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, itemId);
            java.sql.ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (java.sql.SQLException e) {
            log.debug("Could not get item name for id {}: {}", itemId, e.getMessage());
        }
        return null;
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