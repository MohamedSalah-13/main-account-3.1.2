package com.hamza.controlsfx.backupPane;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * The DatabaseBackup interface provides methods for backing up and restoring a database.
 * Implementations of this interface are expected to handle the specifics of the backup
 * and restore operations for a particular database system.
 */
public interface DatabaseBackup {

    /**
     * Creates a backup of the database to the specified path.
     *
     * @param backupPath the path where the backup should be stored
     * @return true if the backup was successful, false otherwise
     * @throws IOException if an I/O error occurs during the backup process
     * @throws InterruptedException if the backup process is interrupted
     */
    boolean backup(@NotNull String backupPath) throws IOException, InterruptedException;

    /**
     * Restores the database from the specified file path.
     *
     * @param filePath The path to the backup file from which to restore the database.
     * @return true if the restore operation was successful; false otherwise.
     * @throws IOException If an I/O error occurs during the restore process.
     * @throws InterruptedException If the restore process is interrupted.
     */
    boolean restore(@NotNull String filePath) throws IOException, InterruptedException;
}
