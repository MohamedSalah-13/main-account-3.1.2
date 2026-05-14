package com.hamza.account.service.version;

import com.hamza.account.config.AppVersionInfo;
import com.hamza.account.config.ConnectionToDatabase;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class DatabaseMigrationService {

    private static final String MIGRATIONS_PATH = "/db/migrations";

    private final ConnectionToDatabase database;
    private final AppVersionInfo appVersionInfo;
    private final SystemInfoService systemInfoService;
    private final DatabaseBackupService backupService;

    public DatabaseMigrationService() {
        this.database = new ConnectionToDatabase();
        this.appVersionInfo = new AppVersionInfo();
        this.systemInfoService = new SystemInfoService();
        this.backupService = new DatabaseBackupService();
    }

    public MigrationResult updateDatabaseIfNeeded() {
        systemInfoService.createSystemTablesIfNotExists();

        String currentDatabaseVersion = systemInfoService.getCurrentDatabaseVersion();
        String requiredDatabaseVersion = appVersionInfo.getRequiredDatabaseVersion();

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
         * عند إضافة ملف SQL جديد، أضفه هنا.
         */
        List<DatabaseMigration> migrations = new ArrayList<>();

        migrations.add(DatabaseMigration.builder()
                .version("4.1.0.1")
                .description("Create/update foreign keys")
                .resourcePath(MIGRATIONS_PATH + "/V4_1_0_1_forrienKey.sql")
                .build());

        migrations.add(DatabaseMigration.builder()
                .version("4.1.0.2")
                .description("Update database schema")
                .resourcePath(MIGRATIONS_PATH + "/V4_1_0_2_update_database.sql")
                .build());

        migrations.add(DatabaseMigration.builder()
                .version("4.1.0.3")
                .description("Create audit log")
                .resourcePath(MIGRATIONS_PATH + "/V4_1_0_3_audit_log.sql")
                .build());

        migrations.add(DatabaseMigration.builder()
                .version("4.1.0.4")
                .description("Create company triggers")
                .resourcePath(MIGRATIONS_PATH + "/V4_1_0_4_trigger_company.sql")
                .build());

        migrations.add(DatabaseMigration.builder()
                .version("4.1.0.5")
                .description("Create items triggers")
                .resourcePath(MIGRATIONS_PATH + "/V4_1_0_5_trigger_items.sql")
                .build());

        migrations.add(DatabaseMigration.builder()
                .version("4.1.0.6")
                .description("Update user truncate logic")
                .resourcePath(MIGRATIONS_PATH + "/V4_1_0_6_user_truncate.sql")
                .build());

        migrations.add(DatabaseMigration.builder()
                .version("4.1.0.7")
                .description("Create/update views")
                .resourcePath(MIGRATIONS_PATH + "/V4_1_0_7_view_table.sql")
                .build());


        /*
         * مثال عند إضافة تحديث جديد:
         *
         * migrations.add(DatabaseMigration.builder()
         *         .version("4.1.1")
         *         .description("Add new customer fields")
         *         .resourcePath(MIGRATIONS_PATH + "/V4_1_1__add_new_customer_fields.sql")
         *         .build());
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

    private List<String> splitSqlStatements(String sqlScript) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        String[] lines = sqlScript.split("\\R");

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("--") || trimmedLine.startsWith("#") || trimmedLine.isBlank()) {
                continue;
            }

            currentStatement.append(line).append("\n");

            if (trimmedLine.endsWith(";")) {
                statements.add(currentStatement.toString().replaceFirst(";\\s*$", ""));
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
