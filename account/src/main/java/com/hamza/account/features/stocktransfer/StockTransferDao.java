package com.hamza.account.features.stocktransfer;

import com.hamza.account.features.items.ItemStockBalanceSql;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StockTransferDao extends AbstractDao<Void> {

    /** Locks every line's source row {@code FOR UPDATE}, and names the ones that exist. */
    Map<Integer, String> lockSource(int stockId, List<Integer> itemIds) throws DaoException {
        String marks = String.join(",", Collections.nCopies(itemIds.size(), "?"));
        String sql = "SELECT s.item_id, i.nameItem FROM items_stock s JOIN items i ON i.id = s.item_id "
                + "WHERE s.stock_id = ? AND s.item_id IN (" + marks + ") ORDER BY s.item_id FOR UPDATE";
        return withConnection(connection -> {
            Map<Integer, String> result = new LinkedHashMap<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, stockId);
                bindIds(statement, 2, itemIds);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) result.put(rows.getInt(1), rows.getString(2));
                }
            }
            return result;
        });
    }

    /**
     * Current base-unit balance of every requested item in {@code stockId}.
     * <p>
     * Read through {@link ItemStockBalanceSql}, not through {@code quantity_items_table}: each of
     * that view's seven CTEs is a {@code GROUP BY} over a whole line table, and MySQL builds all
     * of them before it can answer for one item. The helper computes the view's own columns for
     * named items with correlated subqueries that reach their lines through the item's index -
     * the same definition, pinned against the view by {@code ItemStockBalanceSqlTest}.
     */
    Map<Integer, Double> balances(int stockId, List<Integer> itemIds) throws DaoException {
        String sql = "SELECT item_id, first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment "
                + "- quantitySales - quantityPurchaseRe - fromStock AS balance FROM ("
                + ItemStockBalanceSql.forItems(itemIds.size()) + ") balances_for_items";
        return withConnection(connection -> {
            Map<Integer, Double> result = new HashMap<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, stockId);
                bindIds(statement, 2, itemIds);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) result.put(rows.getInt(1), rows.getDouble(2));
                }
            }
            return result;
        });
    }

    /**
     * A warehouse created before {@code StockService.save} started backfilling
     * {@code items_stock}, or seeded outside the application, may still have no row
     * for an item being transferred in - without one, the incoming quantity has
     * nothing to add itself onto in {@code quantity_items_table}.
     */
    void ensureDestination(int stockId, List<Integer> itemIds) throws DaoException {
        withConnection(connection -> {
            String sql = "INSERT IGNORE INTO items_stock(item_id, stock_id, first_balance, current_quantity) "
                    + "VALUES (?, ?, 0, 0)";
            try (var statement = connection.prepareStatement(sql)) {
                for (int itemId : itemIds) {
                    statement.setInt(1, itemId);
                    statement.setInt(2, stockId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /**
     * What a posted transfer put into its destination, summed per item - the figure reversing it
     * would take back out. One row per item however many lines of it the transfer carries.
     */
    List<IncomingLine> incoming(int transferId) throws DaoException {
        String sql = "SELECT l.item_id, i.nameItem, t.stock_to, s.stock_name, "
                + "SUM(l.quantity * l.type_value) AS moved "
                + "FROM stock_transfer t "
                + "JOIN stock_transfer_list l ON l.stock_transfer_id = t.id "
                + "JOIN items i ON i.id = l.item_id "
                + "JOIN stocks s ON s.stock_id = t.stock_to "
                + "WHERE t.id = ? GROUP BY l.item_id, i.nameItem, t.stock_to, s.stock_name";
        return withConnection(connection -> {
            List<IncomingLine> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, transferId);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new IncomingLine(rows.getInt(1), rows.getString(2),
                                rows.getInt(3), rows.getString(4), rows.getDouble(5)));
                    }
                }
            }
            return result;
        });
    }

    /** One item a transfer moved into {@code stockId}, in base units. */
    record IncomingLine(int itemId, String itemName, int stockId, String stockName, double movedBase) {
    }

    long insert(StockTransferCommand command) throws DaoException {
        String sql = "INSERT INTO stock_transfer(transfer_date, stock_from, stock_to, user_id) VALUES (?, ?, ?, ?)";
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setObject(1, command.transferDate());
                statement.setInt(2, command.fromStockId());
                statement.setInt(3, command.toStockId());
                if (command.userId() == null) statement.setNull(4, java.sql.Types.INTEGER);
                else statement.setInt(4, command.userId());
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
                throw new DaoException("Transfer id was not generated");
            }
        });
    }

    void insertLines(long transferId, List<StockTransferLine> lines) throws DaoException {
        String sql = "INSERT INTO stock_transfer_list(stock_transfer_id, item_id, type, quantity, type_value) "
                + "VALUES (?, ?, ?, ?, ?)";
        withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                for (StockTransferLine line : lines) {
                    statement.setLong(1, transferId);
                    statement.setInt(2, line.itemId());
                    statement.setInt(3, line.unitId());
                    statement.setDouble(4, line.quantity());
                    statement.setDouble(5, line.typeValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /** {@code stock_transfer_list} cascades with the header - see V1__baseline.sql. */
    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate("DELETE FROM stock_transfer WHERE id = ?", id);
    }

    /** Most recent transfers first, one row per header - for the reversal screen. */
    List<StockTransferSummary> recent(int limit) throws DaoException {
        String sql = """
                SELECT st.id, st.transfer_date, stf.stock_name AS name_from, stt.stock_name AS name_to,
                       COUNT(stl.id) AS line_count
                FROM stock_transfer st
                         JOIN stocks stf ON stf.stock_id = st.stock_from
                         JOIN stocks stt ON stt.stock_id = st.stock_to
                         JOIN stock_transfer_list stl ON stl.stock_transfer_id = st.id
                GROUP BY st.id, st.transfer_date, stf.stock_name, stt.stock_name
                ORDER BY st.id DESC
                LIMIT ?
                """;
        return withConnection(connection -> {
            List<StockTransferSummary> result = new java.util.ArrayList<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new StockTransferSummary(
                                rows.getInt("id"),
                                rows.getDate("transfer_date").toLocalDate(),
                                rows.getString("name_from"),
                                rows.getString("name_to"),
                                rows.getInt("line_count")));
                    }
                }
            }
            return result;
        });
    }

    /** One row per line, for the printed transfer log - see {@link StockTransferReportRow}. */
    List<StockTransferReportRow> reportRows(java.time.LocalDate from, java.time.LocalDate to) throws DaoException {
        String sql = """
                SELECT v.id, v.transfer_date, v.name_from, v.name_to, v.nameItem, v.quantity, u.unit_name
                FROM stock_transfer_view v
                         LEFT JOIN units u ON u.unit_id = v.type
                WHERE v.transfer_date BETWEEN ? AND ?
                ORDER BY v.transfer_date, v.id
                """;
        return withConnection(connection -> {
            List<StockTransferReportRow> result = new java.util.ArrayList<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, from);
                statement.setObject(2, to);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new StockTransferReportRow(
                                rows.getInt("id"),
                                rows.getDate("transfer_date").toLocalDate(),
                                rows.getString("name_from"),
                                rows.getString("name_to"),
                                rows.getString("nameItem"),
                                rows.getString("unit_name"),
                                rows.getDouble("quantity")));
                    }
                }
            }
            return result;
        });
    }

    private static void bindIds(java.sql.PreparedStatement statement, int startAt, List<Integer> itemIds)
            throws java.sql.SQLException {
        for (int i = 0; i < itemIds.size(); i++) {
            statement.setInt(startAt + i, itemIds.get(i));
        }
    }
}
