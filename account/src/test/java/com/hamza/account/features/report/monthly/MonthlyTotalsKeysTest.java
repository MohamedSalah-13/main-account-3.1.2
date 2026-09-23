package com.hamza.account.features.report.monthly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The screen names a side and a measure through a variable, where {@code MessageKeyArchitectureTest}
 * cannot see the keys, so they are checked against the three bundles here.
 */
class MonthlyTotalsKeysTest {

    @Test
    @DisplayName("every side's and every measure's label is in the three bundles")
    void everyKeyIsTranslated() throws Exception {
        List<String> keys = new ArrayList<>();
        for (MonthlySide side : MonthlySide.values()) {
            keys.add(side.labelKey());
        }
        for (MonthlyMeasure measure : MonthlyMeasure.values()) {
            keys.add(measure.labelKey());
        }
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var in = Files.newBufferedReader(Path.of("..", "controlsfx", "src", "main", "resources", "i18n",
                    bundle))) {
                properties.load(in);
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
        }
    }
}
