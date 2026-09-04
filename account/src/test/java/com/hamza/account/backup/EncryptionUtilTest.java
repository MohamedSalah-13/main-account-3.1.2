package com.hamza.account.backup;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The integrity half of these tests is the reason they exist.
 * <p>
 * Decryption was built on {@code CipherInputStream}, which swallows
 * {@code AEADBadTagException} and answers {@code -1} instead - so the GCM tag was never
 * actually checked, while the code around it caught that exception and reported "wrong
 * password or corrupt file" as though it were. An altered backup decrypted quietly and
 * went on to be restored over a live database. Nothing failed, which is exactly why
 * nobody could notice.
 */
class EncryptionUtilTest {

    private static final String PASSWORD = "a-passphrase";

    private static File write(Path dir, String name, byte[] content) throws Exception {
        File file = dir.resolve(name).toFile();
        Files.write(file.toPath(), content);
        return file;
    }

    private static byte[] sampleDump() {
        StringBuilder sql = new StringBuilder("CREATE TABLE items (id INT);\n");
        for (int row = 0; row < 500; row++) {
            sql.append("INSERT INTO items VALUES (").append(row).append(");\n");
        }
        return sql.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        void whatGoesInComesBackOut(@TempDir Path dir) throws Exception {
            byte[] original = sampleDump();
            File plain = write(dir, "dump.sql", original);
            File encrypted = dir.resolve("dump.enc").toFile();
            File restored = dir.resolve("restored.sql").toFile();

            EncryptionUtil.encryptFile(plain, encrypted, PASSWORD);
            EncryptionUtil.decryptFile(encrypted, restored, PASSWORD);

            assertArrayEquals(original, Files.readAllBytes(restored.toPath()));
        }

        @Test
        @DisplayName("two encryptions of the same file differ - the salt and IV are fresh")
        void everyFileGetsItsOwnSaltAndIv(@TempDir Path dir) throws Exception {
            File plain = write(dir, "dump.sql", sampleDump());
            File first = dir.resolve("first.enc").toFile();
            File second = dir.resolve("second.enc").toFile();

            EncryptionUtil.encryptFile(plain, first, PASSWORD);
            EncryptionUtil.encryptFile(plain, second, PASSWORD);

            assertFalse(java.util.Arrays.equals(
                    Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath())));
        }
    }

    @Nested
    @DisplayName("integrity")
    class Integrity {

        @Test
        @DisplayName("a single altered byte is refused")
        void tamperingIsCaught(@TempDir Path dir) throws Exception {
            File plain = write(dir, "dump.sql", sampleDump());
            File encrypted = dir.resolve("dump.enc").toFile();
            EncryptionUtil.encryptFile(plain, encrypted, PASSWORD);

            byte[] bytes = Files.readAllBytes(encrypted.toPath());
            int middle = bytes.length / 2;
            bytes[middle] = (byte) (bytes[middle] ^ 0xFF);
            Files.write(encrypted.toPath(), bytes);

            File restored = dir.resolve("restored.sql").toFile();
            assertThrows(UserValidationException.class,
                    () -> EncryptionUtil.decryptFile(encrypted, restored, PASSWORD));
        }

        @Test
        @DisplayName("a truncated file is refused, not restored as far as it goes")
        void truncationIsCaught(@TempDir Path dir) throws Exception {
            File plain = write(dir, "dump.sql", sampleDump());
            File encrypted = dir.resolve("dump.enc").toFile();
            EncryptionUtil.encryptFile(plain, encrypted, PASSWORD);

            byte[] bytes = Files.readAllBytes(encrypted.toPath());
            Files.write(encrypted.toPath(), java.util.Arrays.copyOf(bytes, bytes.length - 64));

            File restored = dir.resolve("restored.sql").toFile();
            assertThrows(UserValidationException.class,
                    () -> EncryptionUtil.decryptFile(encrypted, restored, PASSWORD));
        }

        @Test
        void aWrongPasswordIsRefused(@TempDir Path dir) throws Exception {
            File plain = write(dir, "dump.sql", sampleDump());
            File encrypted = dir.resolve("dump.enc").toFile();
            EncryptionUtil.encryptFile(plain, encrypted, PASSWORD);

            File restored = dir.resolve("restored.sql").toFile();
            assertThrows(UserValidationException.class,
                    () -> EncryptionUtil.decryptFile(encrypted, restored, "not-the-passphrase"));
        }

        @Test
        @DisplayName("a refused decrypt leaves no half-decrypted file behind to be mistaken for one")
        void theOutputIsRemovedOnFailure(@TempDir Path dir) throws Exception {
            File plain = write(dir, "dump.sql", sampleDump());
            File encrypted = dir.resolve("dump.enc").toFile();
            EncryptionUtil.encryptFile(plain, encrypted, PASSWORD);

            File restored = dir.resolve("restored.sql").toFile();
            assertThrows(UserValidationException.class,
                    () -> EncryptionUtil.decryptFile(encrypted, restored, "not-the-passphrase"));

            assertFalse(restored.exists());
        }

        @Test
        @DisplayName("a file too short to hold a salt and an IV is refused")
        void aTinyFileIsRefused(@TempDir Path dir) throws Exception {
            File encrypted = write(dir, "tiny.enc", new byte[8]);
            File restored = dir.resolve("restored.sql").toFile();

            assertThrows(Exception.class,
                    () -> EncryptionUtil.decryptFile(encrypted, restored, PASSWORD));
        }
    }
}
