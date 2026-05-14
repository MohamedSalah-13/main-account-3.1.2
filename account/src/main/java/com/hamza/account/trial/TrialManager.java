package com.hamza.account.trial;

import com.hamza.controlsfx.alert.AllAlerts;
import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;

@Log4j2
public class TrialManager {

    private static final int TRIAL_DAYS = 7;
    private static final int MAX_ITEMS = 10;
    private static final int MAX_CUSTOMERS = 5;
    private static final int MAX_SALES = 10;
    private static final int MAX_PURCHASES = 10;
    private static final String SECRET_KEY = "HamzaAccountKey!";
    private static final String HMAC_KEY = "HamzaAccountHmacKey!";
    private static final String APP_DATA_FOLDER = System.getenv("APPDATA") + "\\HamzaAccount";
    private static final String TRIAL_FILE_PATH = APP_DATA_FOLDER + "\\trial.dat";
    private static final String FILE_VERSION = "v1";
    private static final int MAX_FAILS = 1;
    private static final String LICENSE_FILE_PATH = System.getProperty("user.dir") + "\\license.dat";
    private static final String LICENSE_PUBLIC_KEY_PEM =
            "-----BEGIN PUBLIC KEY-----\n" +
                    "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA0hpbyW7GN3reweG/Pp/7\n" +
                    "O/hlaHOeOnoGEahcF5bgxO009mEubbxRZd/dtrveGrQT1p2sYVZP1nBenlijrto0\n" +
                    "sxrSUOlBQxfLvSnGE3k5951CQQAoDLuOQexg+AVwzA9LuCDS5eX70DpJMu+hZWtd\n" +
                    "pcJMyIgbCYbjGQWWgHZ7adcDMwreELuyD/kR/j8BkmPe+2LzhzMckZI+tAHmHWlz\n" +
                    "qU37N3kOD6oe6yokm1ygpWeIh2BwOXtbyEglOIKCKzycAY2qUBzr5Fee5Nd0dKhI\n" +
                    "uqWPEfBC9SJ2cRJzP1z9v/JGQEGMGrO5xOGvQ1+D15Y2iSI+tkWk+oLc4UzrR3GU\n" +
                    "vjajBVD2mBUiLaP0T4fiuco85itmfschYQmEqcQLF2+kjjU2WKl18pPcAhglrA/P\n" +
                    "uuYqdA7LigV8ejdF1j2wRxTcXwg4fT87Fg0WYUw5UijH7Jx4rTWGO5xhOzMuZbca\n" +
                    "Vimf4BnOTtLm8RmI3Nmy383r8ijdEVTBnamRIx4u1SSVAgMBAAE=\n" +
                    "-----END PUBLIC KEY-----\n";

    private final Connection connection;

    public TrialManager(Connection connection) {
        this.connection = connection;
    }

    public static Path getLicensePath() {
        return Paths.get(LICENSE_FILE_PATH);
    }

    public void checkTrialStatus() {
        try {
            if (isLicenseValid()) {
                return;
            }

            ensureInstallationDateColumnExists();

            String machineId = getMachineId();
            if (machineId == null || machineId.isBlank()) {
                failAndExit("Machine ID cannot be empty or null");
                return;
            }

            TrialDbData dbData = getTrialDataFromDb();
            boolean dbExists = dbData != null && dbData.date != null;
            if (dbExists && dbData.failCount >= MAX_FAILS) {
                failAndExit("Trial has failed too many times. Please contact support.");
                return;
            }

            boolean trialFileExists = Files.exists(Paths.get(TRIAL_FILE_PATH));
            TrialFileData fileData = null;
            if (trialFileExists) {
                fileData = getTrialDataFromFile();
                if (fileData == null) {
                    failAndExit("Unable to read trial data from file. Please try again.");
                    return;
                }
            }

            if (!trialFileExists && dbExists) {
                failAndExit("Trial data not found in file but exists in database. Please contact support.");
                return;
            }

            if (trialFileExists && !dbExists) {
                failAndExit("Trial data exists in file but not in database. Please contact support.");
                return;
            }

            if (!trialFileExists && !dbExists) {
                LocalDate now = LocalDate.now();
                saveInstallationData(now, machineId);
                dbData = getTrialDataFromDb();
                fileData = getTrialDataFromFile();
            } else {
                if (fileData == null || fileData.date == null) {
                    failAndExit("Trial data exists in file but not in database. Please contact support.");
                    return;
                }

                if (fileData.legacy || dbData.machineId == null || dbData.hmac == null || fileData.machineId == null
                        || dbData.lastCheck == null || fileData.lastCheck == null) {
                    if (!dbData.date.equals(fileData.date)) {
                        failAndExit("Trial data mismatch between file and database. Please contact support.");
                        return;
                    }
                    saveInstallationData(dbData.date, machineId);
                    dbData = getTrialDataFromDb();
                    fileData = getTrialDataFromFile();
                }

                if (!dbData.date.equals(fileData.date)) {
                    failAndExit("Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (!dbData.machineId.equals(fileData.machineId)) {
                    failAndExit("Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (!dbData.machineId.equals(machineId)) {
                    failAndExit("Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (dbData.lastCheck != null && fileData.lastCheck != null
                        && !dbData.lastCheck.equals(fileData.lastCheck)) {
                    failAndExit("Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (dbData.lastCheck != null && LocalDate.now().isBefore(dbData.lastCheck)) {
                    failAndExit("Trial expired. Please contact support.");
                    return;
                }

                String payload = buildPayload(dbData.date, dbData.machineId, dbData.lastCheck);
                String expectedHmac = computeHmac(payload);
                if (expectedHmac == null || !expectedHmac.equals(dbData.hmac)) {
                    failAndExit("Trial data mismatch between file and database. Please contact support.");
                    return;
                }
            }

            LocalDate installationDate = dbData != null ? dbData.date : (fileData != null ? fileData.date : LocalDate.now());

            long daysPassed = ChronoUnit.DAYS.between(installationDate, LocalDate.now());
            long daysRemaining = TRIAL_DAYS - daysPassed;

            if (daysRemaining <= 0) {
                failAndExit("Trial expired. Please contact support.");
            } else if (daysRemaining <= 5) {
                AllAlerts.alertError("Warning: Your trial will expire in " + daysRemaining + " days. Please renew your subscription.");
            }

            // Save last check date to file
            updateLastCheck(LocalDate.now(), machineId);
        } catch (Exception e) {
            log.error("Error checking trial status", e);
        }
    }

    private void ensureInstallationDateColumnExists() {
        try (Statement stmt = connection.createStatement()) {
            try {
                stmt.executeQuery("SELECT installation_date FROM company LIMIT 1");
            } catch (Exception e) {
                stmt.execute("ALTER TABLE company ADD COLUMN installation_date DATE NULL");
            }

            try {
                stmt.executeQuery("SELECT trial_machine FROM company LIMIT 1");
            } catch (Exception e) {
                stmt.execute("ALTER TABLE company ADD COLUMN trial_machine VARCHAR(128) NULL");
            }

            try {
                stmt.executeQuery("SELECT trial_hash FROM company LIMIT 1");
            } catch (Exception e) {
                stmt.execute("ALTER TABLE company ADD COLUMN trial_hash VARCHAR(256) NULL");
            }

            try {
                stmt.executeQuery("SELECT trial_last_check FROM company LIMIT 1");
            } catch (Exception e) {
                stmt.execute("ALTER TABLE company ADD COLUMN trial_last_check DATE NULL");
            }

            try {
                stmt.executeQuery("SELECT trial_fail_count FROM company LIMIT 1");
            } catch (Exception e) {
                stmt.execute("ALTER TABLE company ADD COLUMN trial_fail_count INT NULL");
            }

            try {
                stmt.executeQuery("SELECT trial_fail_last FROM company LIMIT 1");
            } catch (Exception e) {
                stmt.execute("ALTER TABLE company ADD COLUMN trial_fail_last DATE NULL");
            }
        } catch (Exception e) {
            log.error("Error ensuring installation_date column exists", e);
        }
    }

    private TrialDbData getTrialDataFromDb() {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT installation_date, trial_machine, trial_hash, trial_last_check, trial_fail_count, trial_fail_last FROM company WHERE comp_id = 1")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                java.sql.Date date = rs.getDate("installation_date");
                java.sql.Date lastCheckDate = rs.getDate("trial_last_check");
                java.sql.Date failLastDate = rs.getDate("trial_fail_last");
                TrialDbData data = new TrialDbData();
                data.date = date != null ? date.toLocalDate() : null;
                data.machineId = rs.getString("trial_machine");
                data.hmac = rs.getString("trial_hash");
                data.lastCheck = lastCheckDate != null ? lastCheckDate.toLocalDate() : null;
                data.failCount = rs.getInt("trial_fail_count");
                data.failLast = failLastDate != null ? failLastDate.toLocalDate() : null;
                return data;
            }
        } catch (Exception e) {
            log.error("Error getting installation date from database", e);
        }
        return null;
    }

    private TrialFileData getTrialDataFromFile() {
        try {
            File file = new File(TRIAL_FILE_PATH);
            if (!file.exists()) return null;

            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) return null;

            if (content.startsWith(FILE_VERSION + ":")) {
                String payload = decryptGcm(content);
                return parsePayload(payload);
            } else {
                String decryptedDate = decryptLegacy(content);
                TrialFileData data = new TrialFileData();
                data.date = LocalDate.parse(decryptedDate, DateTimeFormatter.ISO_LOCAL_DATE);
                data.machineId = null;
                data.lastCheck = null;
                data.legacy = true;
                return data;
            }
        } catch (Exception e) {
            log.error("Error getting installation date from file", e);
        }
        return null;
    }

    private void saveInstallationData(LocalDate date, String machineId) {
        LocalDate now = LocalDate.now();
        saveInstallationDataToDb(date, machineId, now);
        saveInstallationDataToFile(date, machineId, now);
    }

    private void saveInstallationDataToDb(LocalDate date, String machineId, LocalDate lastCheck) {
        String payload = buildPayload(date, machineId, lastCheck);
        String hmac = computeHmac(payload);

        try (PreparedStatement checkStmt = connection.prepareStatement(
                "SELECT comp_id FROM company WHERE comp_id = 1");
             PreparedStatement updateStmt = connection.prepareStatement(
                     "UPDATE company SET installation_date = ?, trial_machine = ?, trial_hash = ?, trial_last_check = ? WHERE comp_id = 1");
             PreparedStatement insertStmt = connection.prepareStatement(
                     "INSERT INTO company (comp_id, comp_name, installation_date, trial_machine, trial_hash, trial_last_check, trial_fail_count, trial_fail_last) VALUES (1, ?, ?, ?, ?, ?, 0, NULL)")) {

            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                updateStmt.setDate(1, java.sql.Date.valueOf(date));
                updateStmt.setString(2, machineId);
                updateStmt.setString(3, hmac);
                updateStmt.setDate(4, java.sql.Date.valueOf(lastCheck));
                updateStmt.executeUpdate();
            } else {
                insertStmt.setString(1, "شركة تجريبية");
                insertStmt.setDate(2, java.sql.Date.valueOf(date));
                insertStmt.setString(3, machineId);
                insertStmt.setString(4, hmac);
                insertStmt.setDate(5, java.sql.Date.valueOf(lastCheck));
                insertStmt.executeUpdate();
            }
        } catch (Exception e) {
            log.error("Error saving installation date to database", e);
        }
    }

    private void saveInstallationDataToFile(LocalDate date, String machineId, LocalDate lastCheck) {
        try {
            Path path = Paths.get(APP_DATA_FOLDER);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            String payload = buildPayload(date, machineId, lastCheck);
            String encryptedPayload = encryptGcm(payload);
            Files.write(Paths.get(TRIAL_FILE_PATH), encryptedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error saving installation date to file", e);
        }
    }

    private String encryptGcm(String value) throws Exception {
        SecretKeySpec secretKey = getAesKey();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        byte[] encryptedValue = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return FILE_VERSION + ":" + Base64.getEncoder().encodeToString(iv) + ":" +
                Base64.getEncoder().encodeToString(encryptedValue);
    }

    private String decryptGcm(String encryptedValue) throws Exception {
        String[] parts = encryptedValue.split(":");
        if (parts.length != 3 || !FILE_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Invalid trial file format");
        }
        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] cipherText = Base64.getDecoder().decode(parts[2]);
        SecretKeySpec secretKey = getAesKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
        byte[] decryptedValue = cipher.doFinal(cipherText);
        return new String(decryptedValue, StandardCharsets.UTF_8);
    }

    private String decryptLegacy(String encryptedValue) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedValue = Base64.getDecoder().decode(encryptedValue);
        byte[] decryptedValue = cipher.doFinal(decodedValue);
        return new String(decryptedValue, StandardCharsets.UTF_8);
    }

    private SecretKeySpec getAesKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(Arrays.copyOf(key, 16), "AES");
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("Error computing HMAC", e);
            return null;
        }
    }

    private String buildPayload(LocalDate date, String machineId, LocalDate lastCheck) {
        String encodedMachine = Base64.getEncoder().encodeToString(
                machineId.getBytes(StandardCharsets.UTF_8));
        String lastCheckStr = lastCheck != null ? lastCheck.format(DateTimeFormatter.ISO_LOCAL_DATE) : "";
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE) + "|" + encodedMachine + "|" + lastCheckStr;
    }

    private TrialFileData parsePayload(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 2) return null;
        TrialFileData data = new TrialFileData();
        data.date = LocalDate.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE);
        byte[] decodedMachine = Base64.getDecoder().decode(parts[1]);
        data.machineId = new String(decodedMachine, StandardCharsets.UTF_8);
        if (parts.length >= 3 && !parts[2].isBlank()) {
            data.lastCheck = LocalDate.parse(parts[2], DateTimeFormatter.ISO_LOCAL_DATE);
        } else {
            data.lastCheck = null;
        }
        data.legacy = false;
        return data;
    }

    private String getMachineId() {
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                    .redirectErrorStream(true)
                    .start();
            byte[] outBytes = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            String output = new String(outBytes, StandardCharsets.UTF_8);
            if (exit != 0) {
                log.warn("Unable to read MachineGuid. Output: {}", output);
                return null;
            }
            for (String line : output.split("\\R")) {
                line = line.trim();
                if (line.startsWith("MachineGuid")) {
                    String[] tokens = line.split("\\s+");
                    return tokens[tokens.length - 1];
                }
            }
        } catch (Exception e) {
            log.error("Error reading MachineGuid", e);
        }
        return null;
    }

    private boolean isLicenseValid() {
        return validateLicense(Paths.get(LICENSE_FILE_PATH), true).valid;
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        String sanitized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(sanitized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public TrialDisplayInfo getDisplayInfo() {
        TrialDisplayInfo info = new TrialDisplayInfo();
        try {
            LicenseCheckResult license = validateLicense(Paths.get(LICENSE_FILE_PATH), false);
            info.licensePresent = license.present;
            info.licenseValid = license.valid;
            info.licenseError = license.error;

            if (license.valid) {
                info.activated = true;
                return info;
            }

            ensureInstallationDateColumnExists();
            TrialDbData dbData = getTrialDataFromDb();
            TrialFileData fileData = getTrialDataFromFile();

            LocalDate installationDate = null;
            if (dbData != null && dbData.date != null) {
                installationDate = dbData.date;
            } else if (fileData != null && fileData.date != null) {
                installationDate = fileData.date;
            }

            info.installationDate = installationDate;
            if (installationDate != null) {
                long daysPassed = ChronoUnit.DAYS.between(installationDate, LocalDate.now());
                info.daysRemaining = TRIAL_DAYS - daysPassed;
                info.trialExpired = info.daysRemaining <= 0;
            }
        } catch (Exception e) {
            log.error("Error building trial display info", e);
            info.error = e.getMessage();
        }
        return info;
    }

    private LicenseCheckResult validateLicense(Path path, boolean strict) {
        LicenseCheckResult result = new LicenseCheckResult();
        try {
            if (!Files.exists(path)) {
                result.present = false;
                result.valid = false;
                return result;
            }
            result.present = true;

            byte[] fileBytes = Files.readAllBytes(path);
            if (fileBytes.length == 0) {
                return licenseFail(result, strict, "File is empty");
            }

            int start = 0;
            if (fileBytes.length >= 3
                    && (fileBytes[0] & 0xFF) == 0xEF
                    && (fileBytes[1] & 0xFF) == 0xBB
                    && (fileBytes[2] & 0xFF) == 0xBF) {
                start = 3; // skip UTF-8 BOM if present
            }

            int dot = indexOfByte(fileBytes, (byte) '.', start);
            if (dot <= start || dot == fileBytes.length - 1) {
                return licenseFail(result, strict, "Invalid license format.");
            }

            byte[] payloadB64Bytes = Arrays.copyOfRange(fileBytes, start, dot);
            byte[] signaturePart = Arrays.copyOfRange(fileBytes, dot + 1, fileBytes.length);

            String payloadB64 = new String(trimAsciiWhitespace(payloadB64Bytes), StandardCharsets.US_ASCII);
            byte[] payloadBytes = Base64.getDecoder().decode(payloadB64);
            byte[] signatureBytes = decodeSignatureBytes(signaturePart);

            PublicKey publicKey = loadPublicKey(LICENSE_PUBLIC_KEY_PEM);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            if (!signature.verify(signatureBytes)) {
                return licenseFail(result, strict, "Invalid signature");
            }

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", 2);
            if (parts.length != 2 || !"HAMZA_ACCOUNT".equals(parts[0])) {
                return licenseFail(result, strict, "Invalid license format.");
            }

            String targetMachine = parts[1];
            if (targetMachine == null || targetMachine.isBlank()) {
                return licenseFail(result, strict, "Invalid target machine.");
            }

            String currentMachine = getMachineId();
            if (currentMachine == null || currentMachine.isBlank()) {
                return licenseFail(result, strict, "Unable to retrieve current machine ID.");
            }

            if (!targetMachine.equals(currentMachine)) {
                return licenseFail(result, strict, "Target machine does not match current machine.");
            }

            result.valid = true;
            return result;
        } catch (Exception e) {
            log.error("Error validating license file", e);
            return licenseFail(result, strict, "Invalid license file.");
        }
    }

    private int indexOfByte(byte[] data, byte target, int from) {
        for (int i = Math.max(0, from); i < data.length; i++) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private byte[] trimAsciiWhitespace(byte[] data) {
        int start = 0;
        int end = data.length;
        while (start < end && isAsciiWhitespace(data[start])) {
            start++;
        }
        while (end > start && isAsciiWhitespace(data[end - 1])) {
            end--;
        }
        return Arrays.copyOfRange(data, start, end);
    }

    private boolean isAsciiWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    private byte[] decodeSignatureBytes(byte[] signaturePart) {
        byte[] compact = removeAsciiWhitespace(signaturePart);
        if (compact.length == 0) {
            return compact;
        }
        if (isAsciiBase64Like(compact)) {
            String sig = new String(compact, StandardCharsets.US_ASCII);
            try {
                return Base64.getDecoder().decode(sig);
            } catch (IllegalArgumentException ignored) {
                try {
                    return Base64.getUrlDecoder().decode(sig);
                } catch (IllegalArgumentException ignoredAgain) {
                    // fall through to raw bytes
                }
            }
        }
        return signaturePart;
    }

    private boolean isAsciiBase64Like(byte[] data) {
        for (byte b : data) {
            if ((b >= 'A' && b <= 'Z')
                    || (b >= 'a' && b <= 'z')
                    || (b >= '0' && b <= '9')
                    || b == '+' || b == '/' || b == '='
                    || b == '-' || b == '_') {
                continue;
            }
            return false;
        }
        return true;
    }

    private byte[] removeAsciiWhitespace(byte[] data) {
        int count = 0;
        for (byte b : data) {
            if (!isAsciiWhitespace(b)) {
                count++;
            }
        }
        if (count == data.length) {
            return data;
        }
        byte[] compact = new byte[count];
        int idx = 0;
        for (byte b : data) {
            if (!isAsciiWhitespace(b)) {
                compact[idx++] = b;
            }
        }
        return compact;
    }

    private LicenseCheckResult licenseFail(LicenseCheckResult result, boolean strict, String message) {
        result.valid = false;
        result.error = message;
        if (strict) {
            failAndExit(message);
        }
        return result;
    }

    public boolean canAddItem() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("items", MAX_ITEMS, "لقد وصلت للحد الأقصى من الأصناف في النسخة التجريبية (10 صنف).");
    }

    public boolean canAddCustomer() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("custom", MAX_CUSTOMERS, "لقد وصلت للحد الأقصى من العملاء في النسخة التجريبية (5 عميل).");
    }

    public boolean canAddSale() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("total_sales", MAX_SALES, "لقد وصلت للحد الأقصى من المبيعات في النسخة التجريبية (10 مبيعات).");
    }

    public boolean canAddPurchase() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("total_buy", MAX_PURCHASES, "لقد وصلت للحد الأقصى من المشتريات في النسخة التجريبية (10 مشتريات).");
    }

    private boolean checkLimit(String tableName, int maxLimit, String errorMessage) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                int count = rs.getInt(1);
                if (count >= maxLimit) {
                    AllAlerts.alertError(errorMessage);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Error checking limit for " + tableName, e);
        }
        return true;
    }

    private void updateLastCheck(LocalDate now, String machineId) {
        TrialDbData dbData = getTrialDataFromDb();
        if (dbData == null || dbData.date == null) return;
        saveInstallationData(dbData.date, machineId);
    }

    private void failAndExit(String message) {
        recordTrialFailure();
        AllAlerts.alertError(message);
        Platform.exit();
        System.exit(0);
    }

    private void recordTrialFailure() {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE company SET trial_fail_count = COALESCE(trial_fail_count, 0) + 1, trial_fail_last = ? WHERE comp_id = 1")) {
            stmt.setDate(1, java.sql.Date.valueOf(LocalDate.now()));
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("Error recording trial failure", e);
        }
    }

    private static class TrialDbData {
        LocalDate date;
        String machineId;
        String hmac;
        LocalDate lastCheck;
        int failCount;
        LocalDate failLast;
    }

    private static class TrialFileData {
        LocalDate date;
        String machineId;
        LocalDate lastCheck;
        boolean legacy;
    }

    private static class LicenseCheckResult {
        boolean present;
        boolean valid;
        String error;
    }

    public static class TrialDisplayInfo {
        public boolean activated;
        public boolean licensePresent;
        public boolean licenseValid;
        public String licenseError;
        public LocalDate installationDate;
        public Long daysRemaining;
        public boolean trialExpired;
        public String error;
    }
}
