package com.hamza.account.features.stockcount;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and writes count sheets.
 * <p>
 * The header and its lines are written together or not at all: a count whose lines
 * were saved without a header, or half its lines, is worse than no count. That is what
 * {@code insertMultiData} is for - it binds the statements to one connection and one
 * transaction, however many DAO calls they are spread over.
 */
public class StockCountDao extends AbstractDao<StockCount> {

    private static final String SELECT_HEADER = """
            SELECT id, stock_id, count_date, status, notes, posted_at, user_id
            FROM stock_count
            """;

    /**
     * A line joined to what it needs to be shown: the item's name and code, and the
     * name of the unit it was counted in. Read in one query rather than by looking each
     * item up per row.
     */
    private static final String SELECT_LINES = """
            SELECT scl.id,
                   scl.item_id,
                   scl.unit_id,
                   scl.type_value,
                   scl.system_qty,
                   scl.counted_qty,
                   i.nameItem,
                   i.barcode,
                   u.unit_name
            FROM stock_count_lines scl
                     JOIN items i ON i.id = scl.item_id
                     LEFT JOIN units u ON u.unit_id = scl.unit_id
            WHERE scl.count_id = ?
            ORDER BY scl.id
            """;

    public StockCountDao() {
        super();
    }

    /**
     * The draft the shop is in the middle of, if there is one.
     * <p>
     * Opening the screen continues an open draft rather than starting a second one:
     * two drafts counting the same shelves would both post, and the stock would move
     * twice.
     */
    public StockCount findOpenDraft(int stockId) throws DaoException {
        String query = SELECT_HEADER + " WHERE status = 'DRAFT' AND stock_id = ? ORDER BY id DESC LIMIT 1";
        StockCount count = queryForObject(query, this::map, stockId);
        if (count != null) {
            count.setLines(linesOf(count.getId()));
        }
        return count;
    }

    public StockCount findById(int id) throws DaoException {
        StockCount count = queryForObject(SELECT_HEADER + " WHERE id = ?", this::map, id);
        if (count != null) {
            count.setLines(linesOf(count.getId()));
        }
        return count;
    }

    /** One page of the history and one extra row, newest first - see {@link StockCountHistoryQuery}. */
    public List<StockCountSummary> page(StockCountHistoryFilter filter) throws DaoException {
        Object[] where = StockCountHistoryQuery.whereValues(filter);
        Object[] values = Arrays.copyOf(where, where.length + 2);
        values[where.length] = filter.queryLimit();
        values[where.length + 1] = filter.offset();
        return withConnection(connection -> {
            List<StockCountSummary> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(StockCountHistoryQuery.PAGE)) {
                setData(statement, values);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(summary(rs));
                    }
                }
            }
            return rows;
        });
    }

    /** Sheets, lines and lines with a difference in the whole filtered set: {@code [sheets, lines, differences]}. */
    public long[] totals(StockCountHistoryFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(StockCountHistoryQuery.TOTALS)) {
                setData(statement, StockCountHistoryQuery.whereValues(filter));
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    return new long[]{rs.getLong("sheets"), rs.getLong("line_count"), rs.getLong("difference_count")};
                }
            }
        });
    }

    /** One sheet's header with who entered it and its two counts, or {@code null} when it is gone. */
    public StockCountSummary summary(int countId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(StockCountHistoryQuery.HEADER)) {
                statement.setInt(1, countId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? summary(rs) : null;
                }
            }
        });
    }

    /** What the posted sheets of the filter found, per item, up to {@code limit} items. */
    public List<StockCountVarianceRow> variance(StockCountHistoryFilter filter, int limit) throws DaoException {
        Object[] where = StockCountHistoryQuery.whereValues(filter.postedOnly());
        Object[] values = Arrays.copyOf(where, where.length + 1);
        values[where.length] = limit;
        return withConnection(connection -> {
            List<StockCountVarianceRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(StockCountHistoryQuery.VARIANCE)) {
                setData(statement, values);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new StockCountVarianceRow(rs.getInt("item_id"), rs.getString("barcode"),
                                rs.getString("nameItem"), rs.getString("unit_name"), rs.getInt("counts"),
                                rs.getDouble("surplus"), rs.getDouble("shortage"), rs.getDouble("net")));
                    }
                }
            }
            return rows;
        });
    }

    /** A warehouse's items in use, in shelf order, for the paper a count starts from. */
    public List<StockCountBlankSheet.Row> blankSheetRows(int stockId) throws DaoException {
        return withConnection(connection -> {
            List<StockCountBlankSheet.Row> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(StockCountBlankSheet.SQL)) {
                statement.setInt(1, stockId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new StockCountBlankSheet.Row(rs.getString("barcode"), rs.getString("nameItem"),
                                rs.getString("group_name"), rs.getString("unit_name")));
                    }
                }
            }
            return rows;
        });
    }

    private static StockCountSummary summary(ResultSet rs) throws SQLException {
        Timestamp posted = rs.getTimestamp("posted_at");
        return new StockCountSummary(
                rs.getInt("id"),
                rs.getObject("count_date", LocalDate.class),
                rs.getInt("stock_id"),
                rs.getString("stock_name"),
                StockCountStatus.of(rs.getString("status")),
                rs.getString("notes"),
                posted == null ? null : posted.toLocalDateTime(),
                rs.getString("user_name"),
                rs.getInt("line_count"),
                rs.getInt("difference_count"));
    }

    public List<StockCountLine> linesOf(int countId) throws DaoException {
        return withConnection(connection -> {
            List<StockCountLine> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_LINES)) {
                statement.setInt(1, countId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        lines.add(new StockCountLine(
                                rs.getInt("id"),
                                rs.getInt("item_id"),
                                rs.getString("nameItem"),
                                rs.getString("barcode"),
                                rs.getInt("unit_id"),
                                rs.getString("unit_name") == null ? "" : rs.getString("unit_name"),
                                rs.getDouble("type_value"),
                                rs.getDouble("system_qty"),
                                rs.getDouble("counted_qty")));
                    }
                }
            }
            return lines;
        });
    }

    /**
     * Saves the sheet as it now stands: the header, then its lines, replaced wholesale.
     * <p>
     * Replacing rather than diffing is deliberate. A count is edited by adding, removing
     * and retyping lines with no natural key beyond (item, unit), and reconciling that
     * by hand is where the "one line saved twice" bugs live. The sheet is small - a few
     * hundred rows at worst - and it all happens inside one transaction.
     */
    public int save(StockCount count) throws DaoException {
        return insertMultiData(() -> {
            if (count.isNew()) {
                count.setId(insertHeader(count));
            } else {
                updateHeader(count);
                deleteLines(count.getId());
            }
            insertLines(count);
        });
    }

    /**
     * Posts the sheet: nothing but the status and the moment it happened.
     * <p>
     * The lines are already stored with the system quantity that was on screen, so
     * posting moves no numbers around - it flips the flag that {@code adjustment_agg}
     * filters on, and the balance changes because the view now sees the rows. The
     * {@code status = 'DRAFT'} in the WHERE is what makes posting twice impossible even
     * if two windows press the button together.
     */
    public int post(int countId) throws DaoException {
        return executeUpdate(
                "UPDATE stock_count SET status = 'POSTED', posted_at = ? WHERE id = ? AND status = 'DRAFT'",
                Timestamp.valueOf(LocalDateTime.now()), countId);
    }

    /**
     * Removes a draft. The {@code status = 'DRAFT'} guard is the real protection: a
     * posted count has moved stock, and deleting it would move the stock back with no
     * record that anything happened. The lines go with it - {@code count_id} cascades.
     */
    public int deleteDraft(int countId) throws DaoException {
        return executeUpdate("DELETE FROM stock_count WHERE id = ? AND status = 'DRAFT'", countId);
    }

    private int insertHeader(StockCount count) throws DaoException {
        String query = """
                INSERT INTO stock_count (stock_id, count_date, status, notes, user_id)
                VALUES (?, ?, ?, ?, ?)
                """;
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, count.getStockId());
                statement.setObject(2, count.getCountDate());
                statement.setString(3, count.getStatus().name());
                statement.setString(4, count.getNotes());
                statement.setInt(5, count.getUserId());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("stock_count returned no generated id");
            }
        });
    }

    private void updateHeader(StockCount count) throws DaoException {
        executeUpdate("UPDATE stock_count SET count_date = ?, notes = ? WHERE id = ? AND status = 'DRAFT'",
                count.getCountDate(), count.getNotes(), count.getId());
    }

    private void deleteLines(int countId) throws DaoException {
        executeUpdate("DELETE FROM stock_count_lines WHERE count_id = ?", countId);
    }

    private void insertLines(StockCount count) throws DaoException {
        if (count.getLines().isEmpty()) {
            return;
        }
        String query = """
                INSERT INTO stock_count_lines (count_id, item_id, unit_id, type_value, system_qty, counted_qty)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                for (StockCountLine line : count.getLines()) {
                    statement.setInt(1, count.getId());
                    statement.setInt(2, line.getItemId());
                    statement.setInt(3, line.getUnitId());
                    statement.setDouble(4, line.getTypeValue());
                    statement.setDouble(5, line.getSystemQuantity());
                    statement.setDouble(6, line.getCountedQuantity());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    @Override
    public StockCount map(ResultSet rs) throws DaoException {
        try {
            StockCount count = new StockCount();
            count.setId(rs.getInt("id"));
            count.setStockId(rs.getInt("stock_id"));
            LocalDate date = rs.getObject("count_date", LocalDate.class);
            count.setCountDate(date == null ? LocalDate.now() : date);
            count.setStatus(StockCountStatus.of(rs.getString("status")));
            count.setNotes(rs.getString("notes"));
            Timestamp posted = rs.getTimestamp("posted_at");
            count.setPostedAt(posted == null ? null : posted.toLocalDateTime());
            count.setUserId(rs.getInt("user_id"));
            return count;
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
