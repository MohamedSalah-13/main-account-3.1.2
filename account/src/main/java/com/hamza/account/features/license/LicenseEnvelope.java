package com.hamza.account.features.license;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * The file as it sits on disk: {@code BASE64(payload).BASE64(signature)} - the shape
 * {@code license.dat} has always had, so one file name serves both formats.
 *
 * <p>Parsing here is stricter than the older reader's, which tolerates a raw binary
 * signature because people produced those files by hand with OpenSSL. This one is written by
 * our own server, so anything that is not clean base64 on both sides is simply not a licence.
 */
public record LicenseEnvelope(String payload, byte[] signature) {

    /** Empty when the bytes are not two base64 halves around a single dot. */
    public static Optional<LicenseEnvelope> parse(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return Optional.empty();
        }
        String text = new String(fileBytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        text = text.strip();
        int dot = text.indexOf('.');
        if (dot <= 0 || dot == text.length() - 1 || text.indexOf('.', dot + 1) >= 0) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getDecoder().decode(text.substring(0, dot).strip());
            byte[] signature = Base64.getDecoder().decode(text.substring(dot + 1).strip());
            return Optional.of(new LicenseEnvelope(new String(payload, StandardCharsets.UTF_8), signature));
        } catch (IllegalArgumentException notBase64) {
            return Optional.empty();
        }
    }

    /**
     * Whether these bytes claim to be a server-issued licence - asked <b>before</b> any
     * signature is checked, because the answer decides which key the check is made with.
     *
     * <p>It does <b>not</b> decide which reader a file is handed to. A server file with one of
     * its first twenty characters changed - they carry the tag - answers false here, and the
     * older reader, which ends an install over a signature it cannot verify, must still never
     * see it; so that reader is shown only a file that is positively its own
     * ({@code OlderLicenceFile}), and a file answering true here never qualifies.
     */
    public static boolean claimsServerFormat(byte[] fileBytes) {
        return parse(fileBytes)
                .map(envelope -> envelope.payload().startsWith(LicenseTerms.TAG + "|"))
                .orElse(false);
    }

    /** The on-disk text for a payload and its signature; what the server hands out. */
    public static String encode(String payload, byte[] signature) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(signature);
    }
}
