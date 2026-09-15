package com.hamza.account.features.users;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Support's own record of every response it has signed, kept on the machine that signs.
 *
 * <p>The customer's database records the recovery ({@code support_recovery_audit}); nothing
 * recorded the other end - who asked, and who at support decided that the caller was
 * entitled to the administrator account. A signature is the whole authorisation, and the
 * weak point of a signed challenge is not the cryptography but the telephone call: an
 * employee locked out of the owner's account can generate a request as easily as the owner.
 * So the signing screen asks for the customer and for confirmation that the caller was
 * checked, and this is where that answer is kept.
 *
 * <p>A plain CSV, UTF-8 with a byte-order mark so a spreadsheet opens the Arabic correctly.
 * Append-only by use, not by enforcement - it is a support desk's notebook, not evidence.
 */
public final class SupportRecoverySigningLog {

    static final String HEADER = "signed_at,customer,machine,nonce,issued_at";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path file;

    public SupportRecoverySigningLog(Path file) {
        this.file = file;
    }

    /** {@code %APPDATA%\AccountK} on Windows, a folder in the home directory elsewhere. */
    public static SupportRecoverySigningLog forCurrentUser() {
        String appData = System.getenv("APPDATA");
        Path folder = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".accountk")
                : Path.of(appData, "AccountK");
        return new SupportRecoverySigningLog(folder.resolve("support-recovery-signatures.csv"));
    }

    public Path file() {
        return file;
    }

    public void append(LocalDateTime signedAt, String customer, SupportRecoveryChallenge challenge) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        StringBuilder text = new StringBuilder();
        if (!Files.exists(file)) {
            text.append('﻿').append(HEADER).append("\r\n");
        }
        text.append(csv(STAMP.format(signedAt))).append(',')
                .append(csv(customer)).append(',')
                .append(csv(challenge.machineId())).append(',')
                .append(csv(challenge.nonce())).append(',')
                .append(csv(STAMP.format(challenge.issuedAt()))).append("\r\n");
        Files.writeString(file, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Quoted always, quotes doubled; and a leading {@code = + - @} is defused, because a
     * customer name is typed by a person and the file is opened in a spreadsheet.
     */
    static String csv(String value) {
        String text = value == null ? "" : value.strip().replaceAll("[\\r\\n]+", " ");
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
