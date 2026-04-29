package com.hamza.account.backup;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.security.spec.KeySpec;
import java.security.SecureRandom;

public class EncryptionUtil {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 65536;
    private static final int SALT_SIZE = 16;
    private static final int IV_SIZE = 12; // GCM recommended IV length
    private static final int TAG_BIT_LENGTH = 128;

    // توليد المفتاح من كلمة المرور
    private static SecretKey getKeyFromPassword(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
    }

    // تشفير ملف وإرجاع ملف مشفر
    public static void encryptFile(File inputFile, File outputFile, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_SIZE];
        random.nextBytes(salt);
        SecretKey key = getKeyFromPassword(password, salt);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV(); // GCM يولّد IV تلقائياً

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            // كتابة الـ Salt والـ IV في بداية الملف
            fos.write(salt);
            fos.write(iv);

            // الكتابة المشفرة
            try (FileInputStream fis = new FileInputStream(inputFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 CipherOutputStream cos = new CipherOutputStream(bos, cipher)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = bis.read(buffer)) != -1) {
                    cos.write(buffer, 0, count);
                }
            }
        }
    }

    // فك تشفير ملف
    public static void decryptFile(File inputFile, File outputFile, String password) throws Exception {
        try (FileInputStream fis = new FileInputStream(inputFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] salt = new byte[SALT_SIZE];
            if (bis.read(salt) != SALT_SIZE) {
                throw new IOException("الملف تالف: لا يحتوي على بيانات الملح الكافية.");
            }

            byte[] iv = new byte[IV_SIZE];
            if (bis.read(iv) != IV_SIZE) {
                throw new IOException("الملف تالف: لا يحتوي على IV صحيح.");
            }

            SecretKey key = getKeyFromPassword(password, salt);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            try (CipherInputStream cis = new CipherInputStream(bis, cipher)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, count);
                }
            }
            // إذا وصلنا هنا بنجاح فالتشفير سليم
        } catch (AEADBadTagException e) {
            throw new Exception("كلمة مرور خاطئة أو الملف المشفر تالف (فشل التحقق من سلامة البيانات).", e);
        }
    }
}