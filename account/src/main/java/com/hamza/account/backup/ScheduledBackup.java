package com.hamza.account.backup;

import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

@Log4j2
public class ScheduledBackup {

    public static Preferences prefsBackup = Preferences.userNodeForPackage(BackupController.class);
    public static final String BACKUP_PATH = prefsBackup.get("backupPath", System.getProperty("user.home"));
    public static final String ENCRYPTION_PASSWORD = prefsBackup.get("encryptionPassword", "");
    public static final String INTERVAL = prefsBackup.get("interval", "معطل");
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> backupTaskHandle;

    public static void startScheduler(BackupService backupService) {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        if (backupTaskHandle != null) backupTaskHandle.cancel(false);

        backupTaskHandle = scheduler.scheduleAtFixedRate(() -> {
            try {
                File dir = new File(BACKUP_PATH);
                deleteOldBackupFiles(dir, 30);
                File backup = backupService.backupToFile(dir);
                Platform.runLater(() -> setStatus("نسخ تلقائي: " + backup.getName()));
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("فشل النسخ التلقائي: " + e.getMessage()));
            }
        }, 0, getTime(), TimeUnit.HOURS);
    }

    private static void deleteOldBackupFiles(File backupDir, int days) {
        if (backupDir == null || !backupDir.exists() || !backupDir.isDirectory()) {
            return;
        }

        Instant deleteBefore = Instant.now().minus(days, ChronoUnit.DAYS);
        File[] oldBackupFiles = backupDir.listFiles(file ->
                file.isFile()
                        && file.getName().toLowerCase().endsWith(".enc")
                        && Instant.ofEpochMilli(file.lastModified()).isBefore(deleteBefore)
        );

        if (oldBackupFiles == null) {
            return;
        }

        for (File file : oldBackupFiles) {
            if (!file.delete()) {
                setStatus("تعذر حذف النسخة القديمة: " + file.getName());
            }
        }
    }
    public static void stopScheduler() {
        if (backupTaskHandle != null) backupTaskHandle.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
    }

    private static void setStatus(String msg) {
        Platform.runLater(() -> log.info("{} | {}", msg, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
    }

    public static long getTime(){
        return switch (INTERVAL) {
            case "كل ساعة" -> 1;
            case "كل ساعتين" -> 2;
            case "كل 6 ساعات" -> 6;
            case "كل يوم" -> 24;
            default -> 0;
        };
    }
}
