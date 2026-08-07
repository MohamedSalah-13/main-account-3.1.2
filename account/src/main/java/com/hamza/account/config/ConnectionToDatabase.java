package com.hamza.account.config;

import com.hamza.controlsfx.database.DBConnection;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.HashMap;


@Log4j2
@Getter
public class ConnectionToDatabase {

    @Getter
    private final DatabaseProperties properties;
    private final DBConnection dbConnection;
    private final String dbName;
    private final String host;
    private final String username;
    private final String port;
    private final String pass;

    public ConnectionToDatabase() {
        try {
            this.properties = DatabaseProperties.getInstance();
            File FILE_DATABASE_XML = new File("config.xml");
            var configMap = loadConfig(FILE_DATABASE_XML);

            host = configMap.get(CryptoDatabaseConfig.HOST);
            username = configMap.get(CryptoDatabaseConfig.USERNAME);
            pass = configMap.get(CryptoDatabaseConfig.PASSWORD);
            port = configMap.get(CryptoDatabaseConfig.PORT);
            dbName = configMap.get(CryptoDatabaseConfig.DBNAME);

            DataSourceProvider.initialize(host, port, dbName, username, pass);

            dbConnection = new DBConnection(host, port, dbName, username, pass);
        } catch (IllegalStateException e) {
            log.error("Configuration error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to initialize database connection", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Reads the encrypted database settings, keeping a config.xml problem
     * distinguishable from a failure to reach the database itself.
     */
    private static HashMap<String, String> loadConfig(File configFile) throws Exception {
        if (!configFile.isFile()) {
            throw new IllegalStateException("config.xml not found at " + configFile.getAbsolutePath()
                    + ". Copy config.xml.example and fill it in with CryptoDatabaseConfig encrypt.");
        }

        CryptoDatabaseConfig encryptor = new CryptoDatabaseConfig(CryptoDatabaseConfig.resolveConfigKey());
        try {
            return encryptor.loadAndDecryptConfig(configFile.getAbsolutePath());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt " + configFile.getAbsolutePath()
                    + " with the key from " + CryptoDatabaseConfig.describeConfigKeySource()
                    + ". The file was encrypted with a different key, or its values are not"
                    + " the Base64 ciphertext the loader expects.", e);
        }
    }
}
