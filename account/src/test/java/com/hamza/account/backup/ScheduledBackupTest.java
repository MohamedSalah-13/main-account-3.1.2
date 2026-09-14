package com.hamza.account.backup;

import com.hamza.account.features.backup.BackupKind;
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
 * {@link ScheduledBackup#pruneOldBackups} and {@link ScheduledBackup#initialDelayMinutes}
 * - the rest of the scheduling and the Preferences-backed settings are left alone
 * deliberately, since exercising them would read and write the real user preferences of
 * whoever runs the tests. Both methods under test take everything they need as arguments,
 * which is what makes them reachable at all.
 */
class ScheduledBackupTest {

    /**
     * Creates {@code count} backups of {@code kind}, oldest first. Modification times are set
     * explicitly and spaced a minute apart: the files are written within the same
     * millisecond otherwise, and the order under test is the order of these
     * timestamps.
     */
    private static List<File> givenBackups(Path dir, BackupKind kind, int count) throws Exception {
        long base = System.currentTimeMillis() - (count + 1L) * 60_000;
        return IntStream.range(0, count).mapToObj(i -> {
            try {
                File file = dir.resolve(kind.fileName("%03d".formatted(i))).toFile();
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
        List<File> created = givenBackups(dir, BackupKind.SCHEDULED, 35);

        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.SCHEDULED);

        Set<String> remaining = namesIn(dir);
        assertEquals(30, remaining.size());
        // 005 .. 034 are the newest thirty.
        created.subList(5, 35).forEach(f ->
                assertTrue(remaining.contains(f.getName()), f.getName() + " should have been kept"));
        created.subList(0, 5).forEach(f ->
                assertFalse(remaining.contains(f.getName()), f.getName() + " should have been deleted"));
    }

    @Test
    void keepsEverythingWhenUnderTheLimit(@TempDir Path dir) throws Exception {
        givenBackups(dir, BackupKind.SCHEDULED, 5);

        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.SCHEDULED);

        assertEquals(5, namesIn(dir).size());
    }

    @Test
    @DisplayName("exactly the limit is not over it")
    void keepsExactlyTheLimit(@TempDir Path dir) throws Exception {
        givenBackups(dir, BackupKind.SCHEDULED, 30);

        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.SCHEDULED);

        assertEquals(30, namesIn(dir).size());
    }

    @Test
    @DisplayName("after-invoice copies cannot push out the scheduled ones or the copy taken before a delete")
    void kindsArePrunedApart(@TempDir Path dir) throws Exception {
        List<File> scheduled = givenBackups(dir, BackupKind.SCHEDULED, 3);
        List<File> beforeDelete = givenBackups(dir, BackupKind.BEFORE_DELETE, 1);
        List<File> afterInvoice = givenBackups(dir, BackupKind.AFTER_INVOICE, 40);

        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.AFTER_INVOICE);
        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.SCHEDULED);
        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.BEFORE_DELETE);

        Set<String> remaining = namesIn(dir);
        scheduled.forEach(f -> assertTrue(remaining.contains(f.getName()), f.getName()));
        beforeDelete.forEach(f -> assertTrue(remaining.contains(f.getName()), f.getName()));
        assertEquals(BackupKind.AFTER_INVOICE.keep(),
                remaining.stream().filter(BackupKind.AFTER_INVOICE::matches).count());
        assertTrue(remaining.contains(afterInvoice.getLast().getName()), "the newest after-invoice copy stays");
    }

    @Test
    @DisplayName("the copy taken before a restore is never pruned")
    void beforeRestoreIsKept(@TempDir Path dir) throws Exception {
        givenBackups(dir, BackupKind.BEFORE_RESTORE, 45);

        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.BEFORE_RESTORE);

        assertEquals(45, namesIn(dir).size());
    }

    @Test
    @DisplayName("files that are not our backups are left alone, encrypted or not")
    void ignoresFilesThatAreNotBackups(@TempDir Path dir) throws Exception {
        givenBackups(dir, BackupKind.AFTER_INVOICE, 12);
        Files.writeString(dir.resolve("notes.txt"), "keep me");
        Files.writeString(dir.resolve("dump.sql"), "keep me too");
        // The folder defaults to the user's home directory; an .enc there may be anybody's.
        Files.writeString(dir.resolve("passwords.enc"), "not ours");
        Files.createDirectory(dir.resolve("subfolder"));
        assertTrue(dir.resolve("passwords.enc").toFile().setLastModified(1_000));

        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.AFTER_INVOICE);

        Set<String> remaining = namesIn(dir);
        assertTrue(remaining.contains("notes.txt"));
        assertTrue(remaining.contains("dump.sql"));
        assertTrue(remaining.contains("passwords.enc"), "an encrypted file that is not a backup of ours");
        assertTrue(remaining.contains("subfolder"));
        assertEquals(10, remaining.stream().filter(BackupKind.AFTER_INVOICE::matches).count());
    }

    @Test
    void matchesTheSuffixCaseInsensitively(@TempDir Path dir) throws Exception {
        List<File> created = givenBackups(dir, BackupKind.AFTER_INVOICE, 11);
        File oldest = created.getFirst();
        File upper = dir.resolve(oldest.getName().replace(".enc", ".ENC")).toFile();
        assertTrue(oldest.renameTo(upper));
        assertTrue(upper.setLastModified(1_000));

        ScheduledBackup.pruneOldBackups(dir.toFile(), BackupKind.AFTER_INVOICE);

        assertFalse(namesIn(dir).contains(upper.getName()));
        assertEquals(10, namesIn(dir).size());
    }

    @Test
    void toleratesAMissingDirectory(@TempDir Path dir) {
        File missing = dir.resolve("does-not-exist").toFile();

        assertDoesNotThrow(() -> ScheduledBackup.pruneOldBackups(missing, BackupKind.SCHEDULED));
    }

    @Test
    void toleratesAFileWhereADirectoryWasExpected(@TempDir Path dir) throws Exception {
        Path notADirectory = dir.resolve("plain.txt");
        Files.writeString(notADirectory, "x");

        assertDoesNotThrow(() -> ScheduledBackup.pruneOldBackups(notADirectory.toFile(), BackupKind.SCHEDULED));
        assertTrue(Files.exists(notADirectory));
    }

    @Test
    void toleratesNull() {
        assertDoesNotThrow(() -> ScheduledBackup.pruneOldBackups(null, BackupKind.SCHEDULED));
    }

    @Test
    @DisplayName("the shipped limit is the documented 30")
    void limitIsThirty() {
        assertEquals(30, ScheduledBackup.MAX_BACKUP_FILES);
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("when the first scheduled backup is due")
    class InitialDelay {

        private static final long HOURLY = 60;
        private static final long NOW = 1_700_000_000_000L;

        private File givenBackupAgedMinutes(Path dir, long minutes) throws Exception {
            File file = dir.resolve("backup_aged.enc").toFile();
            Files.writeString(file.toPath(), "backup");
            assertTrue(file.setLastModified(NOW - minutes * 60_000),
                    "could not set the modification time, the test cannot age the file");
            return file;
        }

        @Test
        @DisplayName("an empty folder backs up at once - there is nothing to lose by waiting")
        void nothingToStartFrom(@TempDir Path dir) {
            assertEquals(0, ScheduledBackup.initialDelayMinutes(dir.toFile(), HOURLY, NOW));
        }

        @Test
        @DisplayName("a recent backup pushes the first run out to the end of the interval")
        void aRecentBackupDelaysTheFirstRun(@TempDir Path dir) throws Exception {
            givenBackupAgedMinutes(dir, 10);

            // This is the whole point: the delay used to be zero, so launching the
            // program dumped the entire database again ten minutes after the last one.
            assertEquals(50, ScheduledBackup.initialDelayMinutes(dir.toFile(), HOURLY, NOW));
        }

        @Test
        @DisplayName("a backup older than the interval is already overdue")
        void anOldBackupIsDueNow(@TempDir Path dir) throws Exception {
            givenBackupAgedMinutes(dir, 180);

            assertEquals(0, ScheduledBackup.initialDelayMinutes(dir.toFile(), HOURLY, NOW));
        }

        @Test
        @DisplayName("exactly one interval old is due, not one minute short of it")
        void theBoundaryIsDue(@TempDir Path dir) throws Exception {
            givenBackupAgedMinutes(dir, HOURLY);

            assertEquals(0, ScheduledBackup.initialDelayMinutes(dir.toFile(), HOURLY, NOW));
        }

        @Test
        @DisplayName("the newest backup is what counts, not the oldest")
        void theNewestDecides(@TempDir Path dir) throws Exception {
            givenBackupAgedMinutes(dir, 600);
            File recent = dir.resolve("backup_recent.enc").toFile();
            Files.writeString(recent.toPath(), "backup");
            assertTrue(recent.setLastModified(NOW - 15 * 60_000));

            assertEquals(45, ScheduledBackup.initialDelayMinutes(dir.toFile(), HOURLY, NOW));
        }

        @Test
        @DisplayName("a folder that is not there backs up at once rather than never")
        void aMissingFolderIsNotAnExcuseToWait(@TempDir Path dir) {
            assertEquals(0, ScheduledBackup.initialDelayMinutes(
                    dir.resolve("nowhere").toFile(), HOURLY, NOW));
        }

        @Test
        @DisplayName("a recent after-invoice copy does not postpone the schedule")
        void afterInvoiceCopiesDoNotCount(@TempDir Path dir) throws Exception {
            givenBackupAgedMinutes(dir, 600);
            File recent = dir.resolve(BackupKind.AFTER_INVOICE.fileName("recent")).toFile();
            Files.writeString(recent.toPath(), "backup");
            assertTrue(recent.setLastModified(NOW - 5 * 60_000));

            assertEquals(0, ScheduledBackup.initialDelayMinutes(dir.toFile(), HOURLY, NOW));
        }

        @Test
        @DisplayName("files that are not backups do not count as one")
        void otherFilesAreIgnored(@TempDir Path dir) throws Exception {
            Files.writeString(dir.resolve("notes.txt"), "not a backup");

            assertEquals(0, ScheduledBackup.initialDelayMinutes(dir.toFile(), HOURLY, NOW));
        }
    }
}
