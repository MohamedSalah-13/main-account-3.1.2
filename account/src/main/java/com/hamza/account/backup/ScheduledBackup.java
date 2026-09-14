package com.hamza.account.backup;

import com.hamza.account.features.backup.BackupKind;
import com.hamza.account.features.backup.BackupPolicy;
import com.hamza.account.features.backup.RetentionPolicy;
import com.hamza.account.features.notification.AppNotifications;
import com.hamza.account.features.notification.NotificationCategories;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

@Log4j2
public class ScheduledBackup {

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

    /**
     * Starts the timer, unless another machine owns the schedule.
     * <p>
     * Every open copy of the program used to start one of these, so a shop with four tills
     * took four full dumps an hour into four different folders, each pruning its own to
     * thirty files. One machine owns it now - see {@link BackupPolicy} - and the others
     * schedule nothing at all.
     */
    public static void startScheduler(BackupService backupService) {
        if (!BackupPolicy.isBackupOwner()) {
            log.info("Automatic backups are owned by another machine ({}); nothing scheduled here",
                    BackupPolicy.ownerMachine().orElse("?"));
            stopScheduler();
            return;
        }
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "scheduled-backup");
                thread.setDaemon(true);
                return thread;
            });
        }
        if (backupTaskHandle != null) backupTaskHandle.cancel(false);

        long periodMinutes = getTime() * 60;
        long initialDelay = initialDelayMinutes(new File(backupPath()), periodMinutes, System.currentTimeMillis());

        backupTaskHandle = scheduler.scheduleAtFixedRate(() -> {
            try {
                File dir = new File(backupPath());
                File backup = backupService.backupToFile(dir, BackupKind.SCHEDULED);
                // Pruned only after a backup succeeds: a failed run must not be able
                // to delete the copies that are still the most recent ones we have.
                pruneOldBackups(dir, BackupKind.SCHEDULED);
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
        }, initialDelay, periodMinutes, TimeUnit.MINUTES);
    }

    /**
     * How long to wait before the first scheduled backup, so the schedule is about how
     * old the newest backup is rather than about when the application happened to start.
     * <p>
     * It used to be zero, and the scheduler starts at login - so every launch began with
     * a full {@code mysqldump} of the whole database, whatever the chosen interval said.
     * A shop opening the program three times a morning took three complete backups before
     * serving anyone.
     * <p>
     * A folder with no backup in it still starts immediately: there is nothing to lose by
     * waiting, and everything to lose by not having one at all.
     * <p>
     * Only scheduled backups count. An after-invoice copy taken ten minutes ago would
     * otherwise push the schedule out, and it belongs to a pool of ten that rolls in an
     * afternoon - it is not the day's copy the schedule exists to keep.
     *
     * @param periodMinutes the chosen interval; a folder whose newest backup is already
     *                      older than this is due now
     */
    public static long initialDelayMinutes(File backupDir, long periodMinutes, long nowMillis) {
        long newest = newestBackupTime(backupDir);
        if (newest <= 0) {
            return 0;
        }
        long elapsedMinutes = (nowMillis - newest) / 60_000;
        if (elapsedMinutes >= periodMinutes) {
            return 0;
        }
        return periodMinutes - elapsedMinutes;
    }

    /** When the newest backup in the folder was written, or 0 when there is none. */
    private static long newestBackupTime(File backupDir) {
        if (backupDir == null || !backupDir.isDirectory()) {
            return 0;
        }
        File[] backups = backupDir.listFiles(file ->
                file.isFile() && BackupKind.SCHEDULED.matches(file.getName()));
        if (backups == null || backups.length == 0) {
            return 0;
        }
        return Arrays.stream(backups).mapToLong(File::lastModified).max().orElse(0);
    }

    /**
     * Keeps the backups of {@code kind} that its {@link BackupKind#retention()} chooses and
     * deletes the rest of that kind. Nothing of another kind, and nothing that is not ours,
     * is looked at. A bound on the number of files is still the point - every policy but
     * the before-restore one has a ceiling - it just no longer means "the last few hours only".
     * <p>
     * Retention used to be by age - delete anything older than 30 days - which set
     * no ceiling on how many files could accumulate inside that window. On an
     * hourly schedule that is more than 700 backups before the first one becomes
     * eligible for deletion, which is what filled the disk. A count is a bound the
     * disk can actually be sized against.
     * <p>
     * It then counted every {@code .enc} in the folder as one pool, which let one kind
     * delete another - see {@link BackupKind} - and deleted any encrypted file of anybody's
     * that happened to sit in the folder, which defaults to the user's home directory.
     */
    public static void pruneOldBackups(File backupDir, BackupKind kind) {
        pruneOldBackups(backupDir, kind, ZoneId.systemDefault());
    }

    /** {@link #pruneOldBackups(File, BackupKind)} with the zone days are counted in, for tests. */
    static void pruneOldBackups(File backupDir, BackupKind kind, ZoneId zone) {
        if (backupDir == null || !backupDir.isDirectory() || kind.retention() instanceof RetentionPolicy.KeepAll) {
            return;
        }

        File[] backups = backupDir.listFiles(file -> file.isFile() && kind.matches(file.getName()));
        if (backups == null || backups.length == 0) {
            return;
        }

        List<RetentionPolicy.Candidate> candidates = Arrays.stream(backups)
                .map(file -> new RetentionPolicy.Candidate(file.getName(), file.lastModified()))
                .toList();
        Set<String> kept = kind.retention().keep(candidates, zone);

        for (File surplus : backups) {
            if (kept.contains(surplus.getName())) {
                continue;
            }
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
