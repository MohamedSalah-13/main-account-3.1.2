package com.hamza.account.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the bundled {@code mysqldump} is looked for. It used to be the working directory alone,
 * so a program started from anywhere but its own folder failed every backup it was asked for.
 */
class MysqlToolsTest {

    @Test
    @DisplayName("a packaged launcher finds the server beside its own executable, wherever it was started from")
    void besideTheLauncher(@TempDir Path install) throws Exception {
        Files.createDirectories(install.resolve("mysql/bin"));
        Path launcher = install.resolve("AccountK.exe");

        assertEquals(install.resolve("mysql/bin/mysqldump").toString(),
                MysqlTools.bundled("mysqldump", launcher.toString()));
    }

    @Test
    @DisplayName("from source there is no launcher, and the working directory is still the answer")
    void fromSource() {
        String relative = new File("mysql/bin/mysqldump").getPath();
        assertEquals(relative, MysqlTools.bundled("mysqldump", null));
        assertEquals(relative, MysqlTools.bundled("mysqldump", " "));
    }

    @Test
    @DisplayName("a launcher with no server beside it - a till - falls back rather than naming a folder that is not there")
    void aTillHasNoServer(@TempDir Path install) {
        assertEquals(new File("mysql/bin/mysqldump").getPath(),
                MysqlTools.bundled("mysqldump", install.resolve("AccountK.exe").toString()));
    }
}
