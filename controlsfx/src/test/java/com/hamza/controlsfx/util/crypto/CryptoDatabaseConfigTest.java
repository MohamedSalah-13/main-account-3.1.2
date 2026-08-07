package com.hamza.controlsfx.util.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class CryptoDatabaseConfigTest {

    /** An arbitrary 256-bit key; these tests never touch the real one. */
    private static final String KEY = "Xw4+b2QIwkqGJqZhUHgXxbZYxhsHLx5J0QbfrjERHPw=";
    private static final String OTHER_KEY = "bm90LXRoZS1zYW1lLWtleS0xMjM0NTY3ODkwMTI=";

    private static CryptoDatabaseConfig crypto(String key) throws Exception {
        return new CryptoDatabaseConfig(key);
    }

    @Nested
    @DisplayName("value encryption")
    class Values {

        @Test
        void decryptUndoesEncrypt() throws Exception {
            CryptoDatabaseConfig crypto = crypto(KEY);
            String secret = "p@ssw0rd with spaces and عربي";

            assertEquals(secret, crypto.decrypt(crypto.encrypt(secret)));
        }

        @Test
        @DisplayName("the same value encrypts differently each time - the IV is random")
        void encryptionIsNotDeterministic() throws Exception {
            CryptoDatabaseConfig crypto = crypto(KEY);

            assertNotEquals(crypto.encrypt("localhost"), crypto.encrypt("localhost"));
        }

        @Test
        void writesTheAuthenticatedFormat() throws Exception {
            assertTrue(crypto(KEY).encrypt("localhost").startsWith("v2:"));
        }

        @Test
        @DisplayName("a modified value is rejected rather than decrypted to something else")
        void tamperingIsDetected() throws Exception {
            CryptoDatabaseConfig crypto = crypto(KEY);
            String encrypted = crypto.encrypt("localhost");

            // Flip a bit in the ciphertext, keeping it valid Base64.
            byte[] raw = Base64.getDecoder().decode(encrypted.substring("v2:".length()));
            raw[raw.length - 1] ^= 0x01;
            String tampered = "v2:" + Base64.getEncoder().encodeToString(raw);

            assertThrows(Exception.class, () -> crypto.decrypt(tampered));
        }

        @Test
        void anotherKeyCannotRead() throws Exception {
            String encrypted = crypto(KEY).encrypt("localhost");

            assertThrows(Exception.class, () -> crypto(OTHER_KEY).decrypt(encrypted));
        }

        @Test
        @DisplayName("values written before the move to GCM are still readable")
        void legacyFormatStillDecrypts() throws Exception {
            // What the old code produced: bare "AES", which the JDK resolves to ECB,
            // Base64 encoded and with no marker.
            Cipher ecb = Cipher.getInstance("AES");
            ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(KEY), "AES"));
            String legacy = Base64.getEncoder()
                    .encodeToString(ecb.doFinal("legacy-host".getBytes(StandardCharsets.UTF_8)));

            assertFalse(legacy.startsWith("v2:"));
            assertEquals("legacy-host", crypto(KEY).decrypt(legacy));
        }
    }

    @Nested
    @DisplayName("config.xml")
    class ConfigFile {

        private HashMap<String, String> writeAndRead(Path dir) throws Exception {
            File file = dir.resolve("config.xml").toFile();
            CryptoDatabaseConfig crypto = crypto(KEY);
            crypto.saveEncryptedConfigToXML(file.getAbsolutePath(),
                    "jdbc:mysql://localhost:3306/accounts", "accounts", "localhost",
                    "appuser", "s3cret", "3306", "com.mysql.cj.jdbc.Driver");
            return crypto.loadAndDecryptConfig(file.getAbsolutePath());
        }

        @Test
        void roundTripsEverySetting(@TempDir Path dir) throws Exception {
            HashMap<String, String> read = writeAndRead(dir);

            assertEquals("jdbc:mysql://localhost:3306/accounts", read.get(CryptoDatabaseConfig.URL));
            assertEquals("accounts", read.get(CryptoDatabaseConfig.DBNAME));
            assertEquals("localhost", read.get(CryptoDatabaseConfig.HOST));
            assertEquals("appuser", read.get(CryptoDatabaseConfig.USERNAME));
            assertEquals("s3cret", read.get(CryptoDatabaseConfig.PASSWORD));
            assertEquals("3306", read.get(CryptoDatabaseConfig.PORT));
            assertEquals("com.mysql.cj.jdbc.Driver", read.get(CryptoDatabaseConfig.DRIVER));
        }

        @Test
        @DisplayName("the key is never written beside the file it protects")
        void doesNotLeaveTheKeyOnDisk(@TempDir Path dir) throws Exception {
            writeAndRead(dir);

            assertFalse(Files.exists(dir.resolve("secret_key.txt")));
            String written = Files.readString(dir.resolve("config.xml"), StandardCharsets.UTF_8);
            assertFalse(written.contains(KEY));
        }

        @Test
        void namesAMissingSetting(@TempDir Path dir) throws Exception {
            CryptoDatabaseConfig crypto = crypto(KEY);
            // url has to be genuinely decryptable: it is read first, and a broken
            // value there would fail before the missing element is ever reached.
            Path file = dir.resolve("config.xml");
            Files.writeString(file, "<DatabaseConfig><url>" + crypto.encrypt("jdbc:x")
                    + "</url></DatabaseConfig>");

            Exception e = assertThrows(Exception.class, () -> crypto.loadAndDecryptConfig(file.toString()));
            assertTrue(e.getMessage().contains("dbname"), "should name the missing element: " + e.getMessage());
        }

        @Test
        void namesAnEmptySetting(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("config.xml");
            Files.writeString(file, """
                    <DatabaseConfig><url></url><dbname>x</dbname><host>x</host><username>x</username>\
                    <password>x</password><port>x</port><driver>x</driver></DatabaseConfig>""");

            Exception e = assertThrows(Exception.class,
                    () -> crypto(KEY).loadAndDecryptConfig(file.toString()));
            assertTrue(e.getMessage().contains("url"), "should name the empty element: " + e.getMessage());
        }
    }

    /**
     * The key file is created by redirecting genkey into it, and a Windows shell
     * writes UTF-16LE or UTF-8 with a byte order mark when it does. Reading it as
     * plain UTF-8 rejected the file the instructions produced.
     */
    @Nested
    @DisplayName("config.key encoding")
    class KeyFile {

        private File keyFileContaining(Path dir, byte[] bytes) throws Exception {
            Path file = dir.resolve("config.key");
            Files.write(file, bytes);
            return file.toFile();
        }

        @Test
        void readsPlainUtf8(@TempDir Path dir) throws Exception {
            File file = keyFileContaining(dir, (KEY + "\n").getBytes(StandardCharsets.UTF_8));

            assertEquals(KEY, CryptoDatabaseConfig.readKeyFile(file));
        }

        @Test
        @DisplayName("UTF-16LE with a BOM, which is what PowerShell redirection writes")
        void readsUtf16LeWithBom(@TempDir Path dir) throws Exception {
            byte[] body = (KEY + "\r\n").getBytes(StandardCharsets.UTF_16LE);
            byte[] withBom = new byte[body.length + 2];
            withBom[0] = (byte) 0xFF;
            withBom[1] = (byte) 0xFE;
            System.arraycopy(body, 0, withBom, 2, body.length);

            assertEquals(KEY, CryptoDatabaseConfig.readKeyFile(keyFileContaining(dir, withBom)));
        }

        @Test
        void readsUtf16BeWithBom(@TempDir Path dir) throws Exception {
            byte[] body = (KEY + "\n").getBytes(StandardCharsets.UTF_16BE);
            byte[] withBom = new byte[body.length + 2];
            withBom[0] = (byte) 0xFE;
            withBom[1] = (byte) 0xFF;
            System.arraycopy(body, 0, withBom, 2, body.length);

            assertEquals(KEY, CryptoDatabaseConfig.readKeyFile(keyFileContaining(dir, withBom)));
        }

        @Test
        void readsUtf8WithBom(@TempDir Path dir) throws Exception {
            byte[] body = (KEY + "\n").getBytes(StandardCharsets.UTF_8);
            byte[] withBom = new byte[body.length + 3];
            withBom[0] = (byte) 0xEF;
            withBom[1] = (byte) 0xBB;
            withBom[2] = (byte) 0xBF;
            System.arraycopy(body, 0, withBom, 3, body.length);

            assertEquals(KEY, CryptoDatabaseConfig.readKeyFile(keyFileContaining(dir, withBom)));
        }

        @Test
        @DisplayName("a file that also caught surrounding output still yields the key")
        void usesTheFirstNonBlankLine(@TempDir Path dir) throws Exception {
            File file = keyFileContaining(dir,
                    ("\n\n" + KEY + "\n\nPut this in ACCOUNT_CONFIG_KEY, or in a file named config.key\n")
                            .getBytes(StandardCharsets.UTF_8));

            assertEquals(KEY, CryptoDatabaseConfig.readKeyFile(file));
        }

        @Test
        void anEmptyFileYieldsNoKey(@TempDir Path dir) throws Exception {
            assertEquals("", CryptoDatabaseConfig.readKeyFile(keyFileContaining(dir, "   \n\n".getBytes())));
        }
    }
}
