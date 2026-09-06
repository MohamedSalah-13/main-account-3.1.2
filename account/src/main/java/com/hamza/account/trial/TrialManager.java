package com.hamza.account.trial;

import com.hamza.account.config.MachineId;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
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

            ensureTrialTableExists();

            String machineId = getMachineId();
            if (machineId == null || machineId.isBlank()) {
                // Nothing is recorded: the row a failure would be charged to is chosen by
                // the very value that is missing, and the shared row it used to land on is
                // what let one machine end another machine's install.
                failAndExit(null, "Machine ID cannot be empty or null");
                return;
            }

            TrialDbData dbData = getTrialDataFromDb(machineId);
            boolean dbExists = dbData != null && dbData.date != null;
            if (dbExists && dbData.failCount >= MAX_FAILS) {
                failAndExit(machineId, "Trial has failed too many times. Please contact support.");
                return;
            }

            boolean trialFileExists = Files.exists(Paths.get(TRIAL_FILE_PATH));
            TrialFileData fileData = null;
            if (trialFileExists) {
                fileData = getTrialDataFromFile();
                if (fileData == null) {
                    failAndExit(machineId, "Unable to read trial data from file. Please try again.");
                    return;
                }
            }

            if (!trialFileExists && !dbExists) {
                /*
                 * A machine this database has not seen before. It inherits the earliest
                 * installation date already recorded rather than starting a trial of its
                 * own: a shop with three tills would otherwise hold three seven-day
                 * trials, each restarting the clock, which is not what a trial is. With
                 * no rows at all this is genuinely a first install, and the date is today.
                 */
                LocalDate start = earliestInstallationDate();
                saveInstallationData(start, machineId);
                dbData = getTrialDataFromDb(machineId);
                fileData = getTrialDataFromFile();
            } else if (trialFileExists != dbExists) {
                /*
                 * One side is missing while the other still holds the trial. This used
                 * to end the trial permanently, but it is the ordinary result of
                 * reinstalling the application, of a new Windows profile, or of
                 * restoring a database backup - none of which are tampering, and all of
                 * which a customer can reach without trying.
                 *
                 * The surviving side is rewritten to both, keeping the installation date
                 * it already carries. Deleting either side therefore restores the same
                 * trial rather than starting a new one, which is the property the
                 * two-sided check existed to protect. Erasing both is still a fresh
                 * trial, but that is indistinguishable from a first run and always was.
                 */
                LocalDate knownDate = dbExists ? dbData.date : fileData.date;
                log.warn("Trial data found on only one side ({}); restoring it from the installation date {}",
                        dbExists ? "database" : "file", knownDate);
                saveInstallationData(knownDate, machineId);
                dbData = getTrialDataFromDb(machineId);
                fileData = getTrialDataFromFile();
            } else {
                if (fileData == null || fileData.date == null) {
                    failAndExit(machineId, "Trial data exists in file but not in database. Please contact support.");
                    return;
                }

                if (fileData.legacy || dbData.machineId == null || dbData.hmac == null || fileData.machineId == null
                        || dbData.lastCheck == null || fileData.lastCheck == null) {
                    if (!dbData.date.equals(fileData.date)) {
                        failAndExit(machineId, "Trial data mismatch between file and database. Please contact support.");
                        return;
                    }
                    saveInstallationData(dbData.date, machineId);
                    dbData = getTrialDataFromDb(machineId);
                    fileData = getTrialDataFromFile();
                }

                if (!dbData.date.equals(fileData.date)) {
                    failAndExit(machineId, "Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (!dbData.machineId.equals(fileData.machineId)) {
                    failAndExit(machineId, "Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (!dbData.machineId.equals(machineId)) {
                    failAndExit(machineId, "Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (dbData.lastCheck != null && fileData.lastCheck != null
                        && !dbData.lastCheck.equals(fileData.lastCheck)) {
                    failAndExit(machineId, "Trial data mismatch between file and database. Please contact support.");
                    return;
                }
                if (dbData.lastCheck != null && LocalDate.now().isBefore(dbData.lastCheck)) {
                    failAndExit(machineId, "Trial expired. Please contact support.");
                    return;
                }

                String payload = buildPayload(dbData.date, dbData.machineId, dbData.lastCheck);
                String expectedHmac = computeHmac(payload);
                if (expectedHmac == null || !expectedHmac.equals(dbData.hmac)) {
                    failAndExit(machineId, "Trial data mismatch between file and database. Please contact support.");
                    return;
                }
            }

            LocalDate installationDate = dbData != null ? dbData.date : (fileData != null ? fileData.date : LocalDate.now());

            long daysPassed = ChronoUnit.DAYS.between(installationDate, LocalDate.now());
            long daysRemaining = TRIAL_DAYS - daysPassed;

            if (daysRemaining <= 0) {
                failAndExit(machineId, "Trial expired. Please contact support.");
            } else if (daysRemaining <= 5) {
                AllAlerts.alertError("Warning: Your trial will expire in " + daysRemaining + " days. Please renew your subscription.");
            }

            // Save last check date to file
            updateLastCheck(LocalDate.now(), machineId);
        } catch (Exception e) {
            log.error("Error checking trial status", e);
        }
    }

    /**
     * The trial table, created here as well as by {@code V38__trial_per_machine.sql}.
     * <p>
     * The migration is what really creates it, and runs before this class is reached. The
     * statement is kept because this class has always been able to stand up its own storage
     * - it used to add six columns to {@code company} by hand - and a trial check that dies
     * because a table is missing is a customer who cannot open the program at all.
     */
    private void ensureTrialTableExists() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS trial_machine_state (
                        machine_id        VARCHAR(128) NOT NULL PRIMARY KEY,
                        installation_date DATE         NOT NULL,
                        trial_hash        VARCHAR(256) NULL,
                        trial_last_check  DATE         NULL,
                        trial_fail_count  INT          NOT NULL DEFAULT 0,
                        trial_fail_last   DATE         NULL,
                        created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE = InnoDB""");
        } catch (Exception e) {
            log.error("Error ensuring the trial table exists", e);
        }
    }

    /**
     * When the trial started for this install, taken from the machine that started it
     * first.
     * <p>
     * A second till joining an existing database is a new machine, not a new customer, so
     * it takes the clock that is already running instead of starting one.
     */
    private LocalDate earliestInstallationDate() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MIN(installation_date) FROM trial_machine_state")) {
            if (rs.next()) {
                java.sql.Date earliest = rs.getDate(1);
                if (earliest != null) {
                    return earliest.toLocalDate();
                }
            }
        } catch (Exception e) {
            log.error("Error reading the earliest installation date", e);
        }
        return LocalDate.now();
    }

    private TrialDbData getTrialDataFromDb(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            return null;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT installation_date, machine_id, trial_hash, trial_last_check, trial_fail_count, trial_fail_last"
                        + " FROM trial_machine_state WHERE machine_id = ?")) {
            stmt.setString(1, machineId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                java.sql.Date date = rs.getDate("installation_date");
                java.sql.Date lastCheckDate = rs.getDate("trial_last_check");
                java.sql.Date failLastDate = rs.getDate("trial_fail_last");
                TrialDbData data = new TrialDbData();
                data.date = date != null ? date.toLocalDate() : null;
                data.machineId = rs.getString("machine_id");
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

    /**
     * This machine's row, written whole.
     * <p>
     * It used to write the {@code company} row, and to create it - name and all - when the
     * table was empty, so a fresh install got a company called "شركة تجريبية" from the
     * licence check. {@code CompanyService.load} is what creates that row now, at the point
     * somebody opens the settings screen and types a real name into it.
     * <p>
     * The upsert leaves {@code trial_fail_count} alone: it belongs to this machine and
     * nothing here is entitled to clear it.
     */
    private void saveInstallationDataToDb(LocalDate date, String machineId, LocalDate lastCheck) {
        String payload = buildPayload(date, machineId, lastCheck);
        String hmac = computeHmac(payload);

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO trial_machine_state"
                        + " (machine_id, installation_date, trial_hash, trial_last_check, trial_fail_count)"
                        + " VALUES (?, ?, ?, ?, 0)"
                        + " ON DUPLICATE KEY UPDATE installation_date = VALUES(installation_date),"
                        + " trial_hash = VALUES(trial_hash), trial_last_check = VALUES(trial_last_check)")) {
            stmt.setString(1, machineId);
            stmt.setDate(2, java.sql.Date.valueOf(date));
            stmt.setString(3, hmac);
            stmt.setDate(4, java.sql.Date.valueOf(lastCheck));
            stmt.executeUpdate();
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

    /**
     * The MachineGuid, read by {@link MachineId} - which is this method, moved out
     * unchanged so the backup owner and the connected-machines list answer to the same
     * identity the licence is bound to.
     */
    private String getMachineId() {
        return MachineId.current().orElse(null);
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

            ensureTrialTableExists();
            TrialDbData dbData = getTrialDataFromDb(getMachineId());
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
            info.error = com.hamza.controlsfx.error.ErrorReporter.shared()
                    .report(LanguageManager.getInstance().getString("trial.error.load.info"), e)
                    .message();
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
                /*
                 * A licence for a different computer. This is NOT tampering, and treating
                 * it as tampering is how a shop bricked its second till: setting one up by
                 * copying the program folder brings license.dat with it, and under `strict`
                 * that counted a failure and exited - permanently, since MAX_FAILS is 1.
                 *
                 * The honest reading is "this machine is not licensed yet", which is what
                 * the trial path already says, so it falls through to it. A signature that
                 * does not verify is still tampering and still fails.
                 */
                result.valid = false;
                result.error = "Target machine does not match current machine.";
                log.warn("The licence file names another machine; this one runs on the trial until it has its own");
                return result;
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
            failAndExit(getMachineId(), message);
        }
        return result;
    }

    public boolean canAddItem() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("items", MAX_ITEMS, LanguageManager.getInstance().getString("trial.limit.items"));
    }

    public boolean canAddCustomer() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("custom", MAX_CUSTOMERS, LanguageManager.getInstance().getString("trial.limit.customers"));
    }

    public boolean canAddSale() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("total_sales", MAX_SALES, LanguageManager.getInstance().getString("trial.limit.sales"));
    }

    public boolean canAddPurchase() {
        if (getDisplayInfo().licenseValid) return true;
        return checkLimit("total_buy", MAX_PURCHASES, LanguageManager.getInstance().getString("trial.limit.purchases"));
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
        TrialDbData dbData = getTrialDataFromDb(machineId);
        if (dbData == null || dbData.date == null) return;
        saveInstallationData(dbData.date, machineId);
    }

    /**
     * @param machineId the machine to charge the failure to, or {@code null} to charge it
     *                  to nobody - which is right when the failure is that we could not
     *                  work out which machine this is
     */
    private void failAndExit(String machineId, String message) {
        recordTrialFailure(machineId);
        AllAlerts.alertError(message);
        Platform.exit();
        System.exit(0);
    }

    /**
     * Counts one failure against <b>this</b> machine.
     * <p>
     * It used to increment {@code company.trial_fail_count}, one row for the whole shop,
     * with {@link #MAX_FAILS} at 1 - so a second computer starting once left the first one
     * refusing to open for good. The counter is per machine now, which is the only thing
     * that makes a machine-bound licence usable on a shared database.
     */
    private void recordTrialFailure(String machineId) {
        if (machineId == null || machineId.isBlank()) {
            log.warn("Trial failure not recorded: this machine could not be identified");
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO trial_machine_state"
                        + " (machine_id, installation_date, trial_fail_count, trial_fail_last)"
                        + " VALUES (?, ?, 1, ?)"
                        + " ON DUPLICATE KEY UPDATE trial_fail_count = trial_fail_count + 1,"
                        + " trial_fail_last = VALUES(trial_fail_last)")) {
            java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
            stmt.setString(1, machineId);
            stmt.setDate(2, today);
            stmt.setDate(3, today);
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
