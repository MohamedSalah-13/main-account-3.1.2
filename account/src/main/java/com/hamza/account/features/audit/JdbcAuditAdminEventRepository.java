package com.hamza.account.features.audit;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Index-backed JDBC read model for the immutable audit-administration journal. */
public final class JdbcAuditAdminEventRepository extends AbstractDao<AuditAdminEvent>
        implements AuditAdminEventRepository {

    private static final String SELECT_ROWS = """
            SELECT e.id, e.event_type, e.actor_user_id,
                   COALESCE(NULLIF(e.actor_name, ''), u.user_name, '') AS actor_display,
                   e.source, e.workstation_id, e.workstation_name, e.occurred_at,
                   e.reason, e.affected_rows, e.details
            FROM audit_admin_event e
            LEFT JOIN users u ON u.id = e.actor_user_id
            """;

    private static final String SELECT_SUMMARY = """
            SELECT COUNT(*) AS total_rows,
                   COALESCE(SUM(e.event_type IN ('EXPORT', 'ADMIN_EXPORT')), 0) AS export_rows,
                   COALESCE(SUM(e.event_type = 'DELETE_SELECTED'), 0) AS delete_rows,
                   COALESCE(SUM(e.event_type = 'RETENTION_POLICY'), 0) AS policy_rows,
                   COALESCE(SUM(e.event_type = 'RETENTION_CLEANUP'), 0) AS cleanup_rows
            FROM audit_admin_event e
            LEFT JOIN users u ON u.id = e.actor_user_id
            """;

    @Override
    public AuditAdminEventPage load(AuditAdminEventQuery query) throws DaoException {
        Filter filter = filter(query);
        return withConnection(connection -> new AuditAdminEventPage(
                rows(connection, query, filter), summary(connection, filter)));
    }

    @Override
    public AuditAdminOptions options() throws DaoException {
        return withConnection(connection -> new AuditAdminOptions(users(connection), eventTypes(connection)));
    }

    @Override
    public AuditActivitySnapshot activity(LocalDate today) throws DaoException {
        LocalDate safeToday = today == null ? LocalDate.now() : today;
        LocalDateTime todayStart = safeToday.atStartOfDay();
        LocalDateTime tomorrowStart = safeToday.plusDays(1).atStartOfDay();
        LocalDateTime sevenDaysStart = safeToday.minusDays(6).atStartOfDay();
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT
                      (SELECT COUNT(*) FROM audit_log
                       WHERE action_time >= ? AND action_time < ?) AS changes_today,
                      (SELECT COUNT(*) FROM audit_admin_event
                       WHERE occurred_at >= ? AND occurred_at < ?) AS admin_last_seven,
                      ((SELECT COUNT(*) FROM audit_log
                        WHERE source = 'DATABASE' AND action_time >= ? AND action_time < ?)
                       +
                       (SELECT COUNT(*) FROM audit_admin_event
                        WHERE source = 'DATABASE' AND occurred_at >= ? AND occurred_at < ?)) AS direct_last_seven,
                      (SELECT COUNT(*) FROM audit_log
                       WHERE table_name IN ('AUTH_ROLE', 'AUTH_ROLE_PERMISSION', 'AUTH_USER_ROLE',
                                            'AUTH_ROLE_INHERITANCE', 'AUTH_USER_PERMISSION_OVERRIDE')
                         AND action_time >= ? AND action_time < ?) AS authorization_last_seven
                    """)) {
                statement.setTimestamp(1, Timestamp.valueOf(todayStart));
                statement.setTimestamp(2, Timestamp.valueOf(tomorrowStart));
                statement.setTimestamp(3, Timestamp.valueOf(sevenDaysStart));
                statement.setTimestamp(4, Timestamp.valueOf(tomorrowStart));
                statement.setTimestamp(5, Timestamp.valueOf(sevenDaysStart));
                statement.setTimestamp(6, Timestamp.valueOf(tomorrowStart));
                statement.setTimestamp(7, Timestamp.valueOf(sevenDaysStart));
                statement.setTimestamp(8, Timestamp.valueOf(tomorrowStart));
                statement.setTimestamp(9, Timestamp.valueOf(sevenDaysStart));
                statement.setTimestamp(10, Timestamp.valueOf(tomorrowStart));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return AuditActivitySnapshot.EMPTY;
                    return new AuditActivitySnapshot(result.getLong("changes_today"),
                            result.getLong("admin_last_seven"), result.getLong("direct_last_seven"),
                            result.getLong("authorization_last_seven"));
                }
            }
        });
    }

    @Override
    public List<AuditAdminEvent> exportRows(AuditAdminEventQuery query, int limit) throws DaoException {
        Filter filter = filter(query);
        return withConnection(connection -> {
            String sql = SELECT_ROWS + filter.sql() + " ORDER BY " + query.sort().orderBy() + " LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                List<Object> parameters = new ArrayList<>(filter.parameters());
                parameters.add(Math.max(1, limit));
                bind(statement, parameters);
                try (ResultSet result = statement.executeQuery()) {
                    List<AuditAdminEvent> rows = new ArrayList<>();
                    while (result.next()) rows.add(map(result));
                    return rows;
                }
            }
        });
    }

    @Override
    public void recordExport(AuditExportFormat format, AuditAdminEventQuery query, int rows, String fileName)
            throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    CALL write_audit_admin_event('ADMIN_EXPORT', NULL, ?,
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

    private List<AuditAdminEvent> rows(Connection connection, AuditAdminEventQuery query, Filter filter)
            throws SQLException, DaoException {
        String sql = SELECT_ROWS + filter.sql() + " ORDER BY " + query.sort().orderBy() + " LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            List<Object> parameters = new ArrayList<>(filter.parameters());
            parameters.add(query.pageSize());
            parameters.add(query.offset());
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<AuditAdminEvent> rows = new ArrayList<>();
                while (result.next()) rows.add(map(result));
                return rows;
            }
        }
    }

    private AuditAdminSummary summary(Connection connection, Filter filter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SUMMARY + filter.sql())) {
            bind(statement, filter.parameters());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return AuditAdminSummary.EMPTY;
                return new AuditAdminSummary(result.getLong("total_rows"), result.getLong("export_rows"),
                        result.getLong("delete_rows"), result.getLong("policy_rows"),
                        result.getLong("cleanup_rows"));
            }
        }
    }

    private List<AuditUserOption> users(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT e.actor_user_id,
                       COALESCE(MAX(NULLIF(e.actor_name, '')), MAX(u.user_name), '') AS actor_display
                FROM audit_admin_event e
                LEFT JOIN users u ON u.id = e.actor_user_id
                WHERE e.actor_user_id IS NOT NULL
                GROUP BY e.actor_user_id
                ORDER BY actor_display, e.actor_user_id
                """); ResultSet result = statement.executeQuery()) {
            List<AuditUserOption> users = new ArrayList<>();
            while (result.next()) {
                users.add(new AuditUserOption(result.getInt("actor_user_id"), result.getString("actor_display")));
            }
            return users;
        }
    }

    private List<String> eventTypes(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT event_type FROM audit_admin_event ORDER BY event_type");
             ResultSet result = statement.executeQuery()) {
            List<String> types = new ArrayList<>();
            while (result.next()) types.add(result.getString(1));
            return types;
        }
    }

    static Filter filter(AuditAdminEventQuery query) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        conditions.add("e.occurred_at >= ?");
        parameters.add(Timestamp.valueOf(query.from().atStartOfDay()));
        conditions.add("e.occurred_at < ?");
        parameters.add(Timestamp.valueOf(query.to().plusDays(1).atStartOfDay()));
        if (!query.search().isEmpty()) {
            String like = "%" + escapeLike(query.search()) + "%";
            conditions.add("""
                    (CAST(e.id AS CHAR) LIKE ? ESCAPE '!'
                     OR e.event_type LIKE ? ESCAPE '!'
                     OR e.actor_name LIKE ? ESCAPE '!'
                     OR u.user_name LIKE ? ESCAPE '!'
                     OR e.source LIKE ? ESCAPE '!'
                     OR e.workstation_name LIKE ? ESCAPE '!'
                     OR e.reason LIKE ? ESCAPE '!'
                     OR CAST(e.details AS CHAR) LIKE ? ESCAPE '!')""");
            for (int i = 0; i < 8; i++) parameters.add(like);
        }
        if (query.userId() != null) {
            conditions.add("e.actor_user_id = ?");
            parameters.add(query.userId());
        }
        if (!query.eventType().isEmpty()) {
            conditions.add("e.event_type = ?");
            parameters.add(query.eventType());
        }
        if (query.source().databaseValue() != null) {
            conditions.add("e.source = ?");
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
    public AuditAdminEvent map(ResultSet result) throws DaoException {
        try {
            return new AuditAdminEvent(result.getLong("id"), result.getString("event_type"),
                    nullableInteger(result, "actor_user_id"), result.getString("actor_display"),
                    result.getString("source"), result.getString("workstation_id"),
                    result.getString("workstation_name"), result.getTimestamp("occurred_at").toLocalDateTime(),
                    result.getString("reason"), result.getLong("affected_rows"), result.getString("details"));
        } catch (SQLException error) {
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
