package com.hamza.account.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigFilesTest {

    @TempDir
    Path temporary;

    @Test
    void overrideDirectoryWinsOverProgramData() {
        var location = DatabaseConfigFiles.preferred(
                temporary.resolve("chosen").toString(), temporary.resolve("program-data").toString(),
                temporary.resolve("home").toString());

        assertEquals(temporary.resolve("chosen").toAbsolutePath(), location.directory());
    }

    @Test
    void programDataIsTheDefaultOnWindows() {
        var location = DatabaseConfigFiles.preferred(
                null, temporary.resolve("program-data").toString(), temporary.resolve("home").toString());

        assertEquals(temporary.resolve("program-data/AccountK").toAbsolutePath(), location.directory());
    }

    @Test
    void existingProgramDataConfigWins() throws Exception {
        var preferred = DatabaseConfigFiles.at(temporary.resolve("preferred"));
        Path working = temporary.resolve("working");
        Files.createDirectories(preferred.directory());
        Files.createDirectories(working);
        Files.writeString(preferred.configFile(), "preferred");
        Files.writeString(working.resolve("config.xml"), "legacy");

        var selected = DatabaseConfigFiles.locateForRead(preferred, working);

        assertEquals(preferred.directory(), selected.directory());
        assertFalse(selected.legacy());
    }

    @Test
    void workingDirectoryRemainsACompatibilityFallback() throws Exception {
        var preferred = DatabaseConfigFiles.at(temporary.resolve("preferred"));
        Path working = temporary.resolve("working");
        Files.createDirectories(working);
        Files.writeString(working.resolve("config.xml"), "legacy");

        var selected = DatabaseConfigFiles.locateForRead(preferred, working);

        assertEquals(working.toAbsolutePath(), selected.directory());
        assertTrue(selected.legacy());
    }
}
