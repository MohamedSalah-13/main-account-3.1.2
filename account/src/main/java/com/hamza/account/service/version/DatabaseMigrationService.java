package com.hamza.account.service.version;

import com.hamza.account.config.ConnectionToDatabase;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
public class DatabaseMigrationService {

    private static final String MIGRATIONS_PATH = "/db/migrations";
    private static final String GENESIS_RESOURCE_PATH = MIGRATIONS_PATH + "/V000_genesis_baseline.sql";
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("(?i)^DELIMITER\\s+(\\S+)$");

    private final ConnectionToDatabase database;
    private final SystemInfoService systemInfoService;
    private final DatabaseBackupService backupService;

    public DatabaseMigrationService() {
        this.database = new ConnectionToDatabase();
        this.systemInfoService = new SystemInfoService();
        this.backupService = new DatabaseBackupService();
    }

    public MigrationResult updateDatabaseIfNeeded() {
        systemInfoService.createSystemTablesIfNotExists();

        if (isFreshInstall()) {
            return runFreshInstall();
        }

        String currentDatabaseVersion = systemInfoService.getCurrentDatabaseVersion();
        String requiredDatabaseVersion = getLatestAvailableDatabaseVersion();

        List<DatabaseMigration> pendingMigrations = getPendingMigrations(
                currentDatabaseVersion,
                requiredDatabaseVersion
        );

        if (pendingMigrations.isEmpty()) {
            return MigrationResult.noUpdateRequired(currentDatabaseVersion, requiredDatabaseVersion);
        }

        backupService.createBackup();

        List<String> executedVersions = new ArrayList<>();

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                for (DatabaseMigration migration : pendingMigrations) {
                    executeMigration(connection, migration);
                    registerMigration(connection, migration);
                    systemInfoService.updateDatabaseVersion(migration.getVersion());
                    executedVersions.add(migration.getVersion());
                }

                connection.commit();

                return MigrationResult.updated(
                        currentDatabaseVersion,
                        requiredDatabaseVersion,
                        executedVersions
                );

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            log.error("Database migration failed", e);
            throw new RuntimeException("Database migration failed", e);
        }
    }

    /**
     * The migration engine used to gate execution on a `db.required.version` value that was
     * maintained by hand in version.properties and routinely drifted out of sync with the
     * migrations actually registered below (some shipped for months without ever running).
     * The required version is now derived directly from this list, so it can never drift.
     */
    public String getLatestAvailableDatabaseVersion() {
        return getAvailableMigrations().stream()
                .map(DatabaseMigration::getVersion)
                .max(DatabaseMigration::compareVersions)
                .orElse("0.0.0");
    }

    /**
     * A brand-new client database (no `items` table yet) can't run the incremental ALTER-based
     * migrations below — several of them assume older, pre-migration column shapes that were
     * never created. For a verified-empty database we instead run one consolidated baseline
     * script that builds the full current schema directly, then mark migration history as
     * fully applied (the standard "baseline" approach used by migration tools).
     */
    private boolean isFreshInstall() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'items'")) {
            resultSet.next();
            return resultSet.getInt(1) == 0;
        } catch (Exception e) {
            log.error("Failed to check database state", e);
            throw new RuntimeException("Failed to check database state", e);
        }
    }

    private MigrationResult runFreshInstall() {
        DatabaseMigration genesis = DatabaseMigration.builder()
                .version("0.0.1")
                .description("Genesis: base schema for a new/empty database")
                .resourcePath(GENESIS_RESOURCE_PATH)
                .build();

        List<String> executedVersions = new ArrayList<>();

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                executeMigration(connection, genesis);
                registerMigration(connection, genesis);
                executedVersions.add(genesis.getVersion());

                for (DatabaseMigration migration : getAvailableMigrations()) {
                    registerMigration(connection, migration);
                    executedVersions.add(migration.getVersion());
                }

                connection.commit();

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }

        } catch (Exception e) {
            log.error("Fresh database initialization failed", e);
            throw new RuntimeException("Fresh database initialization failed", e);
        }

        String latestVersion = getLatestAvailableDatabaseVersion();
        systemInfoService.updateDatabaseVersion(latestVersion);

        return MigrationResult.freshInstall(latestVersion, executedVersions);
    }

    private List<DatabaseMigration> getPendingMigrations(String currentVersion, String requiredVersion) {
        return getAvailableMigrations().stream()
                .filter(migration -> DatabaseMigration.compareVersions(migration.getVersion(), currentVersion) > 0)
                .filter(migration -> DatabaseMigration.compareVersions(migration.getVersion(), requiredVersion) <= 0)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<DatabaseMigration> getAvailableMigrations() {
        /*
         * ملاحظة:
         * Java لا يستطيع دائمًا قراءة أسماء الملفات داخل resources بعد التغليف jar بسهولة.
         * لذلك نضع هنا قائمة التحديثات صراحة.
         * عند إضافة ملف SQL جديد، أضفه هنا فقط - هذه هي القائمة الوحيدة التي يعتمد عليها
         * كل من: تحديد الإصدار المطلوب، وترتيب التنفيذ، وشاشة "عن البرنامج".
         */
        List<DatabaseMigration> migrations = new ArrayList<>();

//        migrations.add(DatabaseMigration.builder()
//                .version("4.2.0.1")
//                .description("Add item_barcodes table for multiple barcodes per item")
//                .resourcePath(MIGRATIONS_PATH + "/V4_2_0_1_item_barcodes.sql")
//                .build());


        /*
         * مثال عند إضافة تحديث جديد:
         *
         * migrations.add(DatabaseMigration.builder()
         *         .version("4.1.1")
         *         .description("Add new customer fields")
         *         .resourcePath(MIGRATIONS_PATH + "/V4_1_1__add_new_customer_fields.sql")
         *         .build());
         *
         * ملحوظة: لو الميزة الجديدة بتضيف جدول جديد بالكامل (مش تعديل جدول موجود)،
         * ضيف نفس تعريف الجدول (CREATE TABLE IF NOT EXISTS) لملف
         * V000_genesis_baseline.sql كمان، عشان العميل الجديد (قاعدة بيانات فاضية)
         * ياخد الجدول ده من أول تشغيل بدل ما يستناه.
         */

        return migrations.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private void executeMigration(Connection connection, DatabaseMigration migration) throws Exception {
        String sqlScript = readResourceFile(migration.getResourcePath());

        List<String> statements = splitSqlStatements(sqlScript);

        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }

        log.info("Executed database migration: {}", migration.getVersion());
    }

    private void registerMigration(Connection connection, DatabaseMigration migration) throws Exception {
        String sql = """
                INSERT INTO database_migrations (
                    version,
                    description,
                    executed_at
                ) VALUES (
                    '%s',
                    '%s',
                    NOW()
                )
                ON DUPLICATE KEY UPDATE
                    description = VALUES(description)
                """.formatted(
                escape(migration.getVersion()),
                escape(migration.getDescription())
        );

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private String readResourceFile(String resourcePath) throws Exception {
        try (InputStream inputStream = DatabaseMigrationService.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Migration file not found: " + resourcePath);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            )) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Splits a SQL script into individually-executable statements, honoring MySQL CLI-style
     * `DELIMITER` directives (used by the stored-procedure-based migration scripts to define
     * procedure bodies containing internal semicolons). JDBC has no notion of `DELIMITER` - it's
     * a client-side convention - so the directive itself is stripped out and never sent to the
     * server; only the statement text between delimiters is executed.
     */
    private List<String> splitSqlStatements(String sqlScript) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        String delimiter = ";";

        String[] lines = sqlScript.split("\\R");

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("--") || trimmedLine.startsWith("#") || trimmedLine.isBlank()) {
                continue;
            }

            Matcher delimiterMatcher = DELIMITER_PATTERN.matcher(trimmedLine);
            if (delimiterMatcher.matches()) {
                delimiter = delimiterMatcher.group(1);
                continue;
            }

            currentStatement.append(line).append("\n");

            if (trimmedLine.endsWith(delimiter)) {
                String statement = currentStatement.toString()
                        .replaceFirst(Pattern.quote(delimiter) + "\\s*$", "");
                statements.add(statement);
                currentStatement.setLength(0);
            }
        }

        if (!currentStatement.toString().isBlank()) {
            statements.add(currentStatement.toString());
        }

        return statements;
    }

    private Connection getConnection() throws Exception {
        String url = "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowMultiQueries=true"
                .formatted(database.getHost(), database.getPort(), database.getDbName());

        return DriverManager.getConnection(url, database.getUsername(), database.getPass());
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
