package com.hamza.account.backup;

import com.hamza.account.features.notification.AppNotifications;
import com.hamza.account.features.notification.NotificationCategories;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

@Log4j2
public class ScheduledBackup {

    /**
     * How many backup files to keep in the backup folder.
     */
    public static final int MAX_BACKUP_FILES = 30;

    private static final String BACKUP_FILE_SUFFIX = ".enc";

    /*
     * Constant keys, so consecutive successes collapse into one inbox entry with a
     * counter rather than a row per hour, and a run that keeps failing stays a
     * single entry the user can act on.
     */
    private static final String SUCCESS_KEY = "backup.scheduled.success";
    private static final String FAILURE_KEY = "backup.scheduled.failure";

    /**
     * Stored in Preferences instead of the combo box's displayed label, so the saved
     * schedule survives a language switch. Old installs may still hold the Arabic
     * label {@code getTime()} used to switch on directly; those are still recognised.
     */
    public static final String INTERVAL_DISABLED = "disabled";
    public static final String INTERVAL_HOURLY = "hourly";
    public static final String INTERVAL_EVERY_2_HOURS = "every_2_hours";
    public static final String INTERVAL_EVERY_6_HOURS = "every_6_hours";
    public static final String INTERVAL_DAILY = "daily";

    public static Preferences prefsBackup = Preferences.userNodeForPackage(BackupController.class);
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> backupTaskHandle;

    /*
     * These three were static final fields, read from Preferences once when the
     * class loaded. Changing the folder, the password or the interval in the
     * settings screen wrote the new value to Preferences but every reader kept
     * seeing the old one until the application was restarted - so a re-scheduled
     * backup still ran on the old interval and still wrote to the old folder.
     * Reading at the point of use keeps them in step with what the user set.
     */

    public static String backupPath() {
        return prefsBackup.get("backupPath", System.getProperty("user.home"));
    }

    public static String encryptionPassword() {
        return prefsBackup.get("encryptionPassword", "");
    }

    public static String interval() {
        return prefsBackup.get("interval", INTERVAL_DISABLED);
    }

    public static void startScheduler(BackupService backupService) {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "scheduled-backup");
                thread.setDaemon(true);
                return thread;
            });
        }
        if (backupTaskHandle != null) backupTaskHandle.cancel(false);

        backupTaskHandle = scheduler.scheduleAtFixedRate(() -> {
            try {
                File dir = new File(backupPath());
                File backup = backupService.backupToFile(dir);
                // Pruned only after a backup succeeds: a failed run must not be able
                // to delete the copies that are still the most recent ones we have.
                pruneOldBackups(dir, MAX_BACKUP_FILES);
                setStatus(LanguageManager.getInstance().getString("backup.status.auto.created", backup.getName()));
                AppNotifications.success(SUCCESS_KEY, NotificationCategories.BACKUP,
                        LanguageManager.getInstance().getString("backup.notify.success.title"), backup.getName());
            } catch (Exception e) {
                var report = com.hamza.controlsfx.error.ErrorReporter.shared()
                        .report(LanguageManager.getInstance().getString("backup.op.create.auto"), e);
                setStatus(report.message());
                // A log line was the only trace of this, which is how a folder that
                // has been unwritable for weeks goes unnoticed.
                AppNotifications.error(FAILURE_KEY, NotificationCategories.BACKUP,
                        LanguageManager.getInstance().getString("backup.notify.failure.title"), report.message());
            }
        }, 0, getTime(), TimeUnit.HOURS);
    }

    /**
     * Keeps the newest {@code keep} backups in the folder and deletes the rest.
     * <p>
     * Retention used to be by age - delete anything older than 30 days - which set
     * no ceiling on how many files could accumulate inside that window. On an
     * hourly schedule that is more than 700 backups before the first one becomes
     * eligible for deletion, which is what filled the disk. A count is a bound the
     * disk can actually be sized against.
     */
    public static void pruneOldBackups(File backupDir, int keep) {
        if (backupDir == null || !backupDir.isDirectory()) {
            return;
        }

        File[] backups = backupDir.listFiles(file ->
                file.isFile() && file.getName().toLowerCase().endsWith(BACKUP_FILE_SUFFIX));

        if (backups == null || backups.length <= keep) {
            return;
        }

        // Newest first, so everything from index `keep` onwards is surplus.
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());

        for (int i = keep; i < backups.length; i++) {
            File surplus = backups[i];
            if (surplus.delete()) {
                log.info("Deleted old backup: {}", surplus.getName());
            } else {
                setStatus(LanguageManager.getInstance().getString("backup.status.prune.failed", surplus.getName()));
            }
        }
    }

    public static void stopScheduler() {
        if (backupTaskHandle != null) backupTaskHandle.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
    }

    private static void setStatus(String msg) {
        log.info("{} | {}", msg, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    public static long getTime() {
        return switch (interval()) {
            case INTERVAL_HOURLY, "كل ساعة" -> 1;
            case INTERVAL_EVERY_2_HOURS, "كل ساعتين" -> 2;
            case INTERVAL_EVERY_6_HOURS, "كل 6 ساعات" -> 6;
            case INTERVAL_DAILY, "كل يوم" -> 24;
            default -> 0;
        };
    }
}
