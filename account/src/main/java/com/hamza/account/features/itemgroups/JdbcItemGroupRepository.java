package com.hamza.account.features.itemgroups;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** JDBC persistence for the item-group manager. All user input is bound as parameters. */
public final class JdbcItemGroupRepository extends AbstractDao<Object> implements ItemGroupRepository {

    /**
     * What an item has to match, written once so the tree and the list beside it cannot start
     * describing different sets. Three bound parameters, in this order: name, barcode, id.
     * <p>
     * {@code id = ?} rather than the {@code CAST(id AS CHAR) = ?} this used to carry: casting
     * every row of the catalogue to compare it with a string cannot use the primary key, and a
     * search that is not a number binds an id no row has. The two {@code LIKE}s lean on the
     * column's own collation, which is case-insensitive - the {@code LOWER(nameItem)} that used
     * to wrap the name did the same job per row while making the column unindexable, and it was
     * never applied to the barcode anyway, so the two halves of one search disagreed.
     */
    private static final String ITEM_MATCHES = "(nameItem LIKE ? OR barcode LIKE ? OR id = ?)";

    /**
     * <b>The counts are aggregated before the join, not after it.</b> This used to LEFT JOIN
     * {@code items} onto every sub-group and then {@code GROUP BY} four columns, so opening the
     * screen walked the whole catalogue and grouped it by (main, sub) - on a shop with tens of
     * thousands of items, once per debounced keystroke. Grouping {@code items} by {@code sub_num}
     * on its own is served by the foreign key's index, and what comes back is one small row per
     * group.
     */
    private static final String GROUPS_SQL = """
            SELECT mg.id AS main_id, mg.name_g AS main_name,
                   sg.id AS sub_id, sg.name AS sub_name,
                   COALESCE(counts.item_count, 0) AS item_count
            FROM main_group mg
                     JOIN sub_group sg ON sg.main_id = mg.id
                     LEFT JOIN (SELECT sub_num, COUNT(*) AS item_count
                                FROM items
                                GROUP BY sub_num) counts ON counts.sub_num = sg.id
            ORDER BY mg.name_g, sg.name
            """;

    /**
     * The searching half. The filter runs before the aggregate, so only matching items are
     * counted, and the plain {@code JOIN} is what the old {@code HAVING COUNT(i.id) > 0} was
     * for - a group holding nothing that matches simply has no row to join to.
     */
    private static final String MATCHING_GROUPS_SQL = """
            SELECT mg.id AS main_id, mg.name_g AS main_name,
                   sg.id AS sub_id, sg.name AS sub_name, matches.item_count
            FROM main_group mg
                     JOIN sub_group sg ON sg.main_id = mg.id
                     JOIN (SELECT sub_num, COUNT(*) AS item_count
                           FROM items
                           WHERE """ + ITEM_MATCHES + """

                           GROUP BY sub_num) matches ON matches.sub_num = sg.id
            ORDER BY mg.name_g, sg.name
            """;

    private static final String ITEMS_SQL = """
            SELECT id, nameItem, barcode, sub_num, item_active
            FROM items
            WHERE sub_num = ?
            ORDER BY nameItem, id
            LIMIT ? OFFSET ?
            """;

    private static final String MATCHING_ITEMS_SQL = """
            SELECT id, nameItem, barcode, sub_num, item_active
            FROM items
            WHERE sub_num = ? AND """ + ITEM_MATCHES + """

            ORDER BY nameItem, id
            LIMIT ? OFFSET ?
            """;

    @Override
    public List<ItemGroupSummary> findGroups(String search) throws DaoException {
        boolean searching = !search.isEmpty();
        return withConnection(connection -> {
            List<ItemGroupSummary> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(searching ? MATCHING_GROUPS_SQL : GROUPS_SQL)) {
                if (searching) bindMatch(statement, 1, search);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new ItemGroupSummary(
                                rows.getInt("main_id"), rows.getString("main_name"),
                                rows.getInt("sub_id"), rows.getString("sub_name"),
                                rows.getInt("item_count")));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public List<ItemGroupItem> findItems(int subGroupId, String search, int limit, int offset)
            throws DaoException {
        boolean searching = !search.isEmpty();
        return withConnection(connection -> {
            List<ItemGroupItem> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(searching ? MATCHING_ITEMS_SQL : ITEMS_SQL)) {
                int index = 1;
                statement.setInt(index++, subGroupId);
                if (searching) index = bindMatch(statement, index, search);
                statement.setInt(index++, limit);
                statement.setInt(index, offset);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(mapItem(rows));
                    }
                }
            }
            return result;
        });
    }

    private static ItemGroupItem mapItem(java.sql.ResultSet rows) throws java.sql.SQLException {
        return new ItemGroupItem(rows.getInt("id"), rows.getString("nameItem"),
                rows.getString("barcode"), rows.getInt("sub_num"), rows.getBoolean("item_active"));
    }

    /** Binds {@link #ITEM_MATCHES}, and answers the next free parameter index. */
    private static int bindMatch(java.sql.PreparedStatement statement, int index, String search)
            throws java.sql.SQLException {
        String like = "%" + search + "%";
        statement.setString(index, like);
        statement.setString(index + 1, like);
        // A search that is not a number can still be a name or a barcode, so it binds an id no
        // row has rather than dropping the clause and changing the statement a second time.
        statement.setInt(index + 2, asItemId(search));
        return index + 3;
    }

    private static int asItemId(String search) {
        try {
            return Integer.parseInt(search.trim());
        } catch (NumberFormatException notAnId) {
            return 0;
        }
    }

    @Override
    public List<ItemGroupItem> findItemsByIds(Set<Integer> itemIds) throws DaoException {
        if (itemIds.isEmpty()) return List.of();
        String sql = "SELECT id, nameItem, barcode, sub_num, item_active FROM items WHERE id IN ("
                + marks(itemIds.size()) + ") ORDER BY nameItem, id";
        return withConnection(connection -> {
            List<ItemGroupItem> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(sql)) {
                bindIds(statement, itemIds);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(mapItem(rows));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Map<Integer, Integer> lockCurrentGroups(Set<Integer> itemIds) throws DaoException {
        if (itemIds.isEmpty()) return Map.of();
        String marks = marks(itemIds.size());
        String sql = "SELECT id, sub_num FROM items WHERE id IN (" + marks + ") ORDER BY id FOR UPDATE";
        return withConnection(connection -> {
            Map<Integer, Integer> result = new LinkedHashMap<>();
            try (var statement = connection.prepareStatement(sql)) {
                bindIds(statement, itemIds);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) result.put(rows.getInt("id"), rows.getInt("sub_num"));
                }
            }
            return result;
        });
    }

    @Override
    public Set<Integer> existingSubGroups(Set<Integer> subGroupIds) throws DaoException {
        if (subGroupIds.isEmpty()) return Set.of();
        String sql = "SELECT id FROM sub_group WHERE id IN (" + marks(subGroupIds.size()) + ")";
        return withConnection(connection -> {
            Set<Integer> result = new LinkedHashSet<>();
            try (var statement = connection.prepareStatement(sql)) {
                bindIds(statement, subGroupIds);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) result.add(rows.getInt(1));
                }
            }
            return result;
        });
    }

    @Override
    public int moveItems(List<ItemGroupChange> changes, int userId) throws DaoException {
        String sql = "UPDATE items SET sub_num = ?, user_id = ? WHERE id = ? AND sub_num = ?";
        return withConnection(connection -> {
            int moved = 0;
            try (var statement = connection.prepareStatement(sql)) {
                int queued = 0;
                for (ItemGroupChange change : changes) {
                    statement.setInt(1, change.targetSubGroupId());
                    statement.setInt(2, userId);
                    statement.setInt(3, change.itemId());
                    statement.setInt(4, change.sourceSubGroupId());
                    statement.addBatch();
                    queued++;
                    if (queued == 100) {
                        moved += count(statement.executeBatch());
                        queued = 0;
                    }
                }
                if (queued > 0) moved += count(statement.executeBatch());
            }
            return moved;
        });
    }

    private static int count(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result == 1 || result == Statement.SUCCESS_NO_INFO) count++;
        }
        return count;
    }

    private static String marks(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private static void bindIds(java.sql.PreparedStatement statement, Iterable<Integer> ids)
            throws java.sql.SQLException {
        int index = 1;
        for (int id : ids) statement.setInt(index++, id);
    }
}
