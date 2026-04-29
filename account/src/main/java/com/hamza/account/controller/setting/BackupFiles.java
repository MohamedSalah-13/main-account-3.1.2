package com.hamza.account.controller.setting;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.backupPane.DatabaseBackup;
import com.hamza.controlsfx.controller.MaskerPaneSetting;
import com.hamza.controlsfx.interfaceData.DownloadTask;
import com.hamza.controlsfx.util.Extensions;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseFile;
import javafx.beans.property.BooleanProperty;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static com.hamza.account.controller.setting.BackupController.*;


@Log4j2
public record BackupFiles<V>(DatabaseBackup databaseBackup, DownloadTask progressFinishTask, Task<V> progressTask) {

    private void encryptFileDatabase(File selectedFile) {
        try {
            // Re-encrypt file after restore attempt
            CryptoDatabaseFile.encryptFile(selectedFile);
        } catch (Exception ex) {
            log.error("Failed to re-encrypt the selected file after restore.", ex);
            AllAlerts.showExceptionDialog(ex);
        }
    }

    public void backupAction(MaskerPaneSetting maskerPaneSetting) {
        try {
            FileChooser fileChooser = getFileChooser();
            fileChooser.setInitialFileName(generateBackupFileName());
            File targetFile = fileChooser.showSaveDialog(null);

            if (targetFile == null) {
                log.warn("No file selected for backup.");
                throw new IOException("No file selected for backup.");
            }
            maskerPaneSetting.showMaskerPane(() -> {
                try {
                    Path backupPath = targetFile.toPath().toAbsolutePath();
                    boolean succeeded = databaseBackup.backup(quoteForCommand(backupPath));

                    if (!succeeded) {
                        log.error("Backup failed for path: {}", backupPath);
                        throw new IOException("Backup failed for path: " + backupPath);
                    }

                } catch (Exception e) {
                    log.error("Backup failed with an unexpected error.", e);
                    AllAlerts.showExceptionDialog(e);
                }
            });

            maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
                try {
                    // Encrypt file after saving
                    CryptoDatabaseFile.encryptFile(targetFile);
//            setAfterSaved(true);
                    AllAlerts.alertSave();
                } catch (Exception e) {
                    log.error("Failed to encrypt the selected file after backup.", e);
                    AllAlerts.showExceptionDialog(e);
                }
            });

        } catch (Exception e) {
            log.error("Backup failed with an unexpected error.", e);
            AllAlerts.showExceptionDialog(e);
        }
    }

    public void restoreAction(MaskerPaneSetting maskerPaneSetting, BooleanProperty afterSaved) {
        try {
            var result = getResult();
            maskerPaneSetting.showMaskerPane(() -> databaseBackup.restore(quoteForCommand(result.decryptedPath())));
            maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
                log.info("Restore successful.");
                encryptFileDatabase(result.selectedFile());
                afterSaved.setValue(true);
            });
            maskerPaneSetting.getVoidTask().setOnFailed(workerStateEvent -> {
                log.error("Restore failed with an unexpected error.", maskerPaneSetting.getVoidTask().getException());
                encryptFileDatabase(result.selectedFile());
                AllAlerts.showExceptionDialog(maskerPaneSetting.getVoidTask().getException());
            });
            maskerPaneSetting.getVoidTask().setOnCancelled(workerStateEvent -> {
                log.warn("Restore cancelled.");
                encryptFileDatabase(result.selectedFile());
            });

        } catch (Exception e) {
            log.error("Restore failed with an unexpected error.", e);
            AllAlerts.showExceptionDialog(e);
        }
    }

    @NotNull
    private Result getResult() throws Exception {
        FileChooser fileChooser = getFileChooser();
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile == null) {
            log.warn("No file selected for restore.");
            throw new IOException("No file selected for restore.");
        }

        Path decryptedPath = CryptoDatabaseFile.decryptFile(selectedFile);
        if (decryptedPath == null) {
            log.error("Decryption returned null for file: {}", selectedFile.getAbsolutePath());
            throw new IOException("Decryption returned null for file: " + selectedFile.getAbsolutePath());
        }
        return new Result(selectedFile, decryptedPath);
    }

    private FileChooser getFileChooser() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(Extensions.EXT_FILTER_SQL);
        return fc;
    }

    public String generateBackupFileName() {
        String ts = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
        return BACKUP_FILE_PREFIX + ts + BACKUP_FILE_EXTENSION;
    }

    private String quoteForCommand(Path path) {
        return "\"" + normalizePathForProcess(path) + "\"";
    }

    private String normalizePathForProcess(Path path) {
        String s = path.toAbsolutePath().toString();
        // Normalize for process invocation on Windows; other platforms remain unchanged.
        return File.separatorChar == '\\' ? s.replace('\\', '/') : s;
    }

    private record Result(File selectedFile, Path decryptedPath) {
    }
}
