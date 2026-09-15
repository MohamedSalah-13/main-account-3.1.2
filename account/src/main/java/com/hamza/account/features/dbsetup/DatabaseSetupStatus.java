package com.hamza.account.features.dbsetup;

import java.util.List;

/**
 * What the setup screen says after a successful operation: one severity and the
 * sentences to show, as message keys with their arguments.
 *
 * <p>Kept out of the controller so the choice can be tested without a JavaFX toolkit. It
 * is more than a lookup now that one run can produce two facts at once - the account's
 * password and whether the server will let that account create triggers - and the second
 * must never be hidden behind a green first.
 */
public record DatabaseSetupStatus(Severity severity, List<Sentence> sentences) {

    public enum Severity { SUCCESS, WARNING }

    public record Sentence(String key, List<Object> arguments) {
        static Sentence of(String key, Object... arguments) {
            return new Sentence(key, List.of(arguments));
        }
    }

    public DatabaseSetupStatus {
        sentences = List.copyOf(sentences);
    }

    public static DatabaseSetupStatus afterConnectionTest(DatabaseProbeResult result) {
        if (result.storedPrograms().blocksTriggers()) {
            Sentence blocked = Sentence.of("dbsetup.test.success.stored.programs.blocked");
            // Both facts, not the louder one: that the program will create the schema on its
            // first start is exactly what a technician looking at a missing database needs.
            return new DatabaseSetupStatus(Severity.WARNING, result.databaseExists()
                    ? List.of(blocked)
                    : List.of(Sentence.of("dbsetup.test.success.database.missing"), blocked));
        }
        return result.databaseExists()
                ? new DatabaseSetupStatus(Severity.SUCCESS, List.of(Sentence.of("dbsetup.test.success")))
                : new DatabaseSetupStatus(Severity.WARNING,
                        List.of(Sentence.of("dbsetup.test.success.database.missing")));
    }

    public static DatabaseSetupStatus afterProvisioning(DatabaseServerProvisioningResult result) {
        Sentence password = switch (result.password()) {
            case CREATED -> Sentence.of("dbsetup.provision.success", result.database(), result.account());
            case RESET -> Sentence.of("dbsetup.provision.success.password.reset",
                    result.database(), result.account());
            case UNCHANGED -> Sentence.of("dbsetup.provision.success.password.unchanged",
                    result.database(), result.account());
        };
        boolean warning = result.password() == DatabaseServerProvisioningResult.PasswordOutcome.UNCHANGED;

        return switch (result.storedPrograms()) {
            case NOT_REQUIRED, ALREADY_ALLOWED ->
                    new DatabaseSetupStatus(warning ? Severity.WARNING : Severity.SUCCESS, List.of(password));
            case ENABLED -> new DatabaseSetupStatus(warning ? Severity.WARNING : Severity.SUCCESS,
                    List.of(password, Sentence.of("dbsetup.provision.stored.programs.enabled")));
            case BLOCKED -> new DatabaseSetupStatus(Severity.WARNING,
                    List.of(password, Sentence.of("dbsetup.provision.stored.programs.blocked")));
            case DECLINED -> new DatabaseSetupStatus(Severity.WARNING,
                    List.of(password, Sentence.of("dbsetup.provision.stored.programs.declined")));
        };
    }
}
