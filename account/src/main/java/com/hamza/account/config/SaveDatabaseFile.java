package com.hamza.account.config;

import com.hamza.account.backup.BackupController;
import com.hamza.account.backup.BackupService;
import com.hamza.controlsfx.alert.AllAlerts;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.prefs.Preferences;

@Log4j2
public class SaveDatabaseFile {

    public static void saveBeforeClose(boolean showMessage) throws Exception {
        var prefs = Preferences.userNodeForPackage(BackupController.class);

        var backupPath = prefs.get("backupPath", System.getProperty("user.home"));
        var encryptionPassword = prefs.get("encryptionPassword", "");
        var connection = new ConnectionToDatabase();
        var backupService = new BackupService(connection.getHost()
                , connection.getPort(), connection.getDbName(), connection.getUsername(), connection.getPass()
                , encryptionPassword);
        backupService.backupToFile(new File(backupPath));

        if (showMessage) {
            AllAlerts.alertSave();
        }
    }

}
