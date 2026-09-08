package com.hamza.account.features.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSetupServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T12:34:56Z"), ZoneOffset.UTC);

    @TempDir
    Path temporary;

    @Test
    void validatesAndNormalizesTheEnteredValues() throws Exception {
        var service = new DatabaseSetupService(settings -> new DatabaseProbeResult(true), CLOCK);

        DatabaseConnectionSettings result = service.validate(
                " 192.168.1.10 ", "3306", " account_system_db ", " appuser ", "secret");

        assertEquals("192.168.1.10", result.host());
        assertEquals(3306, result.port());
        assertEquals("account_system_db", result.database());
        assertEquals("appuser", result.username());
        assertFalse(result.toString().contains("secret"));
    }

    @Test
    void rejectsInvalidPortBeforeAnyConnectionIsAttempted() {
        var service = new DatabaseSetupService(settings -> new DatabaseProbeResult(true), CLOCK);

        DatabaseSetupException failure = assertThrows(DatabaseSetupException.class,
                () -> service.validate("localhost", "70000", "accounts", "appuser", "secret"));

        assertEquals("dbsetup.validation.port", failure.messageKey());
    }

    @Test
    void distinguishesAuthenticationFailures() {
        var service = new DatabaseSetupService(settings -> {
            throw new SQLException("denied", "28000");
        }, CLOCK);
        var settings = settings();

        DatabaseSetupException failure = assertThrows(DatabaseSetupException.class,
                () -> service.test(settings));

        assertEquals("dbsetup.test.authentication.failed", failure.messageKey());
    }

    @Test
    void writesAKeyAndAnAuthenticatedEncryptedConfig() throws Exception {
        var service = new DatabaseSetupService(settings -> new DatabaseProbeResult(true), CLOCK);
        var target = DatabaseConfigFiles.at(temporary.resolve("AccountK"));

        service.save(settings(), target);

        assertTrue(Files.isRegularFile(target.configFile()));
        assertTrue(Files.isRegularFile(target.keyFile()));
        String key = Files.readString(target.keyFile(), StandardCharsets.UTF_8).trim();
        var values = new CryptoDatabaseConfig(key).loadAndDecryptConfig(target.configFile().toString());
        assertEquals("localhost", values.get(CryptoDatabaseConfig.HOST));
        assertEquals("accounts", values.get(CryptoDatabaseConfig.DBNAME));
        assertEquals("appuser", values.get(CryptoDatabaseConfig.USERNAME));
        assertEquals("secret", values.get(CryptoDatabaseConfig.PASSWORD));
        assertTrue(Files.readString(target.configFile()).contains("v2:"));
    }

    @Test
    void preservesTimestampedBackupsWhenReplacingAnExistingPair() throws Exception {
        var service = new DatabaseSetupService(settings -> new DatabaseProbeResult(true), CLOCK);
        var target = DatabaseConfigFiles.at(temporary.resolve("AccountK"));
        Files.createDirectories(target.directory());
        Files.writeString(target.configFile(), "old config");
        Files.writeString(target.keyFile(), "old key");

        service.save(settings(), target);

        assertEquals("old config", Files.readString(target.directory()
                .resolve("config.xml.backup-20260908-123456")));
        assertEquals("old key", Files.readString(target.directory()
                .resolve("config.key.backup-20260908-123456")));
    }

    private static DatabaseConnectionSettings settings() {
        return new DatabaseConnectionSettings("localhost", 3306, "accounts", "appuser", "secret");
    }
}
