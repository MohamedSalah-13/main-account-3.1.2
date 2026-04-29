package com.hamza.account.backup;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

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
            // تنفيذ mysqldump
            ProcessBuilder pb = new ProcessBuilder(
                    mysqlDumpPath,
                    "-h", dbHost,
                    "-P", dbPort,
                    "-u", dbUser,
                    "--password=" + dbPassword,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    dbName
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // قراءة مخرجات dump وحفظها كملف SQL مؤقت
            try (InputStream is = process.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tempSqlFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("فشل mysqldump مع رمز الخروج: " + exitCode);
            }

            // تشفير الملف المؤقت
            EncryptionUtil.encryptFile(tempSqlFile, encryptedFile, encryptionPassword);
            return encryptedFile;
        } finally {
            // حذف الملف المؤقت
            Files.deleteIfExists(tempSqlFile.toPath());
        }
    }

    // استعادة نسخة احتياطية من ملف مشفر
    public void restoreFromFile(File encryptedBackup, String encryptionPassword) throws Exception {
        File tempSqlFile = File.createTempFile("restore_", ".sql");
        try {
            EncryptionUtil.decryptFile(encryptedBackup, tempSqlFile, encryptionPassword);

            // التحقق من أن الملف SQL صالح
            if (!isSqlFile(tempSqlFile)) {
                throw new Exception("ملف النسخة الاحتياطية غير صالح بعد فك التشفير. تأكد من كلمة المرور وسلامة الملف.");
            }

            // تنفيذ الاستيراد ...
        } finally {
            Files.deleteIfExists(tempSqlFile.toPath());
        }
    }

    private boolean isSqlFile(File file) throws IOException {
        // اقرأ أول 5 كيلوبايت وافحص وجود بداية جمل SQL شائعة
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 20) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("--") && !line.startsWith("/*")) {
                    // إذا وجدنا كلمة أساسية SQL مبكراً فهو SQL غالباً
                    if (line.toUpperCase().matches(".*\\b(CREATE|INSERT|ALTER|DROP|SET|USE)\\b.*")) {
                        return true;
                    }
                }
                linesRead++;
            }
        }
        return false;
    }
}
