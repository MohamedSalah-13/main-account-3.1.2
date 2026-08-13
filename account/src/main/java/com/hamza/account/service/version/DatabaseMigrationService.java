package com.hamza.account.service.version;

import com.hamza.account.config.ConnectionToDatabase;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Brings a client database up to the schema this build expects, on every start.
 *
 * <p>The migrations themselves live in {@code db/migration} and are run by Flyway. What this class
 * adds around it is the three things Flyway cannot know about this application:
 *
 * <ol>
 *   <li>the database may not exist yet on a brand-new machine (Flyway needs to connect to it);</li>
 *   <li>an existing client database has never been touched by Flyway, so it has to be baselined -
 *       and baselining the wrong schema would silently skip real changes, so the shape is checked
 *       first;</li>
 *   <li>a client database is real data, so it gets dumped before anything is applied to it.</li>
 * </ol>
 *
 * <p>Concurrency is Flyway's problem, not ours: it locks the history table for the duration, so two
 * machines starting the app against one server cannot both apply the same migration.
 */
@Log4j2
public class DatabaseMigrationService {

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";

    /**
     * The version {@code V1__baseline.sql} carries. An existing client database is stamped with it
     * rather than having it executed, because the database already <em>is</em> that schema.
     */
    private static final String BASELINE_VERSION = "1";
    private static final String RBAC_MIGRATION_VERSION = "11";

    /**
     * Tables every v4.1.3 install has. A non-empty database missing any of them is not the schema
     * V1 describes, so baselining it would mark V1 as applied over a schema that never had it.
     */
    private static final List<String> BASELINE_MARKER_TABLES =
            List.of("items", "users", "custom", "suppliers", "total_sales", "total_buy", "treasury");

    private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("[A-Za-z0-9_$]+");

    private final ConnectionToDatabase database;
    private final SystemInfoService systemInfoService;
    private final DatabaseBackupService backupService;

    public DatabaseMigrationService() {
        this(new ConnectionToDatabase());
    }

    public DatabaseMigrationService(ConnectionToDatabase database) {
        this.database = database;
        this.systemInfoService = new SystemInfoService();
        this.backupService = new DatabaseBackupService();
    }

    public MigrationResult updateDatabaseIfNeeded() {
        createDatabaseIfMissing();

        // Emptiness has to be sampled before anything writes to the schema - Flyway treats a
        // non-empty schema with no history table as "baseline me", so creating so much as the
        // system_info table first would make a genuinely new database skip V1.
        boolean freshInstall = isEmptyDatabase();

        if (!freshInstall) {
            verifyLooksLikeBaseline();
        }

        Flyway flyway = buildFlyway();
        recoverFailedRbacMigration(flyway);

        List<MigrationInfo> pending = Arrays.asList(flyway.info().pending());

        if (!freshInstall && pending.isEmpty()) {
            String current = currentVersion(flyway);
            systemInfoService.createSystemTablesIfNotExists();
            systemInfoService.updateDatabaseVersion(current);
            return MigrationResult.noUpdateRequired(current, current);
        }

        if (!freshInstall) {
            // Only a database with data in it is worth dumping, and only when something is
            // actually about to be applied to it.
            backupService.createBackup();
        }

        MigrateResult result;
        try {
            result = flyway.migrate();
        } catch (Exception e) {
            log.error("Database migration failed", e);
            throw new RuntimeException("Database migration failed: " + e.getMessage(), e);
        }

        List<String> executedVersions = result.getSuccessfulMigrations().stream()
                .map(migration -> migration.version)
                .collect(Collectors.toList());

        String versionAfter = currentVersion(flyway);

        // Flyway reports where it actually started from, which on a client being adopted is the
        // baseline it just stamped. Asking before the migration would say "0", since the history
        // table did not exist yet - and the user would be told they upgraded from nothing.
        String versionBefore = result.initialSchemaVersion == null
                ? BASELINE_VERSION
                : result.initialSchemaVersion;

        systemInfoService.createSystemTablesIfNotExists();
        systemInfoService.updateDatabaseVersion(versionAfter);

        log.info("Flyway applied {} migration(s): {}", result.migrationsExecuted, executedVersions);

        return freshInstall
                ? MigrationResult.freshInstall(versionAfter, executedVersions)
                : MigrationResult.updated(versionBefore, versionAfter, executedVersions);
    }

    /**
     * The highest migration version this build carries, for the "about" screen.
     *
     * <p>Flyway can only enumerate migrations with a connection open, so this needs a reachable
     * database even though the answer comes from the shipped files. It is only ever called from a
     * running application, where that holds - but it reports rather than throws if it does not,
     * because a version label is not worth failing a screen over.
     */
    public String getLatestAvailableDatabaseVersion() {
        try {
            return Arrays.stream(buildFlyway().info().all())
                    .filter(info -> info.getVersion() != null)
                    .map(info -> info.getVersion().getVersion())
                    .reduce((first, second) -> second)
                    .orElse("0");
        } catch (Exception e) {
            log.warn("Could not read the available schema version", e);
            return "unknown";
        }
    }

    private Flyway buildFlyway() {
        return Flyway.configure()
                .dataSource(jdbcUrl(database.getDbName()), database.getUsername(), database.getPass())
                .locations(MIGRATIONS_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion(BASELINE_VERSION)
                .baselineDescription("Schema as shipped to clients in v4.1.3")
                // Client databases have been edited by hand over the years; a checksum mismatch on
                // a migration that already ran should not stop the app from opening.
                .validateOnMigrate(false)
                .cleanDisabled(true)
                .load();
    }

    private String currentVersion(Flyway flyway) {
        MigrationInfo current = flyway.info().current();
        return current == null || current.getVersion() == null ? "0" : current.getVersion().getVersion();
    }

    /**
     * V11 originally used MariaDB's {@code ADD COLUMN IF NOT EXISTS} syntax. MySQL records that
     * first-statement syntax failure in its non-transactional Flyway history even though no schema
     * object was changed. Remove only that known-safe failed row so the corrected migration can run.
     * Any partial V11 schema, or any other failed migration, is refused for manual investigation.
     */
    private void recoverFailedRbacMigration(Flyway flyway) {
        List<MigrationInfo> failed = Arrays.stream(flyway.info().all())
                .filter(info -> info.getState().isFailed())
                .toList();
        if (failed.isEmpty()) return;

        boolean onlyKnownFailure = failed.size() == 1
                && failed.getFirst().getVersion() != null
                && RBAC_MIGRATION_VERSION.equals(failed.getFirst().getVersion().getVersion());
        if (!onlyKnownFailure || hasRbacArtifacts()) {
            String versions = failed.stream()
                    .map(info -> info.getVersion() == null ? info.getScript() : info.getVersion().getVersion())
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Database contains a failed migration that cannot be retried automatically: " + versions);
        }

        String sql = "DELETE FROM flyway_schema_history WHERE version = ? AND success = 0";
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(database.getDbName()), database.getUsername(), database.getPass());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, RBAC_MIGRATION_VERSION);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Failed V11 history row was not found for safe retry");
            }
            log.warn("Removed the failed V11 history row after confirming that no RBAC artifacts exist");
        } catch (Exception e) {
            log.error("Failed to prepare V11 for a safe retry", e);
            throw new RuntimeException("Failed to prepare database migration V11 for retry", e);
        }
    }

    private boolean hasRbacArtifacts() {
        if (countExistingTables(List.of("roles", "role_permission", "user_role", "rbac_audit_log")) > 0) {
            return true;
        }

        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'permission'
                  AND column_name IN ('category', 'sort_order')
                """;
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(database.getDbName()), database.getUsername(), database.getPass());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        } catch (Exception e) {
            log.error("Failed to inspect V11 migration artifacts", e);
            throw new RuntimeException("Failed to inspect V11 migration artifacts", e);
        }
    }

    /**
     * A first-ever install has an empty MySQL server, and Flyway cannot connect to a schema that
     * does not exist yet. Created with the same charset the old V001_tables.sql used.
     */
    private void createDatabaseIfMissing() {
        String databaseName = database.getDbName();

        if (databaseName == null || !SAFE_DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalStateException(
                    "Database name in config.xml is not a plain identifier: " + databaseName);
        }

        String sql = "CREATE DATABASE IF NOT EXISTS `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                .formatted(databaseName);

        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(""), database.getUsername(), database.getPass());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            log.error("Failed to create database {}", databaseName, e);
            throw new RuntimeException("Failed to create database " + databaseName, e);
        }
    }

    private boolean isEmptyDatabase() {
        return countExistingTables(List.of()) == 0;
    }

    /**
     * Guards the baseline. Stamping V1 over a database that is not the v4.1.3 schema would record
     * it as applied without applying it, and every table V1 creates would then be missing for good.
     */
    private void verifyLooksLikeBaseline() {
        int found = countExistingTables(BASELINE_MARKER_TABLES);

        if (found == BASELINE_MARKER_TABLES.size()) {
            return;
        }

        // Already under Flyway's control - the history table decides, not the marker tables.
        if (countExistingTables(List.of("flyway_schema_history")) == 1) {
            return;
        }

        throw new IllegalStateException(
                ("Database '%s' is not empty but does not look like the v4.1.3 schema: only %d of the %d "
                        + "expected core tables are present. Refusing to baseline it, because that would "
                        + "record the base schema as applied without creating it. Point config.xml at the "
                        + "right database, or start from an empty one.")
                        .formatted(database.getDbName(), found, BASELINE_MARKER_TABLES.size()));
    }

    /**
     * @param tableNames the tables to look for, or an empty list to count every table in the schema
     */
    private int countExistingTables(List<String> tableNames) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()";

        if (!tableNames.isEmpty()) {
            String inList = tableNames.stream()
                    .map(name -> "'" + name + "'")
                    .collect(Collectors.joining(", "));
            sql += " AND table_name IN (" + inList + ")";
        }

        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(database.getDbName()), database.getUsername(), database.getPass());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (Exception e) {
            log.error("Failed to inspect database state", e);
            throw new RuntimeException("Failed to inspect database state", e);
        }
    }

    /**
     * @param databaseName the schema to connect to, or an empty string to reach the server itself
     */
    private String jdbcUrl(String databaseName) {
        return "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
                .formatted(database.getHost(), database.getPort(), databaseName);
    }
}
