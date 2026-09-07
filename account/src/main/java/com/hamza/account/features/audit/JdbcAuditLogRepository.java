package com.hamza.account.features.audit;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** JDBC read model shaped specifically for the audit browser. */
public final class JdbcAuditLogRepository extends AbstractDao<AuditLogEntry> implements AuditLogRepository {

    private static final String SELECT_ROWS = """
            SELECT a.id,
                   a.table_name,
                   a.record_id,
                   a.action_type,
                   a.user_id,
                   COALESCE(NULLIF(a.actor_name, ''), u.user_name, '') AS actor_display,
                   a.action_time,
                   a.old_data,
                   a.new_data,
                   a.source,
                   a.workstation_id,
                   a.workstation_name,
                   a.notes
            FROM audit_log a
            LEFT JOIN users u ON u.id = a.user_id
            """;

    private static final String SELECT_SUMMARY = """
            SELECT COUNT(*) AS total_rows,
                   COALESCE(SUM(a.action_type = 'INSERT'), 0) AS insert_rows,
                   COALESCE(SUM(a.action_type = 'UPDATE'), 0) AS update_rows,
                   COALESCE(SUM(a.action_type = 'DELETE'), 0) AS delete_rows
            FROM audit_log a
            LEFT JOIN users u ON u.id = a.user_id
            """;

    @Override
    public AuditLogPage load(AuditLogQuery query) throws DaoException {
        Filter filter = filter(query);
        return withConnection(connection -> {
            AuditLogSummary summary = summary(connection, filter);
            List<AuditLogEntry> rows = rows(connection, query, filter);
            return new AuditLogPage(rows, summary);
        });
    }

    @Override
    public AuditLogOptions options() throws DaoException {
        return withConnection(connection -> new AuditLogOptions(users(connection), tables(connection)));
    }

    @Override
    public List<AuditLogEntry> exportRows(AuditLogQuery query, int limit) throws DaoException {
        Filter filter = filter(query);
        return withConnection(connection -> {
            String sql = SELECT_ROWS + filter.sql() + " ORDER BY " + query.sort().orderBy() + " LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                List<Object> parameters = new ArrayList<>(filter.parameters());
                parameters.add(Math.max(1, limit));
                bind(statement, parameters);
                try (ResultSet result = statement.executeQuery()) {
                    List<AuditLogEntry> rows = new ArrayList<>();
                    while (result.next()) rows.add(map(result));
                    return rows;
                }
            }
        });
    }

    @Override
    public void recordExport(AuditExportFormat format, AuditLogQuery query, int rows, String fileName)
            throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    CALL write_audit_admin_event('EXPORT', NULL, ?,
                        JSON_OBJECT('format', ?, 'file_name', ?, 'from_date', ?, 'to_date', ?))
                    """)) {
                statement.setInt(1, rows);
                statement.setString(2, format.name());
                statement.setString(3, fileName);
                statement.setString(4, query.from().toString());
                statement.setString(5, query.to().toString());
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public int delete(List<Long> ids, String reason) throws DaoException {
        LinkedHashSet<Long> safeIds = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(id -> id != null && id > 0).forEach(safeIds::add);
        }
        if (safeIds.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(safeIds.size(), "?"));
        return withConnection(connection -> {
            try (PreparedStatement context = connection.prepareStatement("SET @app_audit_delete_reason = ?")) {
                context.setString(1, reason);
                context.executeUpdate();
            }
            try {
                int deleted;
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM audit_log WHERE id IN (" + placeholders + ")")) {
                    int index = 1;
                    for (Long id : safeIds) statement.setLong(index++, id);
                    deleted = statement.executeUpdate();
                }
                try (PreparedStatement event = connection.prepareStatement("""
                        CALL write_audit_admin_event('DELETE_SELECTED', ?, ?,
                            JSON_OBJECT('requested_rows', ?))
                        """)) {
                    event.setString(1, reason);
                    event.setInt(2, deleted);
                    event.setInt(3, safeIds.size());
                    event.execute();
                }
                return deleted;
            } finally {
                try (PreparedStatement clear = connection.prepareStatement("SET @app_audit_delete_reason = NULL")) {
                    clear.executeUpdate();
                }
            }
        });
    }

    @Override
    public AuditRetentionPolicy retentionPolicy() throws DaoException {
        return withConnection(connection -> {
            boolean enabled = false;
            int days = AuditRetentionPolicy.DEFAULT_DAYS;
            LocalDateTime lastRun = null;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT setting_key, setting_value FROM app_setting
                    WHERE setting_key IN ('audit.retention.enabled', 'audit.retention.days',
                                          'audit.retention.last_run')
                    """); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String key = rows.getString(1);
                    String value = rows.getString(2);
                    if ("audit.retention.enabled".equals(key)) enabled = Boolean.parseBoolean(value);
                    else if ("audit.retention.days".equals(key)) days = parseDays(value);
                    else if ("audit.retention.last_run".equals(key)) lastRun = parseDateTime(value);
                }
            }
            return new AuditRetentionPolicy(enabled, days, lastRun);
        });
    }

    @Override
    public void saveRetentionPolicy(AuditRetentionPolicy policy, String reason) throws DaoException {
        withConnection(connection -> {
            String sql = """
                    INSERT INTO app_setting(setting_key, setting_value, updated_by)
                    VALUES (?, ?, @app_user_id)
                    ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value),
                                            updated_by = VALUES(updated_by)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setting(statement, "audit.retention.enabled", Boolean.toString(policy.enabled()));
                setting(statement, "audit.retention.days", Integer.toString(policy.days()));
                statement.executeBatch();
            }
            try (PreparedStatement event = connection.prepareStatement("""
                    CALL write_audit_admin_event('RETENTION_POLICY', ?, 0,
                        JSON_OBJECT('enabled', ?, 'days', ?))
                    """)) {
                event.setString(1, reason);
                event.setBoolean(2, policy.enabled());
                event.setInt(3, policy.days());
                event.execute();
            }
            return null;
        });
    }

    @Override
    public long countBefore(LocalDateTime cutoff) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM audit_log WHERE action_time < ?")) {
                statement.setTimestamp(1, Timestamp.valueOf(cutoff));
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? row.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public int purgeBefore(LocalDateTime cutoff, String reason, boolean automatic) throws DaoException {
        return withConnection(connection -> {
            if (automatic) {
                try (PreparedStatement system = connection.prepareStatement("""
                        SET @app_audit_source = 'SYSTEM', @app_user_id = NULL, @app_actor_name = NULL
                        """)) {
                    system.executeUpdate();
                }
            }
            int deleted;
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM audit_log WHERE action_time < ?")) {
                statement.setTimestamp(1, Timestamp.valueOf(cutoff));
                deleted = statement.executeUpdate();
            }
            try (PreparedStatement event = connection.prepareStatement("""
                    CALL write_audit_admin_event('RETENTION_CLEANUP', ?, ?,
                        JSON_OBJECT('cutoff', ?, 'automatic', ?))
                    """)) {
                event.setString(1, reason);
                event.setInt(2, deleted);
                event.setString(3, cutoff.toString());
                event.setBoolean(4, automatic);
                event.execute();
            }
            try (PreparedStatement setting = connection.prepareStatement("""
                    INSERT INTO app_setting(setting_key, setting_value, updated_by)
                    VALUES ('audit.retention.last_run', ?, @app_user_id)
                    ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value),
                                            updated_by = VALUES(updated_by)
                    """)) {
                setting.setString(1, LocalDateTime.now().toString());
                setting.executeUpdate();
            }
            return deleted;
        });
    }

    private static void setting(PreparedStatement statement, String key, String value) throws SQLException {
        statement.setString(1, key);
        statement.setString(2, value);
        statement.addBatch();
    }

    private static int parseDays(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed < AuditRetentionPolicy.MIN_DAYS || parsed > AuditRetentionPolicy.MAX_DAYS
                    ? AuditRetentionPolicy.DEFAULT_DAYS : parsed;
        } catch (NumberFormatException ignored) {
            return AuditRetentionPolicy.DEFAULT_DAYS;
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private AuditLogSummary summary(Connection connection, Filter filter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SUMMARY + filter.sql())) {
            bind(statement, filter.parameters());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return AuditLogSummary.EMPTY;
                return new AuditLogSummary(result.getLong("total_rows"), result.getLong("insert_rows"),
                        result.getLong("update_rows"), result.getLong("delete_rows"));
            }
        }
    }

    private List<AuditLogEntry> rows(Connection connection, AuditLogQuery query, Filter filter)
            throws SQLException, DaoException {
        String sql = SELECT_ROWS + filter.sql() + " ORDER BY " + query.sort().orderBy() + " LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            List<Object> parameters = new ArrayList<>(filter.parameters());
            parameters.add(query.pageSize());
            parameters.add(query.offset());
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<AuditLogEntry> rows = new ArrayList<>();
                while (result.next()) rows.add(map(result));
                return rows;
            }
        }
    }

    private List<AuditUserOption> users(Connection connection) throws SQLException {
        String sql = """
                SELECT a.user_id, COALESCE(MAX(NULLIF(a.actor_name, '')), MAX(u.user_name), '') AS actor_display
                FROM audit_log a
                LEFT JOIN users u ON u.id = a.user_id
                WHERE a.user_id IS NOT NULL
                GROUP BY a.user_id
                ORDER BY actor_display, a.user_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<AuditUserOption> users = new ArrayList<>();
            while (result.next()) {
                users.add(new AuditUserOption(result.getInt("user_id"), result.getString("actor_display")));
            }
            return users;
        }
    }

    private List<String> tables(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT table_name FROM audit_log ORDER BY table_name");
             ResultSet result = statement.executeQuery()) {
            List<String> tables = new ArrayList<>();
            while (result.next()) tables.add(result.getString("table_name"));
            return tables;
        }
    }

    static Filter filter(AuditLogQuery query) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        conditions.add("a.action_time >= ?");
        parameters.add(Timestamp.valueOf(query.from().atStartOfDay()));
        conditions.add("a.action_time < ?");
        parameters.add(Timestamp.valueOf(query.to().plusDays(1).atStartOfDay()));

        if (!query.search().isEmpty()) {
            String like = "%" + escapeLike(query.search()) + "%";
            conditions.add("""
                    (a.record_id LIKE ? ESCAPE '!'
                     OR a.table_name LIKE ? ESCAPE '!'
                     OR a.action_type LIKE ? ESCAPE '!'
                     OR a.source LIKE ? ESCAPE '!'
                     OR a.actor_name LIKE ? ESCAPE '!'
                     OR u.user_name LIKE ? ESCAPE '!'
                     OR a.notes LIKE ? ESCAPE '!')""");
            for (int i = 0; i < 7; i++) parameters.add(like);
        }
        if (query.userId() != null) {
            conditions.add("a.user_id = ?");
            parameters.add(query.userId());
        }
        if (query.action().action() != null) {
            conditions.add("a.action_type = ?");
            parameters.add(query.action().action().name());
        }
        if (!query.tableName().isEmpty()) {
            conditions.add("a.table_name = ?");
            parameters.add(query.tableName());
        }
        if (query.source().databaseValue() != null) {
            conditions.add("a.source = ?");
            parameters.add(query.source().databaseValue());
        }
        return new Filter(" WHERE " + String.join(" AND ", conditions), List.copyOf(parameters));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
    }

    @Override
    public AuditLogEntry map(ResultSet result) throws DaoException {
        try {
            return new AuditLogEntry(
                    result.getLong("id"),
                    result.getString("table_name"),
                    result.getString("record_id"),
                    AuditAction.valueOf(result.getString("action_type")),
                    nullableInteger(result, "user_id"),
                    result.getString("actor_display"),
                    result.getTimestamp("action_time").toLocalDateTime(),
                    result.getString("old_data"),
                    result.getString("new_data"),
                    result.getString("source"),
                    result.getString("workstation_id"),
                    result.getString("workstation_name"),
                    result.getString("notes"));
        } catch (SQLException | IllegalArgumentException error) {
            throw new DaoException(error);
        }
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    record Filter(String sql, List<Object> parameters) {
    }
}
