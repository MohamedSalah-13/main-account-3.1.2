package com.hamza.controlsfx.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.log4j.Log4j2;

import java.util.Properties;

@Log4j2
public final class DataSourceProvider {

    private static volatile HikariDataSource dataSource;
    private static final Object LOCK = new Object();

    private DataSourceProvider() {
    }

    public static void initialize(String host, String port, String dbName, String username, String password) {
        if (dataSource != null) {
            return;
        }

        synchronized (LOCK) {
            if (dataSource != null) {
                return;
            }

            try {
                HikariConfig config = new HikariConfig();
                String jdbcUrl = String.format(
                    "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&connectTimeout=10000&socketTimeout=15000&tcpKeepAlive=true",
                    host, port, dbName
                );

                config.setJdbcUrl(jdbcUrl);
                config.setUsername(username);
                config.setPassword(password);
                config.setPoolName("main-account-db-pool");

                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setConnectionTimeout(10_000);
                config.setIdleTimeout(60_000);
                config.setMaxLifetime(30 * 60_000);
                config.setValidationTimeout(5_000);

                Properties dsProps = new Properties();
                dsProps.setProperty("cachePrepStmts", "true");
                dsProps.setProperty("prepStmtCacheSize", "250");
                dsProps.setProperty("prepStmtCacheSqlLimit", "2048");
                config.setDataSourceProperties(dsProps);

                dataSource = new HikariDataSource(config);
                log.info("Hikari datasource initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize datasource", e);
                throw new RuntimeException("Database datasource initialization failed", e);
            }
        }
    }

    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not initialized. Call initialize(...) first.");
        }
        return dataSource;
    }

    public static void shutdown() {
        HikariDataSource ds = dataSource;
        if (ds != null) {
            ds.close();
            dataSource = null;
            log.info("Hikari datasource closed");
        }
    }
}
