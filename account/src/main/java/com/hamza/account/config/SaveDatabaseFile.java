package com.hamza.account.config;

import com.hamza.account.view.DownLoadApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import lombok.extern.log4j.Log4j2;

import java.io.File;

import static com.hamza.account.backup.ScheduledBackup.BACKUP_PATH;

@Log4j2
public class SaveDatabaseFile {

    public static void saveBeforeClose(boolean showMessage) throws Exception {

//        var backupPath = prefsBackup.get("backupPath", System.getProperty("user.home"));
//        var encryptionPassword = prefsBackup.get("encryptionPassword", "");
//        var connection = new ConnectionToDatabase();
//        var backupService = new BackupService(connection.getHost()
//                , connection.getPort(), connection.getDbName(), connection.getUsername(), connection.getPass()
//                , encryptionPassword);
        DownLoadApplication.loadBackupService().backupToFile(new File(BACKUP_PATH));

        if (showMessage) {
            AllAlerts.alertSave();
        }
    }

}
