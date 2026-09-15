package com.hamza.account.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Reads the release private key a technician points a tool at, for one signing operation.
 *
 * <p>Nothing here stores, caches or copies the key: every caller reads the file, signs, and
 * lets the key go. It is the one loader for both things that key signs from a screen - a
 * client edition and a support-recovery response - so a format either of them accepts is a
 * format both accept.
 *
 * <p>Only PKCS#8 ({@code BEGIN PRIVATE KEY}) is read, which is what the README's
 * {@code openssl genpkey} produces. A PKCS#1 file ({@code BEGIN RSA PRIVATE KEY}) is refused
 * with its own message rather than as "unreadable", because it is the one mistake a person
 * holding the right key can make and fix with a single openssl command.
 */
public final class PrivateKeyFiles {

    private static final String PKCS8_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_END = "-----END PRIVATE KEY-----";
    private static final String PKCS1_BEGIN = "-----BEGIN RSA PRIVATE KEY-----";

    private PrivateKeyFiles() {
    }

    public static PrivateKey readRsa(Path path) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("private key file is missing");
        }
        return parseRsa(Files.readString(path, StandardCharsets.US_ASCII));
    }

    public static PrivateKey parseRsa(String pem) throws Exception {
        if (pem.contains(PKCS1_BEGIN)) {
            throw new UnsupportedKeyFormatException();
        }
        if (!pem.contains(PKCS8_BEGIN)) {
            throw new IllegalArgumentException("only PKCS#8 private keys are supported");
        }
        String compact = pem.replace(PKCS8_BEGIN, "").replace(PKCS8_END, "").replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(compact);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    /** A PKCS#1 key: the right key, perhaps, in the one format this reader does not take. */
    public static final class UnsupportedKeyFormatException extends IllegalArgumentException {
        UnsupportedKeyFormatException() {
            super("PKCS#1 private key; convert it with: openssl pkcs8 -topk8 -nocrypt -in key.pem -out private_key.pem");
        }
    }
}
