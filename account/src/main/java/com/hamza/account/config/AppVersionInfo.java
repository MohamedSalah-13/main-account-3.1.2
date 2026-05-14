package com.hamza.account.config;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Getter
@Log4j2
public class AppVersionInfo {

    private static final String VERSION_FILE = "/version.properties";

    private final String appName;
    private final String appVersion;
    private final String buildDate;
    private final String requiredDatabaseVersion;

    public AppVersionInfo() {
        Properties properties = new Properties();

        try (InputStream inputStream = AppVersionInfo.class.getResourceAsStream(VERSION_FILE)) {
            if (inputStream == null) {
                throw new IllegalStateException("version.properties file not found");
            }

            properties.load(inputStream);

            this.appName = properties.getProperty("app.name", "Main Account");
            this.appVersion = properties.getProperty("app.version", "unknown");
            this.buildDate = properties.getProperty("build.date", "unknown");
            this.requiredDatabaseVersion = properties.getProperty("db.required.version", "0.0.0");

        } catch (IOException e) {
            log.error("Failed to read version.properties", e);
            throw new RuntimeException("Failed to read application version info", e);
        }
    }
}
