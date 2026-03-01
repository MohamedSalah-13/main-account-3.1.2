package com.hamza.controlsfx.backupPane;

import com.hamza.controlsfx.language.Error_Text_Show;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class BackupCon {

    private final DatabaseBackup databaseBackup;

    public BackupCon(@NotNull DatabaseBackup databaseBackup) {
        this.databaseBackup = databaseBackup;
    }

    /**
     * Creates a backup of the database to the specified path.
     *
     * @param backupPath the path where the backup should be stored
     * @return true if the backup was successful, false otherwise
     * @throws Exception if an error occurs during the backup process
     */
    public boolean backup(@NotNull String backupPath) throws Exception {
        try {
            return databaseBackup.backup(backupPath);
        } catch (IOException e) {
            if (e.getMessage().contains(" CreateProcess error=2, The system cannot find the file specified")) {
                throw new Exception(Error_Text_Show.NO_SUCH_FILE_OR_DIRECTORY);
            } else throw new Exception(Error_Text_Show.UNABLE_TO_LOAD_DATA + " !" + e.getMessage());
        } catch (InterruptedException e) {
            throw new Exception(Error_Text_Show.UNABLE_TO_LOAD_DATA + " !" + e.getMessage());
        }
    }

    /**
     * Restores the database from the specified file path.
     *
     * @param filePath The path to the backup file from which to restore the database.
     * @return true if the restore operation was successful; false otherwise.
     * @throws IOException          If an I/O error occurs during the restore process.
     * @throws InterruptedException If the restore process is interrupted.
     */
    public boolean restore(@NotNull String filePath) throws IOException, InterruptedException {
        return databaseBackup.restore(filePath);
    }

}
