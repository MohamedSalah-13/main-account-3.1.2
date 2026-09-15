package com.hamza.account.features.documentdelete;

/**
 * The copy of the database taken before documents are removed.
 * <p>
 * Passed in rather than called, because the backup belongs to the application around this
 * package - it runs {@code mysqldump} and reads where the backups go - and this package has no
 * JavaFX and no knowledge of either.
 */
@FunctionalInterface
public interface BackupBeforeDelete {

    /** For a caller that has its own copy of the data already - a test inside a rolled-back transaction. */
    BackupBeforeDelete NONE = () -> { };

    void take() throws Exception;
}
