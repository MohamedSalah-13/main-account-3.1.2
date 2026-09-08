package com.hamza.account.features.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.regex.Pattern;

/** Validates, probes and writes one workstation's encrypted database configuration. */
public final class DatabaseSetupService {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]+");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_]+" );
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DatabaseConnectionProbe probe;
    private final Clock clock;

    public DatabaseSetupService(DatabaseConnectionProbe probe) {
        this(probe, Clock.systemDefaultZone());
    }

    DatabaseSetupService(DatabaseConnectionProbe probe, Clock clock) {
        this.probe = probe;
        this.clock = clock;
    }

    public DatabaseConnectionSettings validate(String host, String port, String database,
                                               String username, String password)
            throws DatabaseSetupException {
        String cleanHost = clean(host);
        String cleanDatabase = clean(database);
        String cleanUsername = clean(username);
        if (cleanHost.isEmpty() || !HOST.matcher(cleanHost).matches()) {
            throw new DatabaseSetupException("dbsetup.validation.host");
        }

        int parsedPort;
        try {
            parsedPort = Integer.parseInt(clean(port));
        } catch (NumberFormatException invalid) {
            throw new DatabaseSetupException("dbsetup.validation.port");
        }
        if (parsedPort < 1 || parsedPort > 65535) {
            throw new DatabaseSetupException("dbsetup.validation.port");
        }
        if (cleanDatabase.isEmpty() || !DATABASE.matcher(cleanDatabase).matches()) {
            throw new DatabaseSetupException("dbsetup.validation.database");
        }
        if (cleanUsername.isEmpty()) {
            throw new DatabaseSetupException("dbsetup.validation.username");
        }
        if (password == null || password.isEmpty()) {
            throw new DatabaseSetupException("dbsetup.validation.password");
        }
        return new DatabaseConnectionSettings(cleanHost, parsedPort, cleanDatabase, cleanUsername, password);
    }

    public DatabaseProbeResult test(DatabaseConnectionSettings settings) throws DatabaseSetupException {
        try {
            return probe.test(settings);
        } catch (java.sql.SQLException unavailable) {
            String state = unavailable.getSQLState();
            String key = state != null && state.startsWith("28")
                    ? "dbsetup.test.authentication.failed" : "dbsetup.test.connection.failed";
            throw new DatabaseSetupException(key, unavailable);
        }
    }

    public DatabaseConfigFiles.Location save(DatabaseConnectionSettings settings)
            throws DatabaseSetupException {
        return save(settings, DatabaseConfigFiles.preferred());
    }

    DatabaseConfigFiles.Location save(DatabaseConnectionSettings settings, DatabaseConfigFiles.Location target)
            throws DatabaseSetupException {
        Path directory = target.directory();
        Path config = target.configFile();
        Path key = target.keyFile();
        Path stagedConfig = directory.resolve("config.xml.new");
        Path stagedKey = directory.resolve("config.key.new");
        boolean hadConfig = Files.isRegularFile(config);
        boolean hadKey = Files.isRegularFile(key);
        Path configBackup = null;
        Path keyBackup = null;

        try {
            Files.createDirectories(directory);
            String environmentKey = System.getenv(CryptoDatabaseConfig.KEY_ENV_VAR);
            CryptoDatabaseConfig crypto = environmentKey == null || environmentKey.isBlank()
                    ? new CryptoDatabaseConfig()
                    : new CryptoDatabaseConfig(environmentKey.trim());
            Files.writeString(stagedKey, crypto.getSecretKeyBase64(), StandardCharsets.UTF_8);
            crypto.saveEncryptedConfigToXML(stagedConfig.toString(), settings.jdbcUrl(true),
                    settings.database(), settings.host(), settings.username(), settings.password(),
                    Integer.toString(settings.port()), "com.mysql.cj.jdbc.Driver");
            verify(stagedConfig, stagedKey, settings);

            String stamp = BACKUP_STAMP.format(LocalDateTime.now(clock));
            if (hadConfig) {
                configBackup = copyBackup(config, stamp);
            }
            if (hadKey) {
                keyBackup = copyBackup(key, stamp);
            }

            replace(stagedKey, key);
            replace(stagedConfig, config);
            return target;
        } catch (Exception failure) {
            restore(config, configBackup, hadConfig);
            restore(key, keyBackup, hadKey);
            throw new DatabaseSetupException("dbsetup.save.failed", failure);
        } finally {
            deleteQuietly(stagedConfig);
            deleteQuietly(stagedKey);
        }
    }

    private static void verify(Path config, Path key, DatabaseConnectionSettings expected) throws Exception {
        String installedKey = Files.readString(key, StandardCharsets.UTF_8).trim();
        HashMap<String, String> values = new CryptoDatabaseConfig(installedKey)
                .loadAndDecryptConfig(config.toString());
        if (!expected.host().equals(values.get(CryptoDatabaseConfig.HOST))
                || !expected.database().equals(values.get(CryptoDatabaseConfig.DBNAME))
                || !expected.username().equals(values.get(CryptoDatabaseConfig.USERNAME))) {
            throw new IllegalStateException("staged database configuration did not verify");
        }
    }

    private static Path copyBackup(Path source, String stamp) throws IOException {
        Path backup = source.resolveSibling(source.getFileName() + ".backup-" + stamp);
        return Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restore(Path target, Path backup, boolean existed) {
        try {
            if (backup != null && Files.isRegularFile(backup)) {
                Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            } else if (!existed) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // Preserve the original exception. The backup path stays on disk for manual recovery.
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A staged file is harmless and must not hide the actual save result.
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
