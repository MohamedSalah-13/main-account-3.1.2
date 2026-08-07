package com.hamza.controlsfx.util.crypto;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Scanner;

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

    /** Environment variable holding the Base64 AES key, checked first. */
    public static final String KEY_ENV_VAR = "ACCOUNT_CONFIG_KEY";
    /** Key file resolved against the working directory, like config.xml itself. */
    public static final String KEY_FILE = "config.key";
    /**
     * The key every config.xml already in the field was encrypted with. It is the
     * last fallback rather than the only option: changing this value would make
     * those files unreadable, so it stays put until each install has been
     * re-encrypted under a key of its own.
     */
    private static final String FALLBACK_KEY = "nZdjCubzMZs+/RU1XDr/7g==";

    private final SecretKey secretKey;

    /**
     * Mints a brand new random key. Only useful for generating a key to hand to
     * {@link #KEY_ENV_VAR} or {@value #KEY_FILE}: anything encrypted with it is
     * unreadable by the application unless that key is installed there too, so
     * do not use this to write a config.xml.
     */
    public CryptoDatabaseConfig() throws Exception {
        // Generate a secret key for AES encryption
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(128); // 128-bit key
        this.secretKey = keyGenerator.generateKey();
    }

    public CryptoDatabaseConfig(String base64Key) throws Exception {
        // Recreate secret key from base64 string
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
//        this.secretKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
//                .digest("nZdjCubzMZs+/RU1XDr/7g==".getBytes()), "AES");
    }

    /**
     * The key both the CLI and the running application encrypt and decrypt
     * config.xml with, so that a file written here loads at startup. Looked up in
     * the environment first, then in {@value #KEY_FILE} next to the working
     * directory, then falling back to the key existing installs already use.
     */
    public static String resolveConfigKey() {
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

        return FALLBACK_KEY;
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

            CryptoDatabaseConfig encryptor = new CryptoDatabaseConfig(resolveConfigKey());
            if (args.length < 1) {
                System.out.println("Usage: java CryptoDatabaseConfig <encrypt|decrypt>");
                System.exit(1);
            }

            String encrypt = args[0];
            if (encrypt.equals("encrypt")) {
                if (args.length < 7) {
                    System.out.println("Usage: java CryptoDatabaseConfig <encrypt|decrypt> <port> <host> <dbname> <username> <password> <filename>");
                    System.exit(1);
                }

                // check pass
                if (!getPasswordFromInput().equals("hamza")) {
                    System.out.println("Wrong password");
                    return;
                }

                String port = args[1];
                String localhost = args[2];
                String accountSystemDb = args[3];
                String username = args[4];
                String password = args[5];
                String fileName = args[6];
                encryptor.saveEncryptedConfigToXML(
                        fileName,
                        "jdbc:mysql://" + localhost + ":" + port + "/" + accountSystemDb,
                        accountSystemDb, localhost, username, password, port,
                        "com.mysql.cj.jdbc.Driver"
                );

                System.out.println("Encrypted database configuration saved to " + fileName
                        + ", using " + describeConfigKeySource() + ".");
            }

            if (encrypt.equals("decrypt")) {
                if (args.length < 2) {
                    System.out.println("Usage: java CryptoDatabaseConfig <encrypt|decrypt> <filename>");
                    System.exit(1);
                }

                // check pass
                if (!getPasswordFromInput().equals("hamza")) {
                    System.out.println("Wrong password");
                    return;
                }

                String fileName = args[1];
                HashMap<String, String> map = encryptor.loadAndDecryptConfig(fileName);
                System.out.println("Decrypted database configuration:");
                System.out.println("URL: " + map.get(URL));
                System.out.println("DBNAME: " + map.get(DBNAME));
                System.out.println("HOST: " + map.get(HOST));
                System.out.println("USERNAME: " + map.get(USERNAME));
                System.out.println("PASSWORD: " + map.get(PASSWORD));
                System.out.println("PORT: " + map.get(PORT));
                System.out.println("DRIVER: " + map.get(DRIVER));
            }

            // In a real application, you would:
            // 1. Securely store the secret key (not in the XML file)
            // 2. Load the key from a secure location when needed
            // 3. Decrypt the values when establishing connections

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getPasswordFromInput() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter password: ");
        return scanner.nextLine();
    }

    public String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public String decrypt(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
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

        Element urlElement = doc.createElement(URL);
        urlElement.appendChild(doc.createTextNode(encrypt(dbUrl)));
        rootElement.appendChild(urlElement);

        Element dbNameElement = doc.createElement(DBNAME);
        dbNameElement.appendChild(doc.createTextNode(encrypt(dbName)));
        rootElement.appendChild(dbNameElement);

        Element hostElement = doc.createElement(HOST);
        hostElement.appendChild(doc.createTextNode(encrypt(host)));
        rootElement.appendChild(hostElement);

        Element userElement = doc.createElement(USERNAME);
        userElement.appendChild(doc.createTextNode(encrypt(username)));
        rootElement.appendChild(userElement);

        Element passElement = doc.createElement(PASSWORD);
        passElement.appendChild(doc.createTextNode(encrypt(password)));
        rootElement.appendChild(passElement);

        Element portElement = doc.createElement(PORT);
        portElement.appendChild(doc.createTextNode(encrypt(port)));
        rootElement.appendChild(portElement);

        Element driverElement = doc.createElement(DRIVER);
        driverElement.appendChild(doc.createTextNode(encrypt(driver)));
        rootElement.appendChild(driverElement);

        // Save the key separately (in a real application, store this securely)
//        Element keyElement = doc.createElement(KEY);
//        keyElement.setAttribute("warning", "YOUR_BASE64_ENCODED_KEY_HERE");
//        keyElement.appendChild(doc.createTextNode(Base64.getEncoder().encodeToString(secretKey.getEncoded())));
//        rootElement.appendChild(keyElement);

        // Write the content into XML file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);

        // Record which key this file was encrypted under, so a config.xml that
        // stops loading can be matched against the key in use. Reading it back is
        // deliberately not automatic: see resolveConfigKey.
        try (FileWriter writer = new FileWriter("secret_key.txt", false)) {
            writer.write(Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        }
    }

    public HashMap<String, String> loadAndDecryptConfig(String filePath) throws Exception {
        // Parse the XML file
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File(filePath));

        // Get the root element
        Element root = doc.getDocumentElement();
        HashMap<String, String> map = new HashMap<>();
        // Decrypt and print each value
        String url = decryptElement(root, URL);
        String dbName = decryptElement(root, DBNAME);
        String host = decryptElement(root, HOST);
        String username = decryptElement(root, USERNAME);
        String password = decryptElement(root, PASSWORD);
        String port = decryptElement(root, PORT);
//        String key = getElementValue(root, KEY);
        String driver = decryptElement(root, DRIVER);
        map.put(URL, url);
        map.put(DBNAME, dbName);
        map.put(HOST, host);
        map.put(USERNAME, username);
        map.put(PASSWORD, password);
        map.put(PORT, port);
//        map.put(KEY, key);
        map.put(DRIVER, driver);
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
        return decrypt(value);
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