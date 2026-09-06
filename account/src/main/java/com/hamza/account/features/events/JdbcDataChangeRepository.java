package com.hamza.account.features.events;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/** JDBC access to {@code data_change}: one row per topic, overwritten. */
public final class JdbcDataChangeRepository extends AbstractDao<Object> {

    /**
     * {@code NOW(3)} is written explicitly rather than left to {@code ON UPDATE
     * CURRENT_TIMESTAMP}: that clause only fires when some column's value actually
     * changes, and the same machine announcing the same topic twice changes nothing -
     * so the second announcement would carry the first one's time and nobody would act
     * on it.
     */
    private static final String ANNOUNCE = """
            INSERT INTO data_change (topic, changed_at, changed_by)
            VALUES (?, NOW(3), ?)
            ON DUPLICATE KEY UPDATE changed_at = NOW(3), changed_by = VALUES(changed_by)
            """;

    private static final String READ_ALL = "SELECT topic, changed_at, changed_by FROM data_change";

    /** What each topic looked like at one moment: when it changed, and who changed it. */
    public record Change(Timestamp changedAt, String changedBy) {
    }

    public void announce(String topic, String machineId) throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ANNOUNCE)) {
                statement.setString(1, topic);
                statement.setString(2, machineId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public Map<String, Change> readAll() throws DaoException {
        return withConnection(connection -> {
            Map<String, Change> changes = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(READ_ALL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    changes.put(resultSet.getString("topic"),
                            new Change(resultSet.getTimestamp("changed_at"),
                                    resultSet.getString("changed_by")));
                }
            }
            return changes;
        });
    }
}
