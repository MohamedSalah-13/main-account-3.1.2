package com.hamza.account.service.version;

import com.hamza.account.config.AppVersionInfo;
import com.hamza.account.config.ConnectionToDatabase;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

@Log4j2
public class SystemInfoService {

    private final ConnectionToDatabase database;
    private final AppVersionInfo appVersionInfo;

    public SystemInfoService() {
        this.database = new ConnectionToDatabase();
        this.appVersionInfo = new AppVersionInfo();
    }

    public SystemInfo getSystemInfo() {
        createSystemTablesIfNotExists();

        String sql = """
                SELECT
                    client_code,
                    client_name,
                    app_version,
                    database_version,
                    install_date,
                    last_update,
                    database_name,
                    server_ip,
                    license_key,
                    notes
                FROM system_info
                WHERE id = 1
                """;

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            if (resultSet.next()) {
                return SystemInfo.builder()
                        .clientCode(resultSet.getString("client_code"))
                        .clientName(resultSet.getString("client_name"))
                        .appVersion(appVersionInfo.getAppVersion())
                        .databaseVersion(resultSet.getString("database_version"))
                        .installDate(resultSet.getTimestamp("install_date") == null ? null : resultSet.getTimestamp("install_date").toLocalDateTime())
                        .lastUpdate(resultSet.getTimestamp("last_update") == null ? null : resultSet.getTimestamp("last_update").toLocalDateTime())
                        .databaseName(database.getDbName())
                        .serverIp(database.getHost())
                        .licenseKey(resultSet.getString("license_key"))
                        .notes(resultSet.getString("notes"))
                        .build();
            }

            insertDefaultSystemInfo();

            return getSystemInfo();

        } catch (Exception e) {
            log.error("Failed to read system info", e);
            throw new RuntimeException("Failed to read system info", e);
        }
    }

    public String getCurrentDatabaseVersion() {
        createSystemTablesIfNotExists();

        String sql = "SELECT database_version FROM system_info WHERE id = 1";

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            if (resultSet.next()) {
                String version = resultSet.getString("database_version");
                return version == null || version.isBlank() ? "0.0.0" : version;
            }

            insertDefaultSystemInfo();
            return "0.0.0";

        } catch (Exception e) {
            log.error("Failed to get current database version", e);
            throw new RuntimeException("Failed to get current database version", e);
        }
    }

    public void updateDatabaseVersion(String version) {
        String sql = """
                UPDATE system_info
                SET database_version = '%s',
                    app_version = '%s',
                    database_name = '%s',
                    server_ip = '%s',
                    last_update = NOW()
                WHERE id = 1
                """.formatted(
                escape(version),
                escape(appVersionInfo.getAppVersion()),
                escape(database.getDbName()),
                escape(database.getHost())
        );

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(sql);
        } catch (Exception e) {
            log.error("Failed to update database version", e);
            throw new RuntimeException("Failed to update database version", e);
        }
    }

    public void createSystemTablesIfNotExists() {
        String createSystemInfo = """
                CREATE TABLE IF NOT EXISTS system_info (
                    id INT PRIMARY KEY,
                    client_code VARCHAR(50),
                    client_name VARCHAR(255),
                    app_version VARCHAR(50),
                    database_version VARCHAR(50),
                    install_date DATETIME,
                    last_update DATETIME,
                    database_name VARCHAR(100),
                    server_ip VARCHAR(100),
                    license_key VARCHAR(255),
                    notes TEXT
                )
                """;

        String createMigrations = """
                CREATE TABLE IF NOT EXISTS database_migrations (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    version VARCHAR(50) NOT NULL UNIQUE,
                    description VARCHAR(255),
                    executed_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.execute(createSystemInfo);
            statement.execute(createMigrations);
            insertDefaultSystemInfoIfMissing(statement);
        } catch (Exception e) {
            log.error("Failed to create system tables", e);
            throw new RuntimeException("Failed to create system tables", e);
        }
    }

    private void insertDefaultSystemInfo() {
        try (
                Connection connection = getConnection();
                Statement statement = connection.createStatement()
        ) {
            insertDefaultSystemInfoIfMissing(statement);
        } catch (Exception e) {
            log.error("Failed to insert default system info", e);
            throw new RuntimeException("Failed to insert default system info", e);
        }
    }

    private void insertDefaultSystemInfoIfMissing(Statement statement) throws Exception {
        String sql = """
                INSERT INTO system_info (
                    id,
                    client_code,
                    client_name,
                    app_version,
                    database_version,
                    install_date,
                    last_update,
                    database_name,
                    server_ip,
                    notes
                )
                SELECT
                    1,
                    'CLIENT-001',
                    'Default Client',
                    '%s',
                    '0.0.0',
                    NOW(),
                    NOW(),
                    '%s',
                    '%s',
                    'Created automatically'
                WHERE NOT EXISTS (
                    SELECT 1 FROM system_info WHERE id = 1
                )
                """.formatted(
                escape(appVersionInfo.getAppVersion()),
                escape(database.getDbName()),
                escape(database.getHost())
        );

        statement.executeUpdate(sql);
    }

    private Connection getConnection() throws Exception {
        String url = "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
                .formatted(database.getHost(), database.getPort(), database.getDbName());

        return DriverManager.getConnection(url, database.getUsername(), database.getPass());
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
