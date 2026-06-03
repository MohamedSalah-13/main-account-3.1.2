package com.hamza.account.model.dao;

import com.hamza.account.model.domain.InventoryItemModel;
import com.hamza.account.database.AbstractDao;
import com.hamza.account.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InventoryDao extends AbstractDao<InventoryItemModel> {

    private static final String SEARCH_CONDITION = """
            AND (
                ? = ''
                OR barcode LIKE ?
                OR nameItem LIKE ?
                OR CAST(item_id AS CHAR) = ?
            )
            """;

    public InventoryDao(Connection connection) {
        super(connection);
    }

    public List<InventoryItemModel> getInventorySummary(String searchText, int limit, int offset) throws DaoException {
        String sql = """
                SELECT
                    item_id,
                    barcode,
                    nameItem,
                    0 AS stock_id,
                    'كل المخازن' AS stock_name,
                    unit_name,
                    first_balance_total AS first_balance,
                    total_purchase AS quantityPurchase,
                    total_sales AS quantitySales,
                    total_purchase_re AS quantityPurchaseRe,
                    total_sales_re AS quantitySalesRe,
                    0 AS transfer_in,
                    0 AS transfer_out,
                    total_balance AS current_balance,
                    buy_price,
                    sel_price1 AS sell_price,
                    total_value_cost AS stock_value_cost,
                    total_value_sell AS stock_value_sell,
                    stock_status
                FROM v_stock_inventory_summary
                WHERE 1 = 1
                """ + SEARCH_CONDITION + """
                ORDER BY nameItem
                LIMIT ? OFFSET ?
                """;

        String value = normalizeSearch(searchText);
        String contains = "%" + value + "%";

        return queryForObjects(sql, this::map, value, contains, contains, value, limit, offset);
    }

    public List<InventoryItemModel> getInventoryByStock(int stockId, String searchText, int limit, int offset) throws DaoException {
        String sql = """
                SELECT
                    item_id,
                    barcode,
                    nameItem,
                    stock_id,
                    stock_name,
                    unit_name,
                    first_balance,
                    quantityPurchase,
                    quantitySales,
                    quantityPurchaseRe,
                    quantitySalesRe,
                    toStock AS transfer_in,
                    fromStock AS transfer_out,
                    current_balance,
                    buy_price,
                    sel_price1 AS sell_price,
                    stock_value_cost,
                    stock_value_sell,
                    stock_status
                FROM v_stock_inventory
                WHERE stock_id = ?
                """ + SEARCH_CONDITION + """
                ORDER BY nameItem
                LIMIT ? OFFSET ?
                """;

        String value = normalizeSearch(searchText);
        String contains = "%" + value + "%";

        return queryForObjects(sql, this::map, stockId, value, contains, contains, value, limit, offset);
    }

    public int countInventorySummary(String searchText) {
        String sql = """
                SELECT COUNT(*)
                FROM v_stock_inventory_summary
                WHERE 1 = 1
                """ + SEARCH_CONDITION;

        String value = normalizeSearch(searchText);
        String contains = "%" + value + "%";

        return queryForInt(sql, value, contains, contains, value);
    }

    public int countInventoryByStock(int stockId, String searchText) {
        String sql = """
                SELECT COUNT(*)
                FROM v_stock_inventory
                WHERE stock_id = ?
                """ + SEARCH_CONDITION;

        String value = normalizeSearch(searchText);
        String contains = "%" + value + "%";

        return queryForInt(sql, stockId, value, contains, contains, value);
    }

    @Override
    public InventoryItemModel map(ResultSet rs) throws DaoException {
        try {
            InventoryItemModel model = new InventoryItemModel();

            model.setItemId(rs.getInt("item_id"));
            model.setBarcode(rs.getString("barcode"));
            model.setNameItem(rs.getString("nameItem"));

            model.setStockId(rs.getInt("stock_id"));
            model.setStockName(rs.getString("stock_name"));

            model.setUnitName(rs.getString("unit_name"));

            model.setFirstBalance(rs.getDouble("first_balance"));
            model.setQuantityPurchase(rs.getDouble("quantityPurchase"));
            model.setQuantitySales(rs.getDouble("quantitySales"));
            model.setQuantityPurchaseRe(rs.getDouble("quantityPurchaseRe"));
            model.setQuantitySalesRe(rs.getDouble("quantitySalesRe"));
            model.setTransferIn(rs.getDouble("transfer_in"));
            model.setTransferOut(rs.getDouble("transfer_out"));

            model.setCurrentBalance(rs.getDouble("current_balance"));

            model.setBuyPrice(rs.getDouble("buy_price"));
            model.setSellPrice(rs.getDouble("sell_price"));

            model.setStockValueCost(rs.getDouble("stock_value_cost"));
            model.setStockValueSell(rs.getDouble("stock_value_sell"));

            model.setStockStatus(rs.getString("stock_status"));

            return model;
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private String normalizeSearch(String searchText) {
        return searchText == null ? "" : searchText.trim();
    }
}
