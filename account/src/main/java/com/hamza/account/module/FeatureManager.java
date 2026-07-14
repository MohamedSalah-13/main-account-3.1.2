package com.hamza.account.module;

import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.util.Properties;

@Log4j2
public final class FeatureManager {

    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream inputStream = FeatureManager.class.getResourceAsStream("/features.properties")) {
            if (inputStream != null) {
                PROPERTIES.load(inputStream);
            } else {
                log.warn("features.properties not found. All features will be disabled by default.");
            }
        } catch (Exception e) {
            log.error("Error loading features.properties", e);
        }
    }

    private FeatureManager() {
    }

    public static boolean isEnabled(AppFeature feature) {
        return Boolean.parseBoolean(
                PROPERTIES.getProperty("feature." + feature.name(), "false")
        );
    }

    public static boolean isDisabled(AppFeature feature) {
        return !isEnabled(feature);
    }
}