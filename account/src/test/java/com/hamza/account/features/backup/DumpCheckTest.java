package com.hamza.account.features.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DumpCheckTest {

    /** The shape mysqldump writes: header, a table, its rows, the closing comment. */
    private static final String WHOLE = """
            -- MySQL dump 10.13  Distrib 8.0.31, for Win64 (x86_64)
            --
            -- Host: 127.0.0.1    Database: account_system_db
            DROP TABLE IF EXISTS `items`;
            CREATE TABLE `items` (
              `id` int NOT NULL AUTO_INCREMENT,
              `nameItem` varchar(100) NOT NULL
            ) ENGINE=InnoDB;
            INSERT INTO `items` VALUES (1,'بيبسي'),(2,'شيبسي');
            CREATE TABLE `units` (
              `unit_id` int NOT NULL
            ) ENGINE=InnoDB;
            /*!40101 SET SQL_MODE=@OLD_SQL_MODE */;

            -- Dump completed on 2026-09-14 14:47:53
            """;

    private static DumpCheck check(String text) throws Exception {
        return DumpCheck.of(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("a whole dump is usable and its tables are counted")
    void wholeDump() throws Exception {
        DumpCheck check = check(WHOLE);

        assertTrue(check.completed());
        assertEquals(2, check.tables());
        assertTrue(check.usable());
    }

    @Test
    @DisplayName("a dump cut off in its data still looks like SQL, and is not usable")
    void truncatedInTheData() throws Exception {
        String cut = WHOLE.substring(0, WHOLE.indexOf("(2,'"));

        DumpCheck check = check(cut);

        assertFalse(check.completed());
        assertFalse(check.usable());
    }

    @Test
    @DisplayName("Windows line endings, trailing blank lines and no final newline are all the same dump")
    void lineEndings() throws Exception {
        assertTrue(check(WHOLE.replace("\n", "\r\n")).usable());
        assertTrue(check(WHOLE + "\n\n  \n").usable());
        assertTrue(check(WHOLE.stripTrailing()).usable());
    }

    @Test
    @DisplayName("the closing comment has to be the last thing, not just somewhere")
    void completedMustBeLast() throws Exception {
        assertFalse(check(WHOLE + "INSERT INTO `items` VALUES (3,'x');\n").completed());
    }

    @Test
    @DisplayName("a completed dump of nothing is not a backup")
    void noTables() throws Exception {
        DumpCheck check = check("-- MySQL dump\n-- Dump completed on 2026-09-14\n");

        assertTrue(check.completed());
        assertFalse(check.usable());
    }

    @Test
    @DisplayName("a very long line does not hide the lines after it")
    void longLines() throws Exception {
        String longInsert = "INSERT INTO `items` VALUES " + "(1,'x'),".repeat(20_000) + "(2,'y');\n";
        String text = WHOLE.replace("INSERT INTO `items` VALUES (1,'بيبسي'),(2,'شيبسي');\n", longInsert);

        assertTrue(check(text).usable());
    }

    @Test
    void emptyIsNothing() throws Exception {
        assertFalse(check("").usable());
    }
}
