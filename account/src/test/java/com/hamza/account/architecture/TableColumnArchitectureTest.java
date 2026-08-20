package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards rule ق-ل1 of {@code docs/new-code-rules.md}: table columns are built in
 * code, not read by reflection off a field name.
 * <p>
 * {@code TableColumnAnnotation} and {@code ColumnData} used to be how every
 * column got built - through {@code PropertyValueFactory}, resolving a field by
 * <i>string</i> at run time, so a renamed field produced a silently empty
 * column with no compile error and no exception. §12 of
 * {@code docs/erp-roadmap.md} replaced every one of the 18 direct call sites and
 * the 3 generic seams (§12.2, §12.3) with explicit column lists built through
 * {@code com.hamza.controlsfx.table.Columns}, then deleted both classes (§12.4)
 * once nothing referenced them.
 * <p>
 * That deletion closes only the wrapper, not the underlying risk: JavaFX's own
 * {@code PropertyValueFactory} is still called directly in 33 places here (and 4
 * more in {@code controlsfx} - see the sibling test there), all outside the
 * screens §12 touched. {@link #theDirectPropertyValueFactoryCountOnlyGoesDown()}
 * is what keeps that number from growing while those call sites wait for their
 * own one-touch migration to {@code Columns}.
 */
class TableColumnArchitectureTest {

    private static final Path CONTROLSFX_TABLE =
            Path.of("..", "controlsfx", "src", "main", "java", "com", "hamza", "controlsfx", "table");

    private static final String[] DELETED_CLASSES = {"ColumnData.java", "TableColumnAnnotation.java"};

    /** Call sites when this rule was tightened after §12.4. Only ever lower this. */
    private static final int DIRECT_PROPERTY_VALUE_FACTORY_BASELINE = 33;

    private static final Pattern DIRECT_PROPERTY_VALUE_FACTORY =
            Pattern.compile("new\\s+PropertyValueFactory\\s*<>\\s*\\(");

    @Test
    void theDeletedClassesStayDeleted() {
        for (String name : DELETED_CLASSES) {
            Path path = CONTROLSFX_TABLE.resolve(name);
            assertFalse(java.nio.file.Files.exists(path),
                    name + " was deleted in §12.4 because nothing referenced it any more "
                            + "(rule ق-ل1) - it should not come back under the same name.");
        }
    }

    @Test
    void noSourceFileMentionsTheDeletedReflectionPath() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.MAIN_JAVA)) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            if (source.contains("TableColumnAnnotation") || source.contains("@ColumnData")) {
                offenders.add(file);
            }
        }
        assertTrue(offenders.isEmpty(),
                "Neither class exists any more, so any live reference here would already fail to "
                        + "compile - this only catches a reintroduction under the same name: " + offenders);
    }

    @Test
    void theDirectPropertyValueFactoryCountOnlyGoesDown() {
        int actual = 0;
        for (String file : SourceTree.javaFiles(SourceTree.MAIN_JAVA)) {
            Matcher matcher = DIRECT_PROPERTY_VALUE_FACTORY.matcher(SourceTree.withoutComments(SourceTree.readJava(file)));
            while (matcher.find()) {
                actual++;
            }
        }
        assertTrue(actual <= DIRECT_PROPERTY_VALUE_FACTORY_BASELINE,
                "new PropertyValueFactory<>(\"field\") resolves the field by name at run time - a "
                        + "rename yields a silently empty column, the exact risk §12 removed "
                        + "TableColumnAnnotation for (rule ق-ل1). Build the column with "
                        + "com.hamza.controlsfx.table.Columns instead. Baseline "
                        + DIRECT_PROPERTY_VALUE_FACTORY_BASELINE + ", found " + actual
                        + ". If you migrated some, lower DIRECT_PROPERTY_VALUE_FACTORY_BASELINE to " + actual + ".");
    }
}
