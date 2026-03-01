package com.hamza.account.config;

import com.hamza.controlsfx.database.DBConnection;
import com.hamza.controlsfx.file.crypto.CryptoDatabaseConfig;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.File;


@Log4j2
@Getter
public class ConnectionToDatabase {

    private DBConnection dbConnection;
    private String dbName;
    private String host;
    private String username;
    private String port;
    private String pass;

    public ConnectionToDatabase() {
        try {
            File FILE_DATABASE_XML = new File("config.xml");
            CryptoDatabaseConfig encryptor = new CryptoDatabaseConfig("nZdjCubzMZs+/RU1XDr/7g==");
            var stringStringHashMap1 = encryptor.loadAndDecryptConfig(FILE_DATABASE_XML.getAbsolutePath());
            host = stringStringHashMap1.get(CryptoDatabaseConfig.HOST);
            username = stringStringHashMap1.get(CryptoDatabaseConfig.USERNAME);
            pass = stringStringHashMap1.get(CryptoDatabaseConfig.PASSWORD);
            port = stringStringHashMap1.get(CryptoDatabaseConfig.PORT);
            dbName = stringStringHashMap1.get(CryptoDatabaseConfig.DBNAME);
            dbConnection = new DBConnection(host, port, dbName, username, pass);
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
        }
    }
}
