package com.hamza.account.features.notification;

import com.hamza.account.backup.ScheduledBackup;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Checks that a recent backup actually exists on disk.
 * <p>
 * Scheduled backups fail quietly: {@code ScheduledBackup} logs the failure and
 * writes a status line nobody reads, so a folder that has been unreachable for a
 * fortnight looks exactly like one that is working. This looks at the folder
 * itself rather than at whether the scheduler thinks it ran, which is the only
 * check that catches a full disk, a disconnected network share, or a scheduler
 * that was never switched on.
 * <p>
 * Filesystem only - no database, no pool connection - so it is safe to run on a
 * machine whose database is down, which is exactly when the backups matter.
 */
public class BackupHealthSource implements NotificationSource {

    public static final String ID = "backup.stale";
    private static final String BACKUP_SUFFIX = ".enc";

    /** Longer than the longest scheduled interval (daily) plus room for a slow run. */
    private static final Duration STALE_AFTER = Duration.ofHours(36);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @NotNull
    @Override
    public String category() {
        return NotificationCategories.BACKUP;
    }

    @NotNull
    @Override
    public String displayName() {
        return "تأخر النسخ الاحتياطي";
    }

    @NotNull
    @Override
    public Duration interval() {
        return Duration.ofHours(6);
    }

    /**
     * Only meaningful once automatic backups are switched on. Nagging someone who
     * has deliberately chosen manual backups is how a notification system gets
     * turned off wholesale.
     */
    @Override
    public boolean enabled() {
        return ScheduledBackup.getTime() > 0;
    }

    @NotNull
    @Override
    public List<AppNotification> poll() {
        File folder = new File(ScheduledBackup.backupPath());

        if (!folder.isDirectory()) {
            return List.of(problem(NotificationSeverity.ERROR,
                    "مجلد النسخ الاحتياطي غير موجود",
                    "المسار: " + folder.getAbsolutePath()));
        }

        Optional<File> newest = newestBackup(folder);
        if (newest.isEmpty()) {
            return List.of(problem(NotificationSeverity.ERROR,
                    "لا توجد نسخة احتياطية",
                    "المجلد فارغ: " + folder.getAbsolutePath()));
        }

        LocalDateTime lastBackup = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(newest.get().lastModified()), ZoneId.systemDefault());

        if (Duration.between(lastBackup, LocalDateTime.now()).compareTo(STALE_AFTER) < 0) {
            return List.of();
        }

        return List.of(problem(NotificationSeverity.WARNING,
                "لم يتم عمل نسخة احتياطية حديثة",
                "آخر نسخة: " + lastBackup.format(WHEN)));
    }

    private Optional<File> newestBackup(File folder) {
        File[] backups = folder.listFiles(file ->
                file.isFile() && file.getName().toLowerCase().endsWith(BACKUP_SUFFIX));
        if (backups == null) {
            return Optional.empty();
        }
        return Arrays.stream(backups).max(Comparator.comparingLong(File::lastModified));
    }

    private AppNotification problem(NotificationSeverity severity, String title, String message) {
        return AppNotification.builder(ID)
                .category(category())
                .severity(severity)
                .title(title)
                .message(message)
                .build();
    }
}
