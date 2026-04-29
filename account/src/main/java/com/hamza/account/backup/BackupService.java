package com.hamza.account.backup;

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
        // إنشاء اسم فريد للملف المؤقت والمشفر
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File tempSqlFile = File.createTempFile("backup_" + timestamp, ".sql");
        File encryptedFile = new File(backupDir, "backup_" + timestamp + ".enc");

        try {
            // 1. تنفيذ mysqldump مع فصل stderr
            runMysqldump(tempSqlFile);

            // 2. تشفير الملف الناتج (باستخدام المفتاح الثابت)
            EncryptionUtil.encryptFile(tempSqlFile, encryptedFile,encryptionPassword);

            return encryptedFile;
        } finally {
            // حذف الملف المؤقت بعد التشفير
            Files.deleteIfExists(tempSqlFile.toPath());
        }
    }

    /**
     * تشغيل mysqldump وكتابة stdout في الملف المحدد،
     * بينما يتم استهلاك stderr في خيط منفصل لعدم تضخم المخزن المؤقت.
     */
    private void runMysqldump(File outputFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                mysqlDumpPath,
                "-h", dbHost,
                "-P", dbPort,
                "-u", dbUser,
                "--password=" + dbPassword,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--set-gtid-purged=OFF",   // <-- منع تضمين GTID_PURGED
                dbName
        );
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
            throw new RuntimeException("فشل mysqldump مع رمز الخروج: " + exitCode);
        }
    }

    // استعادة نسخة احتياطية من ملف مشفر
    public void restoreFromFile(File encryptedBackup, String encryptionPassword) throws Exception {
        File tempSqlFile = File.createTempFile("restore_", ".sql");
        try {
            // 1. فك التشفير
            EncryptionUtil.decryptFile(encryptedBackup, tempSqlFile,encryptionPassword);

            // 2. التحقق من صلاحية الملف
            if (!isSqlFile(tempSqlFile)) {
                throw new Exception("الملف المفكوك ليس SQL صالحاً. قد يكون الملف تالفاً أو أن كلمة المرور خاطئة.");
            }

            // 3. تنفيذ الاستيراد مع تمرير الملف النظيف
            ProcessBuilder pb = new ProcessBuilder(
                    mysqlPath,
                    "-h", dbHost,
                    "-P", dbPort,
                    "-u", dbUser,
                    "--password=" + dbPassword,
                    dbName
            );
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
                throw new RuntimeException("فشل استيراد SQL (رمز الخروج " + exitCode + "): " + errorMsg.toString());
            }

            log.info("تمت الاستعادة بنجاح من: " + encryptedBackup.getName());
        } finally {
            Files.deleteIfExists(tempSqlFile.toPath());
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
