package com.hamza.controlsfx.file.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;

public class CryptoDatabaseFile {

    public static void encryptFile(File file) throws Exception {
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file.getPath());
        }
        byte[] fileContent = Files.readAllBytes(file.toPath());
        SecretKey key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest("BackupEncryptionKey".getBytes()), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedContent = cipher.doFinal(fileContent);
        Files.write(file.toPath(), Base64.getEncoder().encode(encryptedContent));
    }

    public static Path decryptFile(File file) throws Exception {
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file.getPath());
        }

        byte[] fileContent = Files.readAllBytes(file.toPath());
        // Check if the content looks like Base64
        if (!isBase64(fileContent)) {
            throw new IllegalArgumentException("File does not appear to be encrypted (invalid Base64 content)");
        }

        try {
            SecretKey key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest("BackupEncryptionKey".getBytes()), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decryptedContent = cipher.doFinal(Base64.getDecoder().decode(fileContent));
            return Files.write(file.toPath(), decryptedContent);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to decrypt file: Invalid Base64 encoding", e);
        }
    }

    private static boolean isBase64(byte[] content) {
        String str = new String(content);
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 2) {
                System.err.println("Usage: java CryptoDatabaseFile <encrypt|decrypt> <file> ");
                return;
            }
            File file = new File(args[1]);
            if (!file.exists()) {
                System.err.println("File does not exist: " + file.getPath());
                return;
            }

            if (args[0].equals("decrypt")) {
                decryptFile(file);
                System.out.println("File decrypted successfully");
            } else if (args[0].equals("encrypt")) {
                encryptFile(file);
                System.out.println("File encrypted successfully");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
