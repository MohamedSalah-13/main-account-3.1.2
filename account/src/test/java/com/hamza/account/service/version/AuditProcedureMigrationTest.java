package com.hamza.account.service.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A repeatable migration cannot be a prerequisite of a versioned one.
 * <p>
 * Flyway runs every repeatable migration <em>after</em> every versioned one. So anything
 * a versioned migration can reach - directly, or through a trigger it fires - has to be
 * created by a versioned migration. {@code write_audit_log} was not: it lived only in
 * {@code R__procedures.sql}, while {@code V1} and {@code V2} create the eighteen audit
 * triggers that call it.
 * <p>
 * Nothing noticed for nineteen migrations, because none of them wrote to an audited
 * table. {@code V20} does - {@code UPDATE treasury SET opening_date} - and a brand new
 * database died there with "PROCEDURE write_audit_log does not exist", half-built.
 * Existing clients were never affected: they are stamped at {@code V1} and already hold
 * the procedure. It broke only a first install, which is the one case a developer never
 * runs twice, and it is why this was found by trying to build a scratch schema rather
 * than by any test.
 * <p>
 * The rule below is deliberately narrow and mechanical, because the general form - "no
 * versioned migration writes to a table whose triggers call something that does not exist
 * yet" - cannot be decided by reading the files. What can be decided is that the
 * procedure is created by a versioned migration at all, and early enough to be there when
 * {@code V20} runs.
 */
class AuditProcedureMigrationTest {

    private static final Path MIGRATIONS =
            Path.of("src", "main", "resources", "db", "migration");

    private static final String PROCEDURE = "write_audit_log";

    /** The migration that first wrote to an audited table, and so exposed the gap. */
    private static final String FIRST_AUDITED_WRITE = "V20__treasury_types.sql";

    @Test
    @DisplayName("a versioned migration creates the audit procedure, not only the repeatable")
    void aVersionedMigrationCreatesIt() {
        List<Path> creators = versioned()
                .filter(file -> read(file).contains("CREATE PROCEDURE " + PROCEDURE))
                .toList();

        assertFalse(creators.isEmpty(),
                PROCEDURE + " is created only by R__procedures.sql again. Flyway runs "
                        + "repeatables after every versioned migration, so on a fresh install "
                        + "the audit triggers V1 and V2 create would call a procedure that does "
                        + "not exist, and the first versioned write to an audited table would "
                        + "fail the whole install.");
        assertEquals(1, creators.size(),
                "more than one versioned migration creates " + PROCEDURE + ": " + creators);
    }

    @Test
    @DisplayName("it is created before the first versioned write to an audited table")
    void itIsCreatedEarlyEnough() {
        Path creator = versioned()
                .filter(file -> read(file).contains("CREATE PROCEDURE " + PROCEDURE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no versioned migration creates " + PROCEDURE));

        assertTrue(version(creator.getFileName().toString()) < version(FIRST_AUDITED_WRITE),
                "the audit procedure is created by " + creator.getFileName() + ", which runs "
                        + "after " + FIRST_AUDITED_WRITE + ". Flyway applies migrations in "
                        + "version order, so a fix numbered above the migration it is fixing "
                        + "fixes nothing - the fresh install still dies at " + FIRST_AUDITED_WRITE
                        + ".");
    }

    /**
     * The repeatable keeps its copy, and that copy stays the one to edit. This is not
     * duplication to remove: the versioned file is a snapshot of what a database needed at
     * its version, and the repeatable - running afterwards - always has the last word.
     */
    @Test
    @DisplayName("the repeatable still defines it, and so still has the last word")
    void theRepeatableStillOwnsTheDefinition() {
        assertTrue(read(MIGRATIONS.resolve("R__procedures.sql"))
                        .contains("CREATE PROCEDURE " + PROCEDURE),
                "R__procedures.sql no longer defines " + PROCEDURE + ". It is what keeps an "
                        + "existing client's definition current when the body changes; the "
                        + "versioned copy only ever runs once.");
    }

    /** {@code V1_1__x.sql} is version 1.1; compared as a number so 2 sorts below 10. */
    private static double version(String fileName) {
        String digits = fileName.substring(1, fileName.indexOf("__")).replace('_', '.');
        return Double.parseDouble(digits);
    }

    private static Stream<Path> versioned() {
        try (var files = Files.list(MIGRATIONS)) {
            return files.filter(file -> file.getFileName().toString().startsWith("V"))
                    .sorted(Comparator.comparingDouble(
                            file -> version(file.getFileName().toString())))
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list " + MIGRATIONS.toAbsolutePath(), e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path.toAbsolutePath(), e);
        }
    }
}
