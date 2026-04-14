package com.hamza.controlsfx.util.crypto;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class FileDB {


    @SuppressWarnings("ResultOfMethodCallIgnored")
    public File getFileDB() {
        // file destination and create
        File destination = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
        if (!destination.exists()) {
            destination.mkdirs();
        }

        // database
        String key = "Mary has one cat";
        File encryptedFile = new File("dbCon.enc");
        File decryptedFile = new File(destination + "/dbCon.dec");

        try {
            CryptoUtils.decrypt(key, encryptedFile, decryptedFile);
            deleteFile(decryptedFile);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return decryptedFile;
    }

    private void deleteFile(File s) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(10000);
                FileUtils.deleteDirectory(s.getParentFile());
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();

    }

    @SuppressWarnings("deprecation")
    private void hideFile(File file) throws IOException {
        Runtime.getRuntime().exec("attrib +H " + file.getAbsolutePath());
    }

}
