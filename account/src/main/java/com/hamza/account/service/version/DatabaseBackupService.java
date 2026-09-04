package com.hamza.account.service.version;

import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.MysqlTools;
import com.hamza.account.config.PropertiesName;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Log4j2
public class DatabaseBackupService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ConnectionToDatabase database;

    public DatabaseBackupService() {
        this.database = new ConnectionToDatabase();
    }

    public Path createBackup() {
        try {
            Path backupDirectory = Path.of("backups", "database-updates");
            Files.createDirectories(backupDirectory);

            String fileName = "%s_before_update_%s.sql".formatted(
                    database.getDbName(),
                    LocalDateTime.now().format(FORMATTER)
            );

            Path backupFile = backupDirectory.resolve(fileName);

            String mysqlDumpCommand = MysqlTools.mysqldump();

            ProcessBuilder processBuilder = new ProcessBuilder(
                    mysqlDumpCommand,
                    "-h", database.getHost(),
                    "-P", database.getPort(),
                    "-u", database.getUsername(),
                    "--default-character-set=utf8mb4",
                    "--routines",
                    "--triggers",
                    "--single-transaction",
                    database.getDbName()
            );

            // The password goes through the environment, not the command line, where the
            // process list exposes it to anything else running on the machine.
            processBuilder.environment().put("MYSQL_PWD",
                    database.getPass() == null ? "" : database.getPass());
            processBuilder.redirectOutput(backupFile.toFile());
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IllegalStateException("Database backup failed. Exit code: " + exitCode);
            }

            log.info("Database backup created: {}", backupFile.toAbsolutePath());
            return backupFile;

        } catch (Exception e) {
            log.error("Failed to create database backup", e);
            throw new RuntimeException("Failed to create database backup before update", e);
        }
    }

}
