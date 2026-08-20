package com.hamza.controlsfx.table;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sibling of {@code account}'s {@code architecture.TableColumnArchitectureTest} -
 * see that class for why {@code PropertyValueFactory} matters (rule ق-ل1 in
 * {@code docs/new-code-rules.md}). This half tracks the 4 call sites left in
 * {@code controlsfx} itself, in generic table helpers rather than one screen's
 * columns: {@code ColumnSetting}, {@code Table_Setting}, {@code ButtonColumn},
 * {@code Button_Toggle_Table}.
 */
class TableColumnArchitectureTest {

    /** Call sites when this rule was written. Only ever lower this. */
    private static final int DIRECT_PROPERTY_VALUE_FACTORY_BASELINE = 4;

    private static final Pattern DIRECT_PROPERTY_VALUE_FACTORY =
            Pattern.compile("new\\s+PropertyValueFactory\\s*<>\\s*\\(");

    @Test
    void theDirectPropertyValueFactoryCountOnlyGoesDown() throws IOException {
        int actual = 0;
        Path source = Path.of("src", "main", "java");
        try (var files = Files.walk(source)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = DIRECT_PROPERTY_VALUE_FACTORY.matcher(read(path));
                while (matcher.find()) {
                    actual++;
                }
            }
        }
        assertTrue(actual <= DIRECT_PROPERTY_VALUE_FACTORY_BASELINE,
                "new PropertyValueFactory<>(\"field\") resolves the field by name at run time - a "
                        + "rename yields a silently empty column (rule ق-ل1). Baseline "
                        + DIRECT_PROPERTY_VALUE_FACTORY_BASELINE + ", found " + actual
                        + ". If you migrated some, lower DIRECT_PROPERTY_VALUE_FACTORY_BASELINE to " + actual + ".");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
