package com.hamza.account.config;

import com.hamza.account.backup.ScheduledBackup;
import com.hamza.account.features.backup.BackupKind;
import com.hamza.account.view.DownLoadApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import lombok.extern.log4j.Log4j2;

import java.io.File;

@Log4j2
public class SaveDatabaseFile {

    /** A backup into the scheduled pool - the button, and the copy taken on closing. */
    public static void saveBeforeClose(boolean showMessage) throws Exception {
        save(BackupKind.SCHEDULED, showMessage);
    }

    /**
     * A backup of {@code kind} into the backup folder, then that kind's retention. Pruned only
     * after the new copy exists, so a failed backup cannot take the existing ones with it.
     */
    public static void save(BackupKind kind, boolean showMessage) throws Exception {
        File backupDir = new File(ScheduledBackup.backupPath());
        DownLoadApplication.loadBackupService().backupToFile(backupDir, kind);
        ScheduledBackup.pruneOldBackups(backupDir, kind);

        if (showMessage) {
            AllAlerts.alertSave();
        }
    }

}
