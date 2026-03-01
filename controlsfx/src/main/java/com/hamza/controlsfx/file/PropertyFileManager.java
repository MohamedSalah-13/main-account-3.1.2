package com.hamza.controlsfx.file;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

@Log4j2
public class PropertyFileManager {

    private static final String DEFAULT_RETURN_VALUE = "false";
    private final Properties properties;
    private final File file;

    public PropertyFileManager(@NotNull Properties properties, @NotNull File file) {
        this.properties = properties;
        this.file = file;
    }

    public void saveProperty(@NotNull String key, @NotNull String value) {
        try {
            properties.setProperty(key, value);
            storeProperties();
        } catch (IOException e) {
            log.error(e);
        }
    }

    public String getProperty(@NotNull String key) {
        try {
            loadProperties();
            return properties.getProperty(key, DEFAULT_RETURN_VALUE);
        } catch (IOException e) {
            log.error(e);
        }
        return DEFAULT_RETURN_VALUE;
    }

    public void removeProperty(@NotNull String key) {
        try {
            loadProperties();
            properties.remove(key);
            storeProperties();
        } catch (IOException e) {
            log.error(e);
        }
    }

    private void loadProperties() throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
        }
    }

    private void storeProperties() throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, null);
        }
    }
}