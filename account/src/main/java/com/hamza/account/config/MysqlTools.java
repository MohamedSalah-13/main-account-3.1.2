package com.hamza.account.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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

    /** Set by a jpackage launcher to the path of its own executable. */
    static final String APP_PATH_PROPERTY = "jpackage.app-path";

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
        return bundled(command, System.getProperty(APP_PATH_PROPERTY));
    }

    /**
     * The bundled copy, found from where the program is rather than from where it was started.
     * <p>
     * {@code mysql/bin/} used to be resolved against the working directory alone. A shortcut sets
     * that to the install folder, so it worked - and a program started from anywhere else looked
     * for {@code mysqldump} in a folder that does not have one, which fails every backup, and
     * says so on the day a backup is needed. A packaged launcher sets {@code jpackage.app-path}
     * to its own executable, and the server sits beside it. From source there is no such
     * property, and the working directory is still the answer.
     */
    static String bundled(String command, String launcherPath) {
        if (launcherPath != null && !launcherPath.isBlank()) {
            Path beside = Path.of(launcherPath).toAbsolutePath().getParent();
            if (beside != null && Files.isDirectory(beside.resolve(BUNDLED_DIRECTORY))) {
                return beside.resolve(BUNDLED_DIRECTORY).resolve(command).toString();
            }
        }
        return new File(BUNDLED_DIRECTORY + command).getPath();
    }
}
