package com.hamza.account.backup;

import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.file.Files;
import java.security.spec.KeySpec;
import java.security.SecureRandom;

/**
 * Encrypts and decrypts a backup file with AES-GCM, keyed from the user's passphrase
 * through PBKDF2 with a fresh salt per file.
 * <p>
 * <b>Decryption does not use {@link CipherInputStream}, and that is the whole point of
 * this class's shape.</b> That stream catches {@link AEADBadTagException} internally and
 * answers {@code -1} instead of propagating it, so a decrypt built on it never fails a
 * tag check - it just stops early. This code used to be built on it while catching
 * {@code AEADBadTagException} around it, so the catch could not fire and the integrity
 * check the GCM tag exists for was not happening at all: a backup file that had been
 * altered or truncated decrypted "successfully" and went on to be restored over a live
 * database. A wrong password appeared to be caught only because the garbage it produced
 * failed the caller's separate "does this look like SQL" test.
 * <p>
 * {@link Cipher#doFinal()} is what verifies the tag, so it is called explicitly. Its
 * plaintext is written as it is produced, which means the output file exists before the
 * tag is known to be good - so it is deleted on any failure rather than left for a caller
 * to mistake for a decrypted backup.
 */
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

            // The salt and the IV go through the same buffer as the ciphertext that
            // follows them; writing them straight to fos while the cipher writes through
            // bos only happens to produce the right order because nothing is buffered yet.
            bos.write(salt);
            bos.write(iv);

            try (FileInputStream fis = new FileInputStream(inputFile);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = bis.read(buffer)) != -1) {
                    byte[] encrypted = cipher.update(buffer, 0, count);
                    if (encrypted != null) bos.write(encrypted);
                }
                byte[] last = cipher.doFinal();
                if (last != null) bos.write(last);
            }
        }
    }

    // فك تشفير ملف
    public static void decryptFile(File inputFile, File outputFile, String password) throws Exception {
        try {
            try (FileInputStream fis = new FileInputStream(inputFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 FileOutputStream fos = new FileOutputStream(outputFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] salt = bis.readNBytes(SALT_SIZE);
                if (salt.length != SALT_SIZE) {
                    throw new IOException(LanguageManager.getInstance().getString("backup.error.file.corrupt.salt"));
                }

                byte[] iv = bis.readNBytes(IV_SIZE);
                if (iv.length != IV_SIZE) {
                    throw new IOException(LanguageManager.getInstance().getString("backup.error.file.corrupt.iv"));
                }

                SecretKey key = getKeyFromPassword(password, salt);
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, iv));

                byte[] buffer = new byte[8192];
                int count;
                while ((count = bis.read(buffer)) != -1) {
                    byte[] plain = cipher.update(buffer, 0, count);
                    if (plain != null) bos.write(plain);
                }
                // Verifies the tag over everything read. A wrong password, an altered
                // byte or a truncated file all fail here, and only here.
                byte[] last = cipher.doFinal();
                if (last != null) bos.write(last);
            }
        } catch (AEADBadTagException e) {
            Files.deleteIfExists(outputFile.toPath());
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("backup.error.wrong.password.or.corrupt"), e);
        } catch (Exception e) {
            // Whatever went wrong, what is on disk is a partial decrypt and must not be
            // left looking like a usable one.
            Files.deleteIfExists(outputFile.toPath());
            throw e;
        }
    }
}
