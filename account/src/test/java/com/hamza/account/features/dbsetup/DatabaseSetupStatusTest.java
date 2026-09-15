package com.hamza.account.features.dbsetup;

import com.hamza.account.features.dbsetup.DatabaseServerProvisioningResult.PasswordOutcome;
import com.hamza.account.features.dbsetup.DatabaseSetupStatus.Sentence;
import com.hamza.account.features.dbsetup.DatabaseSetupStatus.Severity;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseSetupStatusTest {

    @Test
    void theBinaryLogOnlyBlocksTriggersWhileCreatorsAreUntrusted() {
        assertEquals(StoredProgramLogging.NOT_REQUIRED, StoredProgramLogging.of(false, false));
        assertEquals(StoredProgramLogging.ALREADY_ALLOWED, StoredProgramLogging.of(true, true));
        assertEquals(StoredProgramLogging.BLOCKED, StoredProgramLogging.of(true, false));
        assertTrue(StoredProgramLogging.BLOCKED.blocksTriggers());
        assertFalse(StoredProgramLogging.ENABLED.blocksTriggers());
    }

    /** A green "connected" over a server that will refuse the first CREATE TRIGGER is the defect. */
    @Test
    void aConnectionTestWarnsWhenTheServerWillRefuseTriggersEvenIfTheDatabaseExists() {
        var status = DatabaseSetupStatus.afterConnectionTest(
                new DatabaseProbeResult(true, StoredProgramLogging.BLOCKED));

        assertEquals(Severity.WARNING, status.severity());
        assertEquals(List.of("dbsetup.test.success.stored.programs.blocked"), keys(status));
    }

    /** The missing database is still news: the program creates it on first start, once triggers are allowed. */
    @Test
    void aConnectionTestKeepsTheMissingDatabaseBesideTheTriggerWarning() {
        var status = DatabaseSetupStatus.afterConnectionTest(
                new DatabaseProbeResult(false, StoredProgramLogging.BLOCKED));

        assertEquals(Severity.WARNING, status.severity());
        assertEquals(List.of("dbsetup.test.success.database.missing",
                "dbsetup.test.success.stored.programs.blocked"), keys(status));
    }

    @Test
    void aConnectionTestIsGreenOnlyWhenTheDatabaseExistsAndTriggersAreAllowed() {
        assertEquals(Severity.SUCCESS, DatabaseSetupStatus.afterConnectionTest(
                new DatabaseProbeResult(true, StoredProgramLogging.ALREADY_ALLOWED)).severity());
        var missing = DatabaseSetupStatus.afterConnectionTest(
                new DatabaseProbeResult(false, StoredProgramLogging.NOT_REQUIRED));
        assertEquals(Severity.WARNING, missing.severity());
        assertEquals(List.of("dbsetup.test.success.database.missing"), keys(missing));
    }

    @Test
    void provisioningSaysWhenItEnabledTheSettingOnTheServer() {
        var status = DatabaseSetupStatus.afterProvisioning(
                result(PasswordOutcome.CREATED, StoredProgramLogging.ENABLED));

        assertEquals(Severity.SUCCESS, status.severity());
        assertEquals(List.of("dbsetup.provision.success", "dbsetup.provision.stored.programs.enabled"),
                keys(status));
        assertEquals(List.of("accounts", "'app'@'localhost'"), status.sentences().get(0).arguments());
    }

    @Test
    void aBlockedServerTurnsASuccessfulProvisioningIntoAWarning() {
        var status = DatabaseSetupStatus.afterProvisioning(
                result(PasswordOutcome.CREATED, StoredProgramLogging.BLOCKED));

        assertEquals(Severity.WARNING, status.severity());
        assertEquals(List.of("dbsetup.provision.success", "dbsetup.provision.stored.programs.blocked"),
                keys(status));
    }

    @Test
    void anUnchangedPasswordStaysAWarningAndNeedsNoSecondSentenceWhenNothingIsBlocked() {
        var status = DatabaseSetupStatus.afterProvisioning(
                result(PasswordOutcome.UNCHANGED, StoredProgramLogging.ALREADY_ALLOWED));

        assertEquals(Severity.WARNING, status.severity());
        assertEquals(List.of("dbsetup.provision.success.password.unchanged"), keys(status));
    }

    @Test
    void theProvisionerPersistsTheSettingOnlyWhenTheServerBlocksTriggers() throws Exception {
        Statement statement = mock(Statement.class);
        Connection connection = connectionAnswering(statement, false, true);

        assertEquals(StoredProgramLogging.ENABLED,
                JdbcDatabaseServerProvisioner.allowStoredPrograms(connection, true));
        verify(statement).execute(StoredProgramLogging.ENABLE_SQL);

        Statement untouched = mock(Statement.class);
        assertEquals(StoredProgramLogging.ALREADY_ALLOWED,
                JdbcDatabaseServerProvisioner.allowStoredPrograms(connectionAnswering(untouched, true), true));
        verify(untouched, never()).execute(anyString());
    }

    /** Unticked, the server is read but never changed, and the answer says whose decision it was. */
    @Test
    void anUntickedAllowChangesNothingOnTheServerAndIsReportedAsDeclined() throws Exception {
        Statement statement = mock(Statement.class);

        assertEquals(StoredProgramLogging.DECLINED,
                JdbcDatabaseServerProvisioner.allowStoredPrograms(connectionAnswering(statement, false), false));
        verify(statement, never()).execute(anyString());
        assertTrue(StoredProgramLogging.DECLINED.blocksTriggers());

        var status = DatabaseSetupStatus.afterProvisioning(
                result(PasswordOutcome.CREATED, StoredProgramLogging.DECLINED));
        assertEquals(Severity.WARNING, status.severity());
        assertEquals(List.of("dbsetup.provision.success", "dbsetup.provision.stored.programs.declined"),
                keys(status));
    }

    /** An administrator without SYSTEM_VARIABLES_ADMIN must not fail the whole run. */
    @Test
    void anAdministratorWhoCannotChangeTheSettingIsReportedAsBlocked() throws Exception {
        Statement statement = mock(Statement.class);
        Connection connection = connectionAnswering(statement, false);
        when(statement.execute(StoredProgramLogging.ENABLE_SQL))
                .thenThrow(new SQLException("Access denied; you need SYSTEM_VARIABLES_ADMIN", "42000", 1227));

        assertEquals(StoredProgramLogging.BLOCKED,
                JdbcDatabaseServerProvisioner.allowStoredPrograms(connection, true));
    }

    @Test
    void everySentenceTheScreenCanShowIsTranslatedAndFormatsWithItsArguments() throws Exception {
        List<DatabaseSetupStatus> all = new ArrayList<>();
        for (StoredProgramLogging logging : StoredProgramLogging.values()) {
            all.add(DatabaseSetupStatus.afterConnectionTest(new DatabaseProbeResult(true, logging)));
            all.add(DatabaseSetupStatus.afterConnectionTest(new DatabaseProbeResult(false, logging)));
            for (PasswordOutcome password : PasswordOutcome.values()) {
                all.add(DatabaseSetupStatus.afterProvisioning(result(password, logging)));
            }
        }

        for (String bundle : List.of("messages.properties", "messages_ar.properties", "messages_en.properties")) {
            Properties messages = new Properties();
            try (Reader reader = Files.newBufferedReader(
                    Path.of("../controlsfx/src/main/resources/i18n").resolve(bundle), StandardCharsets.UTF_8)) {
                messages.load(reader);
            }
            for (DatabaseSetupStatus status : all) {
                for (Sentence sentence : status.sentences()) {
                    String message = messages.getProperty(sentence.key());
                    assertTrue(message != null, sentence.key() + " is missing from " + bundle);
                    String formatted = String.format(message, sentence.arguments().toArray());
                    assertFalse(formatted.contains("%s"), sentence.key() + " in " + bundle + " left a placeholder");
                }
            }
        }
    }

    private static Connection connectionAnswering(Statement statement, boolean... trustAnswers) throws Exception {
        Connection connection = mock(Connection.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(StoredProgramLogging.READ_SQL)).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(true);
        Boolean first = trustAnswers[0];
        Boolean[] rest = new Boolean[trustAnswers.length - 1];
        for (int i = 1; i < trustAnswers.length; i++) {
            rest[i - 1] = trustAnswers[i];
        }
        when(result.getBoolean(2)).thenReturn(first, rest);
        return connection;
    }

    private static DatabaseServerProvisioningResult result(PasswordOutcome password, StoredProgramLogging logging) {
        return new DatabaseServerProvisioningResult("accounts", "'app'@'localhost'", password, logging);
    }

    private static List<String> keys(DatabaseSetupStatus status) {
        return status.sentences().stream().map(Sentence::key).toList();
    }
}
