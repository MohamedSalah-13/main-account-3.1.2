package com.hamza.account.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One answer for where this workstation keeps its database connection files.
 * New installations use ProgramData; the working-directory location remains a
 * read fallback so an existing client continues to start after an upgrade.
 */
public final class DatabaseConfigFiles {

    public static final String DIRECTORY_ENV_VAR = "ACCOUNT_CONFIG_DIR";
    private static final String APP_DIRECTORY = "AccountK";

    private DatabaseConfigFiles() {
    }

    public static Location preferred() {
        return preferred(System.getenv(DIRECTORY_ENV_VAR), System.getenv("PROGRAMDATA"),
                System.getProperty("user.home"));
    }

    public static Location locateForRead() {
        return locateForRead(preferred(), Path.of("").toAbsolutePath().normalize());
    }

    public static Location at(Path directory) {
        return new Location(directory.toAbsolutePath().normalize(), false);
    }

    static Location preferred(String overrideDirectory, String programData, String userHome) {
        Path directory;
        if (overrideDirectory != null && !overrideDirectory.isBlank()) {
            directory = Path.of(overrideDirectory.trim());
        } else if (programData != null && !programData.isBlank()) {
            directory = Path.of(programData.trim(), APP_DIRECTORY);
        } else {
            directory = Path.of(userHome, ".accountk");
        }
        return new Location(directory.toAbsolutePath().normalize(), false);
    }

    static Location locateForRead(Location preferred, Path workingDirectory) {
        if (Files.isRegularFile(preferred.configFile())) {
            return preferred;
        }
        Location legacy = new Location(workingDirectory.toAbsolutePath().normalize(), true);
        return Files.isRegularFile(legacy.configFile()) ? legacy : preferred;
    }

    public record Location(Path directory, Path configFile, Path keyFile, boolean legacy) {
        Location(Path directory, boolean legacy) {
            this(directory, directory.resolve("config.xml"), directory.resolve("config.key"), legacy);
        }
    }
}
