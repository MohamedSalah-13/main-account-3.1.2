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
import java.util.Locale;
import java.util.Set;

/** JDBC persistence for the item-group manager. All user input is bound as parameters. */
public final class JdbcItemGroupRepository extends AbstractDao<Object> implements ItemGroupRepository {

    @Override
    public List<ItemGroupSummary> findGroups(String search) throws DaoException {
        String sql = """
                SELECT mg.id AS main_id, mg.name_g AS main_name,
                       sg.id AS sub_id, sg.name AS sub_name, COUNT(i.id) AS item_count
                FROM main_group mg
                         JOIN sub_group sg ON sg.main_id = mg.id
                         LEFT JOIN items i ON i.sub_num = sg.id
                            AND (? = '' OR LOWER(i.nameItem) LIKE ? OR i.barcode LIKE ?
                                 OR CAST(i.id AS CHAR) = ?)
                GROUP BY mg.id, mg.name_g, sg.id, sg.name
                HAVING ? = '' OR COUNT(i.id) > 0
                ORDER BY mg.name_g, sg.name
                """;
        String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
        return withConnection(connection -> {
            List<ItemGroupSummary> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, search);
                statement.setString(2, like);
                statement.setString(3, "%" + search + "%");
                statement.setString(4, search);
                statement.setString(5, search);
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
        String sql = """
                SELECT i.id, i.nameItem, i.barcode, i.sub_num, i.item_active
                FROM items i
                WHERE i.sub_num = ?
                  AND (? = '' OR LOWER(i.nameItem) LIKE ? OR i.barcode LIKE ? OR CAST(i.id AS CHAR) = ?)
                ORDER BY i.nameItem, i.id
                LIMIT ? OFFSET ?
                """;
        String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
        return withConnection(connection -> {
            List<ItemGroupItem> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, subGroupId);
                statement.setString(2, search);
                statement.setString(3, like);
                statement.setString(4, "%" + search + "%");
                statement.setString(5, search);
                statement.setInt(6, limit);
                statement.setInt(7, offset);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new ItemGroupItem(rows.getInt("id"), rows.getString("nameItem"),
                                rows.getString("barcode"), rows.getInt("sub_num"),
                                rows.getBoolean("item_active")));
                    }
                }
            }
            return result;
        });
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
                        result.add(new ItemGroupItem(rows.getInt("id"), rows.getString("nameItem"),
                                rows.getString("barcode"), rows.getInt("sub_num"),
                                rows.getBoolean("item_active")));
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
