package com.hamza.account.config;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes {@code app_setting}. One key, one string, no interpretation. */
public class SharedSettingsStore extends AbstractDao<Object> {

    private static final String READ_ALL = "SELECT setting_key, setting_value FROM app_setting";

    private static final String WRITE = """
            INSERT INTO app_setting (setting_key, setting_value, updated_by)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value),
                                    updated_by = VALUES(updated_by)
            """;

    /**
     * Written only when the key has no row yet - this is how a shop's first upgraded
     * machine publishes the values it already had without a second machine, started an
     * hour later with its own defaults, overwriting them.
     */
    private static final String WRITE_IF_ABSENT = """
            INSERT IGNORE INTO app_setting (setting_key, setting_value, updated_by)
            VALUES (?, ?, ?)
            """;

    public Map<String, String> readAll() throws DaoException {
        return withConnection(connection -> {
            Map<String, String> values = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(READ_ALL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.put(resultSet.getString("setting_key"), resultSet.getString("setting_value"));
                }
            }
            return values;
        });
    }

    public void write(String key, String value, Integer userId) throws DaoException {
        upsert(WRITE, key, value, userId);
    }

    public void writeIfAbsent(String key, String value, Integer userId) throws DaoException {
        upsert(WRITE_IF_ABSENT, key, value, userId);
    }

    private void upsert(String sql, String key, String value, Integer userId) throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, key);
                statement.setString(2, value);
                if (userId == null) {
                    statement.setNull(3, Types.INTEGER);
                } else {
                    statement.setInt(3, userId);
                }
                statement.executeUpdate();
            }
            return null;
        });
    }
}
