package com.hamza.account.config;

import java.io.File;

/**
 * Where {@code mysqldump} and {@code mysql} are, answered in one place.
 * <p>
 * There were three answers before this: {@code DatabaseBackupService} honoured
 * {@link PropertiesName#getDatabaseUsePathVariableSetting()} and fell back to a copy
 * shipped beside the application, {@code BackupService} hardcoded the bare command names,
 * and {@code DatabaseProperties} carried a third pair of settings nothing read at all. On
 * an install without MySQL on the PATH that meant the pre-migration dump worked and every
 * backup the user asked for failed - the worst direction for the two to disagree in.
 */
public final class MysqlTools {

    /** The copy shipped beside the application, used when the PATH is not to be trusted. */
    private static final String BUNDLED_DIRECTORY = "mysql/bin/";

    private MysqlTools() {
    }

    public static String mysqldump() {
        return resolve("mysqldump");
    }

    public static String mysql() {
        return resolve("mysql");
    }

    private static String resolve(String command) {
        if (PropertiesName.getDatabaseUsePathVariableSetting()) {
            return command;
        }
        return new File(BUNDLED_DIRECTORY + command).getPath();
    }
}
