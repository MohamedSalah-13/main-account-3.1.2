package com.hamza.account.features.invoice;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** JDBC implementation that joins the transaction already opened by InvoiceSaveService. */
public final class JdbcInvoiceStockRepository implements InvoiceStockRepository {

    @Override
    public List<StoredLine> originalLinesForUpdate(DocumentType type, int documentId)
            throws DaoException {
        String sql = originalLinesForUpdateSql(type);
        return withConnection(connection -> {
            List<StoredLine> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, documentId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        Date expiry = rows.getDate("expiration_date");
                        lines.add(new StoredLine(rows.getInt("item_id"),
                                rows.getDouble("base_quantity"),
                                expiry == null ? null : expiry.toLocalDate()));
                    }
                }
            }
            return lines;
        });
    }

    @Override
    public Map<Integer, String> lockItems(List<Integer> itemIds) throws DaoException {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String sql = lockItemsSql(itemIds.size());
        Map<Integer, String> items = withConnection(connection -> {
            Map<Integer, String> result = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindIds(statement, 1, itemIds);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.put(rows.getInt("id"), rows.getString("nameItem"));
                    }
                }
            }
            return result;
        });
        if (items.size() != itemIds.size()) {
            throw new DaoException("One or more invoice items no longer exist");
        }
        return items;
    }

    @Override
    public Map<Integer, Double> currentBaseBalances(List<Integer> itemIds)
            throws DaoException {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String sql = baseBalancesSql(itemIds.size());
        return withConnection(connection -> {
            Map<Integer, Double> balances = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, DefaultStock.ID);
                bindIds(statement, 2, itemIds);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        balances.put(rows.getInt("id"), rows.getDouble("current_balance"));
                    }
                }
            }
            return balances;
        });
    }

    @Override
    public Map<BatchKey, Double> currentExpiryBalances(List<Integer> itemIds)
            throws DaoException {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String sql = expiryBalancesSql(itemIds.size());
        return withConnection(connection -> {
            Map<BatchKey, Double> balances = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int parameter = 1;
                for (int branch = 0; branch < 4; branch++) {
                    statement.setInt(parameter++, DefaultStock.ID);
                    parameter = bindIds(statement, parameter, itemIds);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        balances.put(new BatchKey(rows.getInt("item_id"),
                                        rows.getDate("expiration_date").toLocalDate()),
                                rows.getDouble("current_balance"));
                    }
                }
            }
            return balances;
        });
    }

    static String originalLinesForUpdateSql(DocumentType type) {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        return "SELECT " + spec.lineItem() + " AS item_id,"
                + "quantity * type_value AS base_quantity,expiration_date FROM "
                + spec.lineTable() + " WHERE " + DocumentTableSpec.LINE_DOCUMENT
                + "=? ORDER BY " + DocumentTableSpec.LINE_KEY + " FOR UPDATE";
    }

    static String lockItemsSql(int itemCount) {
        return "SELECT id,nameItem FROM items WHERE id IN ("
                + placeholders(itemCount) + ") ORDER BY id FOR UPDATE";
    }

    static String baseBalancesSql(int itemCount) {
        return """
                SELECT i.id,
                       i.first_balance
                       + COALESCE(q.quantityPurchase, 0)
                       + COALESCE(q.quantitySalesRe, 0)
                       + COALESCE(q.toStock, 0)
                       + COALESCE(q.adjustment, 0)
                       - COALESCE(q.quantitySales, 0)
                       - COALESCE(q.quantityPurchaseRe, 0)
                       - COALESCE(q.fromStock, 0) AS current_balance
                FROM items i
                LEFT JOIN quantity_items_table q
                  ON q.item_id = i.id AND q.stock_id = ?
                WHERE i.id IN (%s)
                ORDER BY i.id
                """.formatted(placeholders(itemCount));
    }

    static String expiryBalancesSql(int itemCount) {
        String ids = placeholders(itemCount);
        return """
                SELECT item_id, expiration_date, SUM(base_quantity) AS current_balance
                FROM (
                    SELECT p.num AS item_id, p.expiration_date,
                           p.quantity * p.type_value AS base_quantity
                    FROM purchase p
                    JOIN total_buy h ON h.invoice_number = p.invoice_number
                    WHERE h.stock_id = ? AND p.num IN (%s) AND p.expiration_date IS NOT NULL
                    UNION ALL
                    SELECT r.item_id, r.expiration_date,
                           r.quantity * r.type_value AS base_quantity
                    FROM sales_re r
                    JOIN total_sales_re h ON h.id = r.invoice_number
                    WHERE h.stock_id = ? AND r.item_id IN (%s) AND r.expiration_date IS NOT NULL
                    UNION ALL
                    SELECT s.num AS item_id, s.expiration_date,
                           -(s.quantity * s.type_value) AS base_quantity
                    FROM sales s
                    JOIN total_sales h ON h.invoice_number = s.invoice_number
                    WHERE h.stock_id = ? AND s.num IN (%s) AND s.expiration_date IS NOT NULL
                    UNION ALL
                    SELECT r.item_id, r.expiration_date,
                           -(r.quantity * r.type_value) AS base_quantity
                    FROM purchase_re r
                    JOIN total_buy_re h ON h.id = r.invoice_number
                    WHERE h.stock_id = ? AND r.item_id IN (%s) AND r.expiration_date IS NOT NULL
                ) movements
                GROUP BY item_id, expiration_date
                ORDER BY item_id, expiration_date
                """.formatted(ids, ids, ids, ids);
    }

    static String placeholders(int count) {
        StringJoiner result = new StringJoiner(",");
        for (int index = 0; index < count; index++) {
            result.add("?");
        }
        return result.toString();
    }

    private static int bindIds(PreparedStatement statement, int start,
                               List<Integer> itemIds) throws SQLException {
        int parameter = start;
        for (Integer itemId : itemIds) {
            statement.setInt(parameter++, itemId);
        }
        return parameter;
    }

    private static <T> T withConnection(SqlWork<T> work) throws DaoException {
        if (!ConnectionManager.inTransaction()) {
            throw new DaoException("Invoice stock validation requires an active transaction");
        }
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not validate invoice stock", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
