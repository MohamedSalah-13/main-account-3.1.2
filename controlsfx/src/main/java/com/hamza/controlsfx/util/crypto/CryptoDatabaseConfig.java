package com.hamza.controlsfx.util.crypto;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;

/**
 * Reads and writes the encrypted database settings in config.xml.
 *
 * <h2>Formats</h2>
 * Values written now carry a {@value #V2_PREFIX} marker and are AES/GCM, with a
 * fresh random IV per value and an authentication tag, so a modified file fails
 * to decrypt rather than yielding altered settings.
 * <p>
 * Values without that marker are the original format: AES with no mode
 * specified, which the JDK resolves to ECB, and no integrity check at all. They
 * are still readable so that installs already in the field keep starting, but
 * nothing writes them any more. Use {@code migrate} to convert a file.
 *
 * <h2>Keys</h2>
 * Reading falls back to {@link #FALLBACK_KEY} because every config.xml in the
 * field was encrypted with it. Writing does not: {@link #requireConfigKey}
 * demands a key of the install's own, because a file encrypted under a key that
 * ships in the source is readable by anyone holding the source.
 */
public class CryptoDatabaseConfig {
    public static final String HOST = "host";
    public static final String DATABASE_CONFIG = "DatabaseConfig";
    public static final String URL = "url";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String DRIVER = "driver";
    public static final String DBNAME = "dbname";
    public static final String PORT = "port";
    public static final String KEY = "key";
    private static final String ALGORITHM = "AES";

    /** Marks a value as the authenticated AES/GCM format. */
    private static final String V2_PREFIX = "v2:";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    /** Environment variable holding the Base64 AES key, checked first. */
    public static final String KEY_ENV_VAR = "ACCOUNT_CONFIG_KEY";
    /** Key file resolved against the working directory, like config.xml itself. */
    public static final String KEY_FILE = "config.key";
    /**
     * The key every config.xml already in the field was encrypted with. It is in
     * the source, so it is public knowledge to anyone with the repository, and any
     * file it protects should be treated as readable. It remains only so those
     * installs still start; it is never used to write.
     */
    private static final String FALLBACK_KEY = "nZdjCubzMZs+/RU1XDr/7g==";

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    /**
     * Mints a brand new random key, for handing to {@link #KEY_ENV_VAR} or
     * {@value #KEY_FILE}.
     */
    public CryptoDatabaseConfig() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(256);
        this.secretKey = keyGenerator.generateKey();
    }

    public CryptoDatabaseConfig(String base64Key) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
    }

    /**
     * The key used to read config.xml: the environment variable, then
     * {@value #KEY_FILE} in the working directory, then the built-in default so
     * that existing installs keep working.
     */
    public static String resolveConfigKey() {
        String explicit = explicitConfigKey();
        return explicit != null ? explicit : FALLBACK_KEY;
    }

    /**
     * The key used to write config.xml. Unlike {@link #resolveConfigKey} this
     * refuses to fall back to the built-in key, because writing a new file under a
     * key that is published in the source would hand its credentials to anyone
     * with the repository.
     */
    public static String requireConfigKey() {
        String explicit = explicitConfigKey();
        if (explicit == null) {
            throw new IllegalStateException("""
                    No encryption key of this install's own was found, and the built-in \
                    default must not be used to write a new config.xml - it is published \
                    in the source, so anyone with the repository could decrypt the result.

                    Generate one and install it, then run this command again:
                      java -cp controlsfx/target/classes \
                    com.hamza.controlsfx.util.crypto.CryptoDatabaseConfig genkey

                    Put the value in the %s environment variable, or in a file named %s \
                    in the directory the application starts from.""".formatted(KEY_ENV_VAR, KEY_FILE));
        }
        return explicit;
    }

    /** The key set for this install, or null when only the built-in default is available. */
    private static String explicitConfigKey() {
        String fromEnv = System.getenv(KEY_ENV_VAR);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return validateKey(fromEnv.trim(), "environment variable " + KEY_ENV_VAR);
        }

        File keyFile = new File(KEY_FILE);
        if (keyFile.isFile()) {
            String fromFile;
            try {
                fromFile = Files.readString(keyFile.toPath(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the AES key from " + keyFile.getAbsolutePath(), e);
            }
            if (!fromFile.isEmpty()) {
                return validateKey(fromFile, "key file " + keyFile.getAbsolutePath());
            }
        }

        return null;
    }

    /** Whether config.xml is being read with the key that ships in the source. */
    public static boolean usingBuiltInKey() {
        return explicitConfigKey() == null;
    }

    /** Where {@link #resolveConfigKey()} is about to read the key from, for logging. */
    public static String describeConfigKeySource() {
        String fromEnv = System.getenv(KEY_ENV_VAR);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return "environment variable " + KEY_ENV_VAR;
        }
        File keyFile = new File(KEY_FILE);
        if (keyFile.isFile()) {
            return "key file " + keyFile.getAbsolutePath();
        }
        return "the built-in default key";
    }

    private static String validateKey(String base64Key, String source) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The AES key from " + source + " is not valid Base64", e);
        }
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalStateException("The AES key from " + source + " decodes to " + decoded.length
                    + " bytes; AES needs 16, 24 or 32.");
        }
        return base64Key;
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                printUsage();
                System.exit(1);
            }

            switch (args[0]) {
                case "genkey" -> {
                    System.out.println(new CryptoDatabaseConfig().getSecretKeyBase64());
                    System.out.println();
                    System.out.println("Put this in " + KEY_ENV_VAR + ", or in a file named " + KEY_FILE
                            + " in the directory the application starts from.");
                    System.out.println("Keep it out of version control, and back it up: without it, config.xml"
                            + " cannot be read.");
                }
                case "encrypt" -> {
                    if (args.length < 7) {
                        printUsage();
                        System.exit(1);
                    }
                    CryptoDatabaseConfig encryptor = new CryptoDatabaseConfig(requireConfigKey());
                    String port = args[1];
                    String host = args[2];
                    String dbName = args[3];
                    String username = args[4];
                    String password = args[5];
                    String fileName = args[6];
                    encryptor.saveEncryptedConfigToXML(
                            fileName,
                            "jdbc:mysql://" + host + ":" + port + "/" + dbName,
                            dbName, host, username, password, port,
                            "com.mysql.cj.jdbc.Driver"
                    );
                    System.out.println("Wrote " + fileName + " using " + describeConfigKeySource() + ".");
                }
                case "decrypt" -> {
                    if (args.length < 2) {
                        printUsage();
                        System.exit(1);
                    }
                    CryptoDatabaseConfig encryptor = new CryptoDatabaseConfig(resolveConfigKey());
                    HashMap<String, String> map = encryptor.loadAndDecryptConfig(args[1]);
                    System.out.println("Decrypted using " + describeConfigKeySource() + ":");
                    for (String field : new String[]{URL, DBNAME, HOST, USERNAME, PASSWORD, PORT, DRIVER}) {
                        System.out.println("  " + field + ": " + map.get(field));
                    }
                }
                case "migrate" -> {
                    if (args.length < 2) {
                        printUsage();
                        System.exit(1);
                    }
                    String fileName = args[1];
                    // Read before demanding the new key, so a missing new key is
                    // reported before the existing file has been touched.
                    HashMap<String, String> current = readForMigration(fileName);
                    CryptoDatabaseConfig target = new CryptoDatabaseConfig(requireConfigKey());
                    target.saveEncryptedConfigToXML(fileName,
                            current.get(URL), current.get(DBNAME), current.get(HOST),
                            current.get(USERNAME), current.get(PASSWORD), current.get(PORT),
                            current.get(DRIVER));
                    System.out.println("Rewrote " + fileName + " as AES/GCM using " + describeConfigKeySource() + ".");
                    System.out.println("The previous contents were encrypted with the built-in key. Treat the"
                            + " credentials in them as public and change them on the database server.");
                }
                default -> {
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Reads a config.xml that is about to be re-encrypted.
     * <p>
     * The key configured for this install is tried first, then the built-in one.
     * By the time anyone runs a migration the new key is normally already
     * installed while the file is still under the old one - which is precisely the
     * state migration exists to resolve, so resolving the key the ordinary way
     * would pick the key the file is not encrypted with.
     */
    private static HashMap<String, String> readForMigration(String fileName) throws Exception {
        String configured = explicitConfigKey();
        if (configured != null) {
            try {
                return new CryptoDatabaseConfig(configured).loadAndDecryptConfig(fileName);
            } catch (Exception e) {
                // Not encrypted with this install's key; try the one it shipped under.
            }
        }
        return new CryptoDatabaseConfig(FALLBACK_KEY).loadAndDecryptConfig(fileName);
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  genkey                                                    print a new AES key
                  encrypt <port> <host> <dbname> <user> <pass> <file>        write a new config.xml
                  decrypt <file>                                            print the settings in a config.xml
                  migrate <file>                                            re-encrypt an existing config.xml""");
    }

    /** Encrypts one value in the authenticated format. */
    public String encrypt(String data) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return V2_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts one value in whichever format it carries, so a file written before
     * the move to GCM still loads.
     */
    public String decrypt(String encryptedData) throws Exception {
        if (encryptedData.startsWith(V2_PREFIX)) {
            byte[] combined = Base64.getDecoder().decode(encryptedData.substring(V2_PREFIX.length()));
            if (combined.length <= GCM_IV_BYTES) {
                throw new IllegalStateException("A value in config.xml is too short to be valid.");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, GCM_IV_BYTES, combined.length - GCM_IV_BYTES);
            return new String(decrypted, StandardCharsets.UTF_8);
        }

        // Original format: bare AES, which the JDK resolves to ECB, and unauthenticated.
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)), StandardCharsets.UTF_8);
    }

    public void saveEncryptedConfigToXML(String filePath,
                                         String dbUrl,
                                         String dbName,
                                         String host,
                                         String username,
                                         String password,
                                         String port,
                                         String driver) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element rootElement = doc.createElement(DATABASE_CONFIG);
        doc.appendChild(rootElement);

        appendEncrypted(doc, rootElement, URL, dbUrl);
        appendEncrypted(doc, rootElement, DBNAME, dbName);
        appendEncrypted(doc, rootElement, HOST, host);
        appendEncrypted(doc, rootElement, USERNAME, username);
        appendEncrypted(doc, rootElement, PASSWORD, password);
        appendEncrypted(doc, rootElement, PORT, port);
        appendEncrypted(doc, rootElement, DRIVER, driver);

        // Write the content into XML file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);

        // The key is deliberately not written anywhere near the file it protects.
        // This used to drop it into secret_key.txt in the same directory, which put
        // the plaintext key beside the ciphertext and undid the encryption for
        // anyone who could read the folder.
    }

    private void appendEncrypted(Document doc, Element parent, String tagName, String value) throws Exception {
        Element element = doc.createElement(tagName);
        element.appendChild(doc.createTextNode(encrypt(value)));
        parent.appendChild(element);
    }

    public HashMap<String, String> loadAndDecryptConfig(String filePath) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File(filePath));

        Element root = doc.getDocumentElement();
        HashMap<String, String> map = new HashMap<>();
        map.put(URL, decryptElement(root, URL));
        map.put(DBNAME, decryptElement(root, DBNAME));
        map.put(HOST, decryptElement(root, HOST));
        map.put(USERNAME, decryptElement(root, USERNAME));
        map.put(PASSWORD, decryptElement(root, PASSWORD));
        map.put(PORT, decryptElement(root, PORT));
        map.put(DRIVER, decryptElement(root, DRIVER));
        return map;
    }

    /**
     * Decrypts one element, naming it when the file does not carry it. Going
     * straight to {@link #decrypt} would hand a null to the Base64 decoder and
     * fail with a bare NullPointerException, which says nothing about which
     * setting is missing.
     */
    private String decryptElement(Element root, String tagName) throws Exception {
        String value = getElementValue(root, tagName);
        if (value == null) {
            throw new IllegalStateException("config.xml has no <" + tagName + "> element.");
        }
        if (value.isBlank()) {
            throw new IllegalStateException("The <" + tagName + "> element in config.xml is empty.");
        }
        return decrypt(value.trim());
    }

    private String getElementValue(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    public String getSecretKeyBase64() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
}
