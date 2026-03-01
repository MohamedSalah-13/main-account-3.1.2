package com.hamza.account.features;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * خدمة النسخ الاحتياطي التلقائي
 */
@Log4j2
public class AutoBackupService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String databasePath;
    private final String backupDirectory;

    public AutoBackupService(String databasePath, String backupDirectory) {
        this.databasePath = databasePath;
        this.backupDirectory = backupDirectory;
    }

    /**
     * بدء النسخ الاحتياطي التلقائي
     * @param intervalHours الفترة بالساعات بين كل نسخة
     */
    public void startAutoBackup(int intervalHours) {
        scheduler.scheduleAtFixedRate(
                this::performBackup,
                0,
                intervalHours,
                TimeUnit.HOURS
        );
        log.info("Auto backup started with interval: {} hours", intervalHours);
    }

    /**
     * إيقاف النسخ الاحتياطي التلقائي
     */
    public void stopAutoBackup() {
        scheduler.shutdown();
        log.info("Auto backup stopped");
    }

    /**
     * تنفيذ عملية النسخ الاحتياطي
     */
    private void performBackup() {
        try {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "backup_" + timestamp + ".db";
            Path backupPath = Paths.get(backupDirectory, backupFileName);

            Files.copy(
                    Paths.get(databasePath),
                    backupPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            log.info("Backup created successfully: {}", backupPath);

            // حذف النسخ القديمة (الاحتفاظ بآخر 10 نسخ فقط)
            cleanOldBackups();

        } catch (IOException e) {
            log.error("Error creating backup", e);
        }
    }

    /**
     * حذف النسخ الاحتياطية القديمة
     */
    private void cleanOldBackups() throws IOException {
        // TODO: تنفيذ منطق حذف النسخ القديمة
    }
}
