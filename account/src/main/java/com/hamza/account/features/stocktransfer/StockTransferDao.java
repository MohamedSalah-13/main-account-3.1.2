package com.hamza.account.features.stocktransfer;

import com.hamza.account.features.items.WarehouseStockDao;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class StockTransferDao extends AbstractDao<Void> {

    private final WarehouseStockDao warehouseStock = new WarehouseStockDao();


    /**
     * The source warehouse's rows, locked, and the names of the items that have one.
     * <p>
     * Delegated to {@link WarehouseStockDao} rather than written here: every writer that takes
     * stock out of a warehouse has to lock the same rows in the same order, and a rule kept in
     * two files is a rule that can be ordered two ways.
     */
    Map<Integer, String> lockSource(int stockId, List<Integer> itemIds) throws DaoException {
        return warehouseStock.lockItems(stockId, itemIds);
    }

    /** Current base-unit balance of every requested item in {@code stockId}. */
    Map<Integer, Double> balances(int stockId, List<Integer> itemIds) throws DaoException {
        return warehouseStock.balances(stockId, itemIds);
    }

    /**
     * A warehouse created before {@code StockService.save} started backfilling
     * {@code items_stock}, or seeded outside the application, may still have no row
     * for an item being transferred in - without one, the incoming quantity has
     * nothing to add itself onto in {@code quantity_items_table}.
     */
    void ensureDestination(int stockId, List<Integer> itemIds) throws DaoException {
        // The same statement as a count's, kept once: V78 dropped a column both copies wrote.
        warehouseStock.ensureRows(stockId, itemIds);
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
        String sql = "INSERT INTO stock_transfer(transfer_date, stock_from, stock_to, notes, user_id) "
                + "VALUES (?, ?, ?, ?, ?)";
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setObject(1, command.transferDate());
                statement.setInt(2, command.fromStockId());
                statement.setInt(3, command.toStockId());
                if (command.notes() == null) statement.setNull(4, java.sql.Types.VARCHAR);
                else statement.setString(4, command.notes());
                if (command.userId() == null) statement.setNull(5, java.sql.Types.INTEGER);
                else statement.setInt(5, command.userId());
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

    /** One page of the history and one extra row, newest first - see {@link StockTransferHistoryQuery}. */
    List<StockTransferSummary> page(StockTransferHistoryFilter filter) throws DaoException {
        Object[] where = StockTransferHistoryQuery.whereValues(filter);
        Object[] values = Arrays.copyOf(where, where.length + 2);
        values[where.length] = filter.queryLimit();
        values[where.length + 1] = filter.offset();
        return withConnection(connection -> {
            List<StockTransferSummary> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(StockTransferHistoryQuery.PAGE)) {
                setData(statement, values);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(summary(rows));
                    }
                }
            }
            return result;
        });
    }

    /** How many transfers and lines the whole filtered set holds: {@code [transfers, lines]}. */
    long[] totals(StockTransferHistoryFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(StockTransferHistoryQuery.TOTALS)) {
                setData(statement, StockTransferHistoryQuery.whereValues(filter));
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    return new long[]{rows.getLong("transfers"), rows.getLong("line_count")};
                }
            }
        });
    }

    /** Every line of the filtered transfers, oldest first, up to {@code limit} rows. */
    List<StockTransferReportRow> log(StockTransferHistoryFilter filter, int limit) throws DaoException {
        Object[] where = StockTransferHistoryQuery.whereValues(filter);
        Object[] values = Arrays.copyOf(where, where.length + 1);
        values[where.length] = limit;
        return withConnection(connection -> {
            List<StockTransferReportRow> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(StockTransferHistoryQuery.LOG)) {
                setData(statement, values);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new StockTransferReportRow(
                                rows.getInt("id"),
                                rows.getDate("transfer_date").toLocalDate(),
                                rows.getString("name_from"),
                                rows.getString("name_to"),
                                rows.getString("barcode"),
                                rows.getString("nameItem"),
                                rows.getString("unit_name"),
                                rows.getDouble("quantity"),
                                rows.getString("notes")));
                    }
                }
            }
            return result;
        });
    }

    /** One transfer's header and who entered it, or {@code null} when it is gone. */
    StockTransferSummary header(int transferId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(StockTransferHistoryQuery.HEADER)) {
                statement.setInt(1, transferId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? summary(rows) : null;
                }
            }
        });
    }

    /** One transfer's lines in the order they were entered. */
    List<StockTransferLineRow> lines(int transferId) throws DaoException {
        return withConnection(connection -> {
            List<StockTransferLineRow> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(StockTransferHistoryQuery.LINES)) {
                statement.setInt(1, transferId);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new StockTransferLineRow(rows.getInt("item_id"), rows.getString("barcode"),
                                rows.getString("nameItem"), rows.getString("unit_name"), rows.getDouble("quantity")));
                    }
                }
            }
            return result;
        });
    }

    private static StockTransferSummary summary(java.sql.ResultSet rows) throws java.sql.SQLException {
        return new StockTransferSummary(
                rows.getInt("id"),
                rows.getDate("transfer_date").toLocalDate(),
                rows.getInt("stock_from"),
                rows.getString("name_from"),
                rows.getInt("stock_to"),
                rows.getString("name_to"),
                rows.getInt("line_count"),
                rows.getString("notes"),
                rows.getString("user_name"));
    }
}
