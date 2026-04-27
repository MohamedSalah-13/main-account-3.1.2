package com.hamza.account.config;

import com.hamza.controlsfx.database.DBConnection;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.File;


@Log4j2
@Getter
public class ConnectionToDatabase {

    @Getter
    private DatabaseProperties properties;
    private DBConnection dbConnection;
    private String dbName;
    private String host;
    private String username;
    private String port;
    private String pass;

    public ConnectionToDatabase() {
        try {
            this.properties = DatabaseProperties.getInstance();
            File FILE_DATABASE_XML = new File("config.xml");
            CryptoDatabaseConfig encryptor = new CryptoDatabaseConfig("nZdjCubzMZs+/RU1XDr/7g==");
            var configMap = encryptor.loadAndDecryptConfig(FILE_DATABASE_XML.getAbsolutePath());

            host = configMap.get(CryptoDatabaseConfig.HOST);
            username = configMap.get(CryptoDatabaseConfig.USERNAME);
            pass = configMap.get(CryptoDatabaseConfig.PASSWORD);
            port = configMap.get(CryptoDatabaseConfig.PORT);
            dbName = configMap.get(CryptoDatabaseConfig.DBNAME);

            DataSourceProvider.initialize(host, port, dbName, username, pass);

            dbConnection = new DBConnection(host, port, dbName, username, pass);
        } catch (IllegalStateException e) {
            log.error("Configuration error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to initialize database connection", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
