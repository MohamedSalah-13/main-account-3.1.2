package com.hamza.account.config;


import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

@Log4j2
public class Configs {

    //TODO 11/16/2025 9:14 AM Mohamed: check in realtime
    public static final boolean IS_DOWNLOAD_TASK = true;
    public static final boolean ADD_PACKAGE_TO_ITEMS = true ; // تستخدم فى إضافة المجموعات ام لا
    public static final File FILE_REPORTS = new File("reports/");
    public static final String BACKUP_DATA_PATH = System.getProperty("user.dir") + "/backup_data";


    public static File getSecretKeyFile(String fileName) {
        String userHome = System.getProperty("user.home");
        File secretDir = new File(userHome, ".myapp");
        if (!secretDir.exists()) {
            secretDir.mkdirs();
            setSecurePermissions(secretDir);
        }
        return new File(secretDir, fileName);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void setSecurePermissions(File file) {
        try {
            // Set permissions to read/write for owner only
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                file.setReadable(true, true);
                file.setWritable(true, true);
                file.setExecutable(false);
            } else {
                Files.setPosixFilePermissions(file.toPath(),
                        PosixFilePermissions.fromString("rw-------"));
            }
        } catch (IOException e) {
            log.error("Failed to set file permissions", e);
        }
    }

}
