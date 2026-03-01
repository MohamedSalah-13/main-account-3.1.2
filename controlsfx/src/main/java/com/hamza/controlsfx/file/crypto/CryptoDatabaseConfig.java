package com.hamza.controlsfx.file.crypto;

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
import java.nio.charset.StandardCharsets;
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
    private final SecretKey secretKey;

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

    public static void main(String[] args) {
        try {

            CryptoDatabaseConfig encryptor = new CryptoDatabaseConfig();
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

                System.out.println("Encrypted database configuration saved to XML file.");
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

        // Write the secret key into a separate file
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
        String url = decrypt(getElementValue(root, URL));
        String dbName = decrypt(getElementValue(root, DBNAME));
        String host = decrypt(getElementValue(root, HOST));
        String username = decrypt(getElementValue(root, USERNAME));
        String password = decrypt(getElementValue(root, PASSWORD));
        String port = decrypt(getElementValue(root, PORT));
//        String key = getElementValue(root, KEY);
        String driver = decrypt(getElementValue(root, DRIVER));
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