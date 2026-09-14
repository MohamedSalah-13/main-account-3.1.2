package com.hamza.account.features.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BackupKindTest {

    @Test
    @DisplayName("the scheduled kind keeps the name every existing backup already has")
    void scheduledKeepsTheOldName() {
        assertEquals("backup_20260914_101500.enc", BackupKind.SCHEDULED.fileName("20260914_101500"));
        assertTrue(BackupKind.SCHEDULED.matches("backup_20250101_000000.enc"),
                "the backups an install holds today must stay in their pool, or nothing would ever prune them");
    }

    @Test
    @DisplayName("no kind's files are matched by another kind")
    void kindsDoNotOverlap() {
        for (BackupKind kind : BackupKind.values()) {
            String file = kind.fileName("20260914_101500");
            for (BackupKind other : BackupKind.values()) {
                assertEquals(kind == other, other.matches(file), other + " matching " + file);
            }
        }
    }

    @Test
    @DisplayName("a file that is merely encrypted is nobody's backup")
    void foreignFilesMatchNothing() {
        for (String name : new String[]{"passwords.enc", "backup.enc", "my_backup_1.enc", "backup_1.txt", null}) {
            assertTrue(Arrays.stream(BackupKind.values()).noneMatch(kind -> kind.matches(name)), String.valueOf(name));
        }
    }

    @Test
    void suffixIgnoresCase() {
        assertTrue(BackupKind.AFTER_INVOICE.matches("after-invoice_1.ENC"));
    }

    @Test
    @DisplayName("the copy a restore overwrote is never pruned; the rest keep a number")
    void retention() {
        assertEquals(BackupKind.KEEP_ALL, BackupKind.BEFORE_RESTORE.keep());
        assertEquals(30, BackupKind.SCHEDULED.keep());
        assertEquals(30, BackupKind.BEFORE_DELETE.keep());
        assertEquals(10, BackupKind.AFTER_INVOICE.keep());
    }
}
