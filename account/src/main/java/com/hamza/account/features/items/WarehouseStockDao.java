package com.hamza.account.features.items;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One warehouse's rows of {@code items_stock}: how to lock them, and what they hold.
 * <p>
 * <b>The lock is the invariant, and it only works if there is one of it.</b> Every writer that
 * takes stock out of a warehouse - a document through {@code InvoiceStockGuard}, a transfer,
 * a posted count - locks these same rows {@code FOR UPDATE} <b>in item-id order</b>, which is
 * what makes them queue behind each other instead of deadlocking or racing. A second copy of
 * that statement somewhere else is a second chance to order it differently, so it lives here
 * and the writers call it.
 * <p>
 * The balance is {@link ItemStockBalanceSql}, not {@code quantity_items_table}: each of that
 * view's seven CTEs is a {@code GROUP BY} over a whole line table, and MySQL builds all of them
 * before it can answer for one item. The helper computes the view's own columns for named items
 * through the item's index, and {@code ItemStockBalanceSqlTest} holds the two definitions
 * together.
 * <p>
 * Both methods join whatever transaction is open on the calling thread, which is how a lock
 * taken here is still held while the caller writes.
 */
public class WarehouseStockDao extends AbstractDao<Void> implements WarehouseOpeningBalance.Reader {

    /**
     * Locks each item's row in {@code stockId} and answers the item names, lowest id first.
     * <p>
     * A missing entry means the item has no row in that warehouse - it cannot be drawn from
     * and its absence is the caller's to refuse, because what that means differs: goods cannot
     * leave a shelf they were never on, while a shelf they are arriving at is created.
     */
    public Map<Integer, String> lockItems(int stockId, List<Integer> itemIds) throws DaoException {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String marks = String.join(",", Collections.nCopies(itemIds.size(), "?"));
        String sql = "SELECT s.item_id, i.nameItem FROM items_stock s JOIN items i ON i.id = s.item_id "
                + "WHERE s.stock_id = ? AND s.item_id IN (" + marks + ") ORDER BY s.item_id FOR UPDATE";
        return withConnection(connection -> {
            Map<Integer, String> names = new LinkedHashMap<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, stockId);
                bind(statement, 2, itemIds);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        names.put(rows.getInt(1), rows.getString(2));
                    }
                }
            }
            return names;
        });
    }

    /** Each requested item's current balance in {@code stockId}, in base units. */
    public Map<Integer, Double> balances(int stockId, List<Integer> itemIds) throws DaoException {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT item_id, first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment"
                + " - quantitySales - quantityPurchaseRe - fromStock AS balance FROM ("
                + ItemStockBalanceSql.forItems(itemIds.size()) + ") balances_for_items";
        return withConnection(connection -> {
            Map<Integer, Double> balances = new HashMap<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, stockId);
                bind(statement, 2, itemIds);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        balances.put(rows.getInt(1), rows.getDouble(2));
                    }
                }
            }
            return balances;
        });
    }

    /**
     * Gives each item a row in {@code stockId} if it has none, at zero.
     * <p>
     * {@code quantity_items_table} is driven by {@code items_stock}, so an item with no row
     * there has no balance in that warehouse at all - and anything written about it, a posted
     * count's adjustment included, is summed into a row the view never reads. A silent nothing.
     * Every warehouse and every item created since {@code fbadd53} gets its rows, and {@code V18}
     * backfilled the ones that predate it, but a row seeded outside the application still will
     * not have them.
     */
    public void ensureRows(int stockId, List<Integer> itemIds) throws DaoException {
        if (itemIds.isEmpty()) {
            return;
        }
        withConnection(connection -> {
            String sql = "INSERT IGNORE INTO items_stock(item_id, stock_id, first_balance) VALUES (?, ?, 0)";
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

    /** {@link WarehouseOpeningBalance#MOVEMENT_COUNTS} for one item in one warehouse. */
    @Override
    public Map<String, Integer> openingMovementCounts(int itemId, int stockId) throws DaoException {
        return withConnection(connection -> {
            Map<String, Integer> counts = new HashMap<>();
            try (var statement = connection.prepareStatement(WarehouseOpeningBalance.MOVEMENT_COUNTS)) {
                int parameter = 1;
                for (int i = 0; i < ItemStockBalanceSql.MOVEMENTS.size(); i++) {
                    statement.setInt(parameter++, stockId);
                    statement.setInt(parameter++, itemId);
                }
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        for (ItemStockBalanceSql.Movement movement : ItemStockBalanceSql.MOVEMENTS) {
                            counts.put(movement.column(), rows.getInt(movement.column()));
                        }
                    }
                }
            }
            return counts;
        });
    }

    /** The one opening balance there is since V78: {@code items_stock.first_balance}. */
    @Override
    public double openingBalance(int itemId, int stockId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT first_balance FROM items_stock WHERE item_id = ? AND stock_id = ?")) {
                statement.setInt(1, itemId);
                statement.setInt(2, stockId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? rows.getDouble(1) : 0d;
                }
            }
        });
    }

    private static void bind(java.sql.PreparedStatement statement, int from, List<Integer> itemIds)
            throws java.sql.SQLException {
        int parameter = from;
        for (int itemId : itemIds) {
            statement.setInt(parameter++, itemId);
        }
    }

    @Override
    public Void map(java.sql.ResultSet rs) {
        throw new UnsupportedOperationException("This DAO answers maps, not rows");
    }

    /**
     * The warehouse's name when it is switched off (V77), empty when it is in use - what a writer of a
     * new movement asks before it writes. Read here, where every other question about a warehouse's
     * rows is read, so the invoice, the transfer and the count cannot each decide it their own way.
     */
    public java.util.Optional<String> nameIfInactive(int stockId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT stock_name FROM stocks WHERE stock_id = ? AND is_active = 0")) {
                statement.setInt(1, stockId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? java.util.Optional.of(rows.getString(1)) : java.util.Optional.<String>empty();
                }
            }
        });
    }

    /**
     * How many items the warehouse still holds a balance of - what stops it being switched off.
     * <p>
     * Read from {@code quantity_items_table} for the one warehouse: a question about every item it
     * holds, asked on a rare administrative act, is the catalogue-wide read the view is for (see
     * {@code ItemStockBalanceSql} for when it is not). A millionth of a unit is nothing, the same
     * epsilon the transfer judges a balance with.
     */
    public int itemsHolding(int stockId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM quantity_items_table
                    WHERE stock_id = ?
                      AND ABS(first_balance + quantityPurchase + quantitySalesRe + toStock + adjustment
                              - quantitySales - quantityPurchaseRe - fromStock) > 0.000001""")) {
                statement.setInt(1, stockId);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getInt(1);
                }
            }
        });
    }
}
