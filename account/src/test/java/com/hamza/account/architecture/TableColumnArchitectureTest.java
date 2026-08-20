package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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
 * once nothing referenced them. That deletion is itself the strongest form of
 * this guard: {@code @ColumnData} is no longer a type that exists, so writing it
 * anywhere is a compile error, not a lint failure to catch after the fact.
 * <p>
 * What is left to guard is regression by reintroduction - someone adding either
 * class back, under the same name, and the reflective pattern creeping in again.
 */
class TableColumnArchitectureTest {

    private static final Path CONTROLSFX_TABLE =
            Path.of("..", "controlsfx", "src", "main", "java", "com", "hamza", "controlsfx", "table");

    private static final String[] DELETED_CLASSES = {"ColumnData.java", "TableColumnAnnotation.java"};

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
}
