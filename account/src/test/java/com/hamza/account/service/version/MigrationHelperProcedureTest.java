package com.hamza.account.service.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A versioned migration may only call a helper procedure it defines itself.
 * <p>
 * <b>This rule cost two broken migrations in one day, neither of which a green build could see.</b>
 * MySQL has no {@code ADD COLUMN IF NOT EXISTS} or {@code CREATE INDEX IF NOT EXISTS}, so the
 * migrations here write their own helpers. What is easy to miss is that <b>none of them survives</b>:
 * <ul>
 *   <li>{@code add_index_if_missing} is created by {@code V1} at its line 311, used some eighty
 *       times, and <b>dropped by {@code V1} at its line 994</b>;</li>
 *   <li>{@code add_column_if_missing} is never created by a baseline at all - {@code V20},
 *       {@code V21}, {@code V22} and {@code V23} each define a local copy and drop it again.</li>
 * </ul>
 * So a new migration that calls either fails on <em>every</em> install, new and upgrading, at the
 * first line it reaches. The first drafts of {@code V55} and {@code V56} both did, and the second
 * was written minutes after the first had been found and fixed - which is the argument for a test
 * rather than a note.
 * <p>
 * It is the same rule {@code AuditProcedureMigrationTest} guards from the other side: a versioned
 * migration may not depend on anything a versioned migration before it does not leave behind.
 * {@code V1_1__audit_log_procedure.sql} is the scar from the repeatable-migration version of it.
 * <p>
 * This reads the files, so it runs on every build. What it cannot say is that a migration executes -
 * only migrating a scratch schema from nothing says that, and it should be done for any change here.
 */
class MigrationHelperProcedureTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /** A {@code CALL some_procedure(...)} in a migration. */
    private static final Pattern CALL = Pattern.compile("(?m)^\\s*CALL\\s+(\\w+)\\s*\\(");

    /** A {@code CREATE PROCEDURE name(...)} in the same file. */
    private static final Pattern CREATE = Pattern.compile("CREATE\\s+PROCEDURE\\s+(\\w+)\\s*\\(");

    /**
     * The one procedure a migration may call without defining it: {@code write_audit_log}, which is
     * created by {@code V1_1} for exactly this reason and kept by every install afterwards. That
     * migration exists because {@code V20} fired an audit trigger that called it while it lived only
     * in a repeatable - see {@code AuditProcedureMigrationTest}.
     */
    private static final java.util.Set<String> CREATED_AND_KEPT =
            java.util.Set.of("write_audit_log", "write_audit_admin_event");

    @Test
    @DisplayName("no migration calls a helper it does not define itself")
    void everyCalledHelperIsDefinedInTheSameFile() {
        var offenders = new TreeSet<String>();
        for (Map.Entry<String, String> migration : versionedMigrations().entrySet()) {
            String sql = withoutComments(migration.getValue());
            var defined = new ArrayList<String>();
            Matcher creates = CREATE.matcher(sql);
            while (creates.find()) {
                defined.add(creates.group(1));
            }
            Matcher calls = CALL.matcher(sql);
            while (calls.find()) {
                String called = calls.group(1);
                if (!defined.contains(called) && !CREATED_AND_KEPT.contains(called)) {
                    offenders.add(migration.getKey() + " calls " + called);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "A migration may only call a helper procedure it defines in the same file. "
                        + "add_index_if_missing is dropped by V1 at its own end, and "
                        + "add_column_if_missing is never created by a baseline at all - calling "
                        + "either fails on every install, and no green build can see it. Copy the "
                        + "helper in, as V21, V22, V23, V55 and V56 do, and drop it again at the "
                        + "end: " + offenders);
    }

    /**
     * And a helper a migration defines is dropped again before the file ends.
     * <p>
     * Not tidiness: a procedure left behind is one the <em>next</em> migration may call by accident
     * and find present on the machine it was written on, because a migration that ran earlier
     * happened to leave it - and absent on a customer's, because that customer was stamped past it.
     * That is how a migration comes to work for its author and fail in the field.
     */
    @Test
    @DisplayName("a helper a migration defines is dropped again before the file ends")
    void everyDefinedHelperIsDroppedAgain() {
        var offenders = new TreeSet<String>();
        for (Map.Entry<String, String> migration : versionedMigrations().entrySet()) {
            String sql = withoutComments(migration.getValue());
            Matcher creates = CREATE.matcher(sql);
            while (creates.find()) {
                String name = creates.group(1);
                if (CREATED_AND_KEPT.contains(name)) {
                    continue;
                }
                boolean droppedAfter = sql.indexOf("DROP PROCEDURE IF EXISTS " + name,
                        creates.end()) >= 0;
                if (!droppedAfter) {
                    offenders.add(migration.getKey() + " leaves " + name + " behind");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "A helper procedure must be dropped at the end of the migration that defines it, or "
                        + "the next migration may call it and find it present here and missing in "
                        + "the field: " + offenders);
    }

    /** The versioned migrations, by file name. Repeatables are a different rule and run after these. */
    private static Map<String, String> versionedMigrations() {
        var result = new LinkedHashMap<String, String>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            List<Path> sorted = files
                    .filter(path -> path.getFileName().toString().startsWith("V"))
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
            for (Path file : sorted) {
                result.put(file.getFileName().toString(), Files.readString(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + MIGRATIONS, e);
        }
        assertTrue(result.size() > 40, "the migrations were not found: " + result.size());
        return result;
    }

    /** Line comments only, which is all these files use - and what would otherwise match a CALL. */
    private static String withoutComments(String sql) {
        return sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (all, line) -> all + line + "\n");
    }
}
