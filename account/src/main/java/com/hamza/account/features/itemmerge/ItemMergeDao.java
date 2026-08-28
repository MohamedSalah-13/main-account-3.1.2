package com.hamza.account.features.itemmerge;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The statements of a merge, executed.
 * <p>
 * Every method here is one statement from {@link ItemMergeStatements} and nothing else:
 * no rule is decided at this level and no permission is read, so the whole of what a
 * merge <em>means</em> stays in {@link ItemMergeService} where it can be tested. The
 * ordering matters a great deal and is the service's business too - the barcodes have
 * to be rescued before the source row is deleted, the units moved before the barcodes
 * left behind are read.
 * <p>
 * Nothing here opens a transaction. They all run inside the service's one.
 */
public class ItemMergeDao extends AbstractDao<MergeItem> {

    public ItemMergeDao() {
        super();
    }

    // ---- reading -------------------------------------------------------------

    /** The item, or null when there is no such row. */
    public MergeItem findItem(int id) throws DaoException {
        return queryForObject(ItemMergeStatements.SELECT_ITEM, this::mapItem, id);
    }

    private MergeItem mapItem(ResultSet resultSet) throws SQLException {
        BigDecimal first = resultSet.getBigDecimal("first_balance");
        return new MergeItem(resultSet.getInt("id"),
                resultSet.getString("nameItem"),
                resultSet.getString("barcode"),
                resultSet.getInt("unit_id"),
                resultSet.getBoolean("item_has_validity"),
                first == null ? BigDecimal.ZERO : first);
    }

    /**
     * The items that share a group with at least one other, by {@code groupBy}.
     * <p>
     * {@code limit} is inlined rather than bound because it is a {@code LIMIT}, which
     * MySQL will not take as a parameter in a prepared statement; it is clamped here so
     * the only thing that reaches the text is a number this method chose.
     */
    public List<ItemMergeCandidate> candidates(MergeGroupBy groupBy, int limit) throws DaoException {
        String query = ItemMergeStatements.candidates(groupBy, Math.clamp(limit, 1, 2000));
        return withConnection(connection -> {
            List<ItemMergeCandidate> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Date lastMovement = resultSet.getDate("last_movement");
                    rows.add(new ItemMergeCandidate(
                            resultSet.getInt("id"),
                            resultSet.getString("nameItem"),
                            resultSet.getString("barcode"),
                            resultSet.getInt("unit_id"),
                            resultSet.getString("unit_name"),
                            resultSet.getBoolean("item_has_validity"),
                            resultSet.getBigDecimal("sel_price1"),
                            resultSet.getBigDecimal("first_balance"),
                            resultSet.getString("group_key"),
                            resultSet.getInt("line_count"),
                            lastMovement == null ? null : lastMovement.toLocalDate()));
                }
            }
            return rows;
        });
    }

    /**
     * How many rows each declared reference holds for this item, in registry order.
     * <p>
     * Keyed by {@link ItemReference#qualified()} because {@code items_package} appears
     * twice - once as the thing packaged and once as the package.
     */
    public Map<String, Integer> countRows(int itemId) throws DaoException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemReference reference : ItemReferenceRegistry.ALL) {
            counts.put(reference.qualified(), countOf(ItemMergeStatements.count(reference), itemId));
        }
        return counts;
    }

    /**
     * Document and count-sheet lines of this item dated on or before {@code lockedUntil}.
     * <p>
     * Reported, never refused: a merge changes no figure in a closed period, only which
     * item the figures are filed under. Saying so in the preview is the point.
     */
    public int countLockedLines(int itemId, LocalDate lockedUntil) throws DaoException {
        if (lockedUntil == null) {
            return 0;
        }
        Date day = Date.valueOf(lockedUntil);
        return countOf(ItemMergeStatements.COUNT_LOCKED_SALES, itemId, day)
               + countOf(ItemMergeStatements.COUNT_LOCKED_SALES_RETURN, itemId, day)
               + countOf(ItemMergeStatements.COUNT_LOCKED_PURCHASE, itemId, day)
               + countOf(ItemMergeStatements.COUNT_LOCKED_PURCHASE_RETURN, itemId, day)
               + countOf(ItemMergeStatements.COUNT_LOCKED_STOCK_COUNT_LINES, itemId, day);
    }

    private int countOf(String query, Object... parameters) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                setData(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            }
        });
    }

    // ---- moving --------------------------------------------------------------

    /** A reference whose rows nothing can stop the target owning as well. */
    public int move(ItemReference reference, int targetId, int sourceId) throws DaoException {
        return executeUpdate(ItemMergeStatements.move(reference), targetId, sourceId);
    }

    /**
     * The count sheets: what both items were counted for on one sheet in one unit
     * becomes a single line, then whatever is left moves normally.
     */
    public int mergeStockCountLines(int targetId, int sourceId) throws DaoException {
        executeUpdate(ItemMergeStatements.SUM_STOCK_COUNT_LINES, targetId, sourceId);
        executeUpdate(ItemMergeStatements.DELETE_SUMMED_STOCK_COUNT_LINES, targetId, sourceId);
        return executeUpdate(ItemMergeStatements.move(ItemReferenceRegistry.STOCK_COUNT_LINES), targetId, sourceId);
    }

    /** A row for every warehouse the source was in and the target is not. */
    public int fillMissingItemsStock(int targetId, int sourceId) throws DaoException {
        return executeUpdate(ItemMergeStatements.INSERT_MISSING_ITEMS_STOCK, targetId, targetId, sourceId);
    }

    public int addItemsStockBalances(int targetId, int sourceId) throws DaoException {
        return executeUpdate(ItemMergeStatements.ADD_ITEMS_STOCK_BALANCES, targetId, sourceId);
    }

    /** The source's units, except those the target already has and its base unit. */
    public int moveUnits(int targetId, int sourceId, int targetBaseUnitId) throws DaoException {
        return executeUpdate(ItemMergeStatements.MOVE_ABSENT_UNITS, targetId, targetId, sourceId, targetBaseUnitId);
    }

    /**
     * Every code the source still answers to, kept on the target: its extra barcodes
     * move, and the two that would be destroyed with the row - the item's own and those
     * of the unit rows that stayed behind - are copied in.
     * <p>
     * Call after {@link #moveUnits} and before the source is deleted. Both orders matter
     * and neither is enforceable from here.
     */
    public int keepBarcodes(int targetId, int sourceId) throws DaoException {
        int moved = executeUpdate(ItemMergeStatements.MOVE_EXTRA_BARCODES, targetId, sourceId);
        moved += executeUpdate(ItemMergeStatements.KEEP_ITEM_BARCODE, targetId, sourceId, sourceId);
        moved += executeUpdate(ItemMergeStatements.KEEP_UNIT_BARCODES, targetId, sourceId, sourceId);
        return moved;
    }

    /** The compositions, both columns, then the duplicates and self-references that produces. */
    public int mergePackages(int targetId, int sourceId) throws DaoException {
        int moved = executeUpdate(ItemMergeStatements.MOVE_PACKAGE_ITEM, targetId, sourceId);
        moved += executeUpdate(ItemMergeStatements.MOVE_PACKAGE_PACKAGE, targetId, sourceId);
        executeUpdate(ItemMergeStatements.DELETE_DUPLICATE_PACKAGES);
        executeUpdate(ItemMergeStatements.DELETE_SELF_PACKAGES);
        return moved;
    }

    /** Adds the source's opening balance to the target's. */
    public int addFirstBalance(BigDecimal balance, int targetId) throws DaoException {
        if (balance == null || balance.signum() == 0) {
            return 0;
        }
        return executeUpdate(ItemMergeStatements.ADD_FIRST_BALANCE, balance, targetId);
    }

    // ---- the log -------------------------------------------------------------

    /** Writes the merge and its per-table counts, and answers the log row's id. */
    public int log(ItemMergePreview preview, int userId, String userName) throws DaoException {
        int mergeId = withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    ItemMergeStatements.INSERT_LOG, Statement.RETURN_GENERATED_KEYS)) {
                MergeItem source = preview.source();
                MergeItem target = preview.target();
                statement.setInt(1, target.id());
                statement.setString(2, target.name());
                statement.setInt(3, source.id());
                statement.setString(4, source.name());
                statement.setString(5, source.barcode());
                statement.setBigDecimal(6, source.firstBalance());
                statement.setInt(7, preview.lockedPeriodLines());
                statement.setInt(8, userId);
                statement.setString(9, userName);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : 0;
                }
            }
        });

        for (Map.Entry<String, Integer> row : preview.rows().entrySet()) {
            if (row.getValue() > 0) {
                executeUpdate(ItemMergeStatements.INSERT_LOG_LINE, mergeId, row.getKey(), row.getValue());
            }
        }
        return mergeId;
    }
}
