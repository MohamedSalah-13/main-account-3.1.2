package com.hamza.account.backup;

import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

@Log4j2
public class BackupService {
    private String mysqlDumpPath = "mysqldump"; // أو المسار الكامل
    private String mysqlPath = "mysql";
    private String dbHost, dbPort, dbName, dbUser, dbPassword;
    private String encryptionPassword; // كلمة مرور التشفير (تختلف عن كلمة مرور MySQL)

    public BackupService(String dbHost, String dbPort, String dbName,
                         String dbUser, String dbPassword, String encryptionPassword) {
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.encryptionPassword = encryptionPassword;
    }

    // إجراء نسخ احتياطي كامل إلى ملف مشفر
    public File backupToFile(File backupDir) throws Exception {
        // The encryption password defaults to empty when none was ever set. Restore
        // already refuses an empty password, so a backup taken with one is a file
        // that cannot be restored through the application - and its contents are
        // readable by anyone, since the key comes from an empty passphrase. Better
        // to say so now than to hand back a file that looks like a backup.
        if (encryptionPassword == null || encryptionPassword.isBlank()) {
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("backup.error.no.encryption.password"));
        }

        File encryptedFile = new File(backupDir, "backup_" + timestamp() + ".enc");
        return dumpAndEncrypt(encryptedFile, encryptionPassword);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    /** Dumps the database and writes it encrypted to {@code target}, leaving no plaintext behind. */
    private File dumpAndEncrypt(File target, String password) throws Exception {
        File tempSqlFile = File.createTempFile("backup_", ".sql");
        try {
            runMysqldump(tempSqlFile);
            EncryptionUtil.encryptFile(tempSqlFile, target, password);
            return target;
        } finally {
            Files.deleteIfExists(tempSqlFile.toPath());
        }
    }

    /**
     * تشغيل mysqldump وكتابة stdout في الملف المحدد،
     * بينما يتم استهلاك stderr في خيط منفصل لعدم تضخم المخزن المؤقت.
     */
    /**
     * Hands the database password to the child through {@code MYSQL_PWD} rather than
     * {@code --password=} on the command line, where every other process on the machine
     * can read it out of the process list. This repository has been here before: the same
     * fix was made to {@code scripts/main/RunAllSqlScripts.bat} and never reached the
     * code that runs the same tools.
     */
    private void passwordThroughEnvironment(ProcessBuilder pb) {
        pb.environment().put("MYSQL_PWD", dbPassword == null ? "" : dbPassword);
    }

    private void runMysqldump(File outputFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                mysqlDumpPath,
                "-h", dbHost,
                "-P", dbPort,
                "-u", dbUser,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--set-gtid-purged=OFF",   // <-- منع تضمين GTID_PURGED
                dbName
        );
        passwordThroughEnvironment(pb);
        // لا تدمج stderr مع stdout – يبقى كل تيار مستقلاً
        pb.redirectError(ProcessBuilder.Redirect.PIPE);

        Process process = pb.start();

        // استهلاك stderr في خيط جانبي (يمكنك تسجيله أو التخلص منه)
        new Thread(() -> {
            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    // يمكن توجيه التحذيرات إلى log أو System.err
                    log.warn("[mysqldump-warning] " + line);
                }
            } catch (IOException ignored) {}
        }).start();

        // كتابة stdout (مخرجات SQL النقية) إلى الملف المؤقت
        try (InputStream stdout = process.getInputStream();
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = stdout.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                    LanguageManager.getInstance().getString("backup.error.mysqldump.failed", exitCode));
        }
    }

    /**
     * Replaces the live database with the contents of an encrypted backup.
     * <p>
     * A safety copy of what is about to be replaced is taken first, and a failure to take
     * it stops the restore. The import runs {@code DROP TABLE} / {@code CREATE TABLE}
     * over the live schema, so a run that fails halfway leaves a database with neither
     * its old contents nor a complete new set - and until this copy existed there was
     * nothing at all to go back to. {@code DatabaseMigrationService} has taken the same
     * precaution before every migration since it was written; the more dangerous of the
     * two operations was the one without it.
     * <p>
     * The copy is encrypted with the password that has just been proved to open this
     * backup, so it is an ordinary backup file the same screen can restore - and no
     * plaintext dump of the customer's data is left sitting in the folder.
     */
    public void restoreFromFile(File encryptedBackup, String encryptionPassword) throws Exception {
        File tempSqlFile = File.createTempFile("restore_", ".sql");
        try {
            // 1. فك التشفير
            EncryptionUtil.decryptFile(encryptedBackup, tempSqlFile,encryptionPassword);

            // 2. التحقق من صلاحية الملف
            if (!isSqlFile(tempSqlFile)) {
                throw new UserValidationException(
                        LanguageManager.getInstance().getString("backup.error.invalid.file.or.password"));
            }

            // 3. نسخة أمان لما سيُستبدل، قبل لمس قاعدة البيانات
            File safetyCopy = takeSafetyCopy(encryptedBackup, encryptionPassword);

            // 4. تنفيذ الاستيراد مع تمرير الملف النظيف
            ProcessBuilder pb = new ProcessBuilder(
                    mysqlPath,
                    "-h", dbHost,
                    "-P", dbPort,
                    "-u", dbUser,
                    dbName
            );
            passwordThroughEnvironment(pb);
            pb.redirectErrorStream(true);         // ندمج stderr للاستيراد لنعرف الخطأ
            pb.redirectInput(tempSqlFile);        // الملف النظيف الآن

            Process process = pb.start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                StringBuilder errorMsg = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorMsg.append(line).append("\n");
                    }
                }
                throw new UserValidationException(
                        LanguageManager.getInstance().getString("backup.error.sql.import.failed.safety",
                                exitCode, safetyCopy.getName(), errorMsg.toString()));
            }

            log.info("تمت الاستعادة بنجاح من: " + encryptedBackup.getName());
        } finally {
            Files.deleteIfExists(tempSqlFile.toPath());
        }
    }

    /**
     * The database as it stands, written beside the backup being restored. Refuses the
     * restore if it cannot be taken: proceeding would mean overwriting data with no way
     * back, which is the one outcome this whole method exists to prevent.
     */
    private File takeSafetyCopy(File encryptedBackup, String password) throws Exception {
        File folder = encryptedBackup.getParentFile();
        File target = new File(folder == null ? new File(".") : folder,
                "before-restore_" + timestamp() + ".enc");
        try {
            File copy = dumpAndEncrypt(target, password);
            log.info("Safety copy taken before restore: {}", copy.getName());
            return copy;
        } catch (Exception e) {
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("backup.error.safety.copy.failed"), e);
        }
    }

    private boolean isSqlFile(File file) throws IOException {
        byte[] head = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(head);
            if (read <= 0) return false;
            String content = new String(head, 0, read, StandardCharsets.UTF_8);
            // ابحث عن جمل SQL واضحة
            return content.contains("CREATE TABLE") ||
                    content.contains("INSERT INTO") ||
                    content.contains("ALTER TABLE") ||
                    content.contains("DROP TABLE") ||
                    content.contains("SET ");
        }
    }
}
