package com.hamza.account.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DatabaseProperties {

    private static final String DEFAULT_PROPERTIES_FILE = "/application.properties";
    private static final String TEST_PROPERTIES_FILE = "/application-test.properties";

    private final Properties properties;
    private static DatabaseProperties instance;

    /**
     * Private constructor to load properties file
     * @param propertiesFile the properties file to load
     */
    private DatabaseProperties(String propertiesFile) {
        properties = new Properties();
        loadProperties(propertiesFile);
    }

    /**
     * Get instance for production environment
     */
    public static DatabaseProperties getInstance() {
        if (instance == null) {
            instance = new DatabaseProperties(DEFAULT_PROPERTIES_FILE);
        }
        return instance;
    }

    /**
     * Get instance for test environment
     */
    public static DatabaseProperties getTestInstance() {
        return new DatabaseProperties(TEST_PROPERTIES_FILE);
    }

    /**
     * Load properties from file
     */
    private void loadProperties(String fileName) {
        try (InputStream input = getClass().getResourceAsStream(fileName)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + fileName);
            }
            properties.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Error loading properties file: " + fileName, ex);
        }
    }

    /**
     * Get property value
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get property value with default
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    // Getters for database properties
    public String getDbDriver() {
        return getProperty("db.driver");
    }

    public String getDbUrl() {
        return getProperty("db.url");
    }

    public String getDbUsername() {
        return getProperty("db.username");
    }

    public String getDbPassword() {
        return getProperty("db.password");
    }

    public String getDbHost() {
        return getProperty("db.host", "localhost");
    }

    public String getDbPort() {
        return getProperty("db.port", "3306");
    }

    public String getDbName() {
        return getProperty("db.name");
    }

    public int getPoolMinSize() {
        return Integer.parseInt(getProperty("db.pool.minSize", "5"));
    }

    public int getPoolMaxSize() {
        return Integer.parseInt(getProperty("db.pool.maxSize", "20"));
    }

    public int getPoolTimeout() {
        return Integer.parseInt(getProperty("db.pool.timeout", "30000"));
    }

    public String getMysqlDumpPath() {
        return getProperty("mysql.dump.path", "mysqldump");
    }

    public String getMysqlCommandPath() {
        return getProperty("mysql.command.path", "mysql");
    }

    public boolean isUsePathVariable() {
        return Boolean.parseBoolean(getProperty("mysql.use.path.variable", "true"));
    }
}
