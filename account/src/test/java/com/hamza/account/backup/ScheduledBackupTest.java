package com.hamza.account.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retention deletes files, so a mistake here loses backups. These cover
 * {@link ScheduledBackup#pruneOldBackups} only - the scheduling and the
 * Preferences-backed settings are left alone deliberately, since exercising them
 * would read and write the real user preferences of whoever runs the tests.
 */
class ScheduledBackupTest {

    /**
     * Creates {@code count} backups, oldest first. Modification times are set
     * explicitly and spaced a minute apart: the files are written within the same
     * millisecond otherwise, and the order under test is the order of these
     * timestamps.
     */
    private static List<File> givenBackups(Path dir, int count) throws Exception {
        long base = System.currentTimeMillis() - (count + 1L) * 60_000;
        return IntStream.range(0, count).mapToObj(i -> {
            try {
                File file = dir.resolve("backup_%03d.enc".formatted(i)).toFile();
                Files.writeString(file.toPath(), "backup " + i);
                assertTrue(file.setLastModified(base + i * 60_000L),
                        "could not set the modification time, the test cannot order files");
                return file;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private static Set<String> namesIn(Path dir) {
        File[] files = dir.toFile().listFiles();
        return files == null ? Set.of()
                : Arrays.stream(files).map(File::getName).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("keeps the newest and deletes the surplus")
    void keepsTheNewest(@TempDir Path dir) throws Exception {
        List<File> created = givenBackups(dir, 35);

        ScheduledBackup.pruneOldBackups(dir.toFile(), 30);

        Set<String> remaining = namesIn(dir);
        assertEquals(30, remaining.size());
        // backup_005 .. backup_034 are the newest thirty.
        created.subList(5, 35).forEach(f ->
                assertTrue(remaining.contains(f.getName()), f.getName() + " should have been kept"));
        created.subList(0, 5).forEach(f ->
                assertFalse(remaining.contains(f.getName()), f.getName() + " should have been deleted"));
    }

    @Test
    void keepsEverythingWhenUnderTheLimit(@TempDir Path dir) throws Exception {
        givenBackups(dir, 5);

        ScheduledBackup.pruneOldBackups(dir.toFile(), 30);

        assertEquals(5, namesIn(dir).size());
    }

    @Test
    @DisplayName("exactly the limit is not over it")
    void keepsExactlyTheLimit(@TempDir Path dir) throws Exception {
        givenBackups(dir, 30);

        ScheduledBackup.pruneOldBackups(dir.toFile(), 30);

        assertEquals(30, namesIn(dir).size());
    }

    @Test
    @DisplayName("only .enc files are considered; anything else in the folder is left alone")
    void ignoresFilesThatAreNotBackups(@TempDir Path dir) throws Exception {
        givenBackups(dir, 4);
        Files.writeString(dir.resolve("notes.txt"), "keep me");
        Files.writeString(dir.resolve("dump.sql"), "keep me too");
        Files.createDirectory(dir.resolve("subfolder"));

        ScheduledBackup.pruneOldBackups(dir.toFile(), 1);

        Set<String> remaining = namesIn(dir);
        assertTrue(remaining.contains("notes.txt"));
        assertTrue(remaining.contains("dump.sql"));
        assertTrue(remaining.contains("subfolder"));
        assertEquals(1, remaining.stream().filter(n -> n.endsWith(".enc")).count());
        assertTrue(remaining.contains("backup_003.enc"), "the newest backup should be the one kept");
    }

    @Test
    void matchesTheSuffixCaseInsensitively(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("old.ENC"), "x");
        Files.writeString(dir.resolve("new.enc"), "x");
        assertTrue(dir.resolve("old.ENC").toFile().setLastModified(System.currentTimeMillis() - 600_000));

        ScheduledBackup.pruneOldBackups(dir.toFile(), 1);

        assertEquals(Set.of("new.enc"), namesIn(dir));
    }

    @Test
    void toleratesAMissingDirectory(@TempDir Path dir) {
        File missing = dir.resolve("does-not-exist").toFile();

        assertDoesNotThrow(() -> ScheduledBackup.pruneOldBackups(missing, 30));
    }

    @Test
    void toleratesAFileWhereADirectoryWasExpected(@TempDir Path dir) throws Exception {
        Path notADirectory = dir.resolve("plain.txt");
        Files.writeString(notADirectory, "x");

        assertDoesNotThrow(() -> ScheduledBackup.pruneOldBackups(notADirectory.toFile(), 30));
        assertTrue(Files.exists(notADirectory));
    }

    @Test
    void toleratesNull() {
        assertDoesNotThrow(() -> ScheduledBackup.pruneOldBackups(null, 30));
    }

    @Test
    @DisplayName("the shipped limit is the documented 30")
    void limitIsThirty() {
        assertEquals(30, ScheduledBackup.MAX_BACKUP_FILES);
    }
}
