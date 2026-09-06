package com.hamza.account.features.workstation;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** JDBC persistence for {@code workstation_session}. */
public final class JdbcWorkstationRepository extends AbstractDao<Object> {

    /**
     * {@code first_seen} is written only by the insert, so a machine keeps the day it
     * joined however many times it reports in afterwards.
     */
    private static final String HEARTBEAT = """
            INSERT INTO workstation_session
                (machine_id, machine_name, app_version, database_version, last_user_id, last_seen)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON DUPLICATE KEY UPDATE machine_name = VALUES(machine_name),
                                    app_version = VALUES(app_version),
                                    database_version = VALUES(database_version),
                                    last_user_id = VALUES(last_user_id),
                                    last_seen = NOW()
            """;

    private static final String LIST = """
            SELECT w.machine_id, w.machine_name, w.app_version, w.database_version,
                   u.user_name, w.first_seen, w.last_seen
            FROM workstation_session w
                     LEFT JOIN users u ON u.id = w.last_user_id
            ORDER BY w.last_seen DESC
            """;

    private static final String FORGET = "DELETE FROM workstation_session WHERE machine_id = ?";

    public void heartbeat(String machineId, String machineName, String appVersion,
                          String databaseVersion, Integer userId) throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(HEARTBEAT)) {
                statement.setString(1, machineId);
                statement.setString(2, machineName);
                statement.setString(3, appVersion);
                statement.setString(4, databaseVersion);
                if (userId == null) {
                    statement.setNull(5, Types.INTEGER);
                } else {
                    statement.setInt(5, userId);
                }
                statement.executeUpdate();
            }
            return null;
        });
    }

    public List<Workstation> list() throws DaoException {
        return withConnection(connection -> {
            List<Workstation> machines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(LIST);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    machines.add(new Workstation(
                            resultSet.getString("machine_id"),
                            resultSet.getString("machine_name"),
                            resultSet.getString("app_version"),
                            resultSet.getString("database_version"),
                            resultSet.getString("user_name"),
                            wallClock(resultSet, "first_seen"),
                            wallClock(resultSet, "last_seen"),
                            false,
                            false));
                }
            }
            return machines;
        });
    }

    public int forget(String machineId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(FORGET)) {
                statement.setString(1, machineId);
                return statement.executeUpdate();
            }
        });
    }

    /**
     * Reads a {@code DATETIME} as the wall clock it holds, with no time-zone conversion.
     *
     * <p>{@code getTimestamp} is what this used to call, and it was wrong in a way that
     * showed on the screen: the JDBC URL claimed {@code serverTimezone=UTC}, so the driver
     * read the stored value as a UTC instant and {@code toLocalDateTime()} rendered it in
     * the JVM's zone. The value is written by MySQL's own {@code NOW()}, which is the
     * server's local time - so a machine that reported in at 06:25 was listed as last seen
     * at 09:25, three hours in the future, on the one column a person reads to decide
     * whether a till is still alive.
     *
     * <p>That URL now says {@code connectionTimeZone=LOCAL} and the driver converts
     * nothing, so {@code getTimestamp} would answer correctly here too - this column was
     * the first of about twenty found to have the same fault, and the URL is where it was
     * finally fixed. {@code getObject(LocalDateTime.class)} stays because it is the idiom
     * that does not depend on the URL being right: it hands back the literal value in the
     * column, which is what a {@code DATETIME} is - a wall clock, not an instant.
     */
    private static java.time.LocalDateTime wallClock(ResultSet resultSet, String column) throws java.sql.SQLException {
        return resultSet.getObject(column, java.time.LocalDateTime.class);
    }
}
