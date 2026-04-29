package com.hamza.account.config;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.backupPane.BackupCon;
import com.hamza.controlsfx.util.FileDir;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseFile;
import com.hamza.controlsfx.language.Error_Text_Show;
import lombok.extern.log4j.Log4j2;

import static com.hamza.account.controller.setting.BackupController.*;

@Log4j2
public class SaveDatabaseFile {

    public static void saveBeforeClose(boolean showMessage) throws Exception {
        java.nio.file.Path backupFilePath = resolveBackupFilePath();
        String quotedBackupPath = quoteForCommand(backupFilePath);

        boolean backupSucceeded = new BackupCon(new ConnectionToMysql().connect()).backup(quotedBackupPath);

        if (!backupSucceeded || !java.nio.file.Files.exists(backupFilePath)) {
            throw new Exception(Error_Text_Show.NO_SUCH_FILE_OR_DIRECTORY);
        }

        CryptoDatabaseFile.encryptFile(backupFilePath.toFile());

        if (showMessage) {
            AllAlerts.alertSave();
        }
    }

    private static java.nio.file.Path resolveBackupFilePath() throws java.io.IOException {
        String targetDir = FileDir.whereToSaveFile(Configs.BACKUP_DATA_PATH);
        java.nio.file.Path dirPath = java.nio.file.Path.of(targetDir);
        java.nio.file.Files.createDirectories(dirPath);
        String timestamp = java.time.LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
        String fileName = BACKUP_FILE_PREFIX + timestamp + BACKUP_FILE_EXTENSION;
        return dirPath.resolve(fileName).toAbsolutePath();
    }

    private static String quoteForCommand(java.nio.file.Path path) {
        return "\"" + path.toString() + "\"";
    }

}
