package com.hamza.account.features.license;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Whether a file is the older licence, {@code HAMZA_ACCOUNT|machine} - read the way the older
 * reader in {@code TrialManager} reads it, and no further.
 *
 * <p>This is what decides which files that reader is shown at all. It checks with the release
 * key and, at start-up, treats a signature it cannot verify as tampering, which ends the install
 * for good. It used to be shown every file that did not read as the server's format - so a server
 * file with one of its first twenty characters changed (they carry its tag), a download cut short,
 * or any file that was no licence at all, cost the install its one allowed failure. It is now shown
 * only a file whose text is its own, and everything else is judged by {@link LicenseEvaluator},
 * which charges nothing for anything.
 *
 * <p>The reading copies the older reader's own: a byte-order mark skipped, the text before the
 * first dot with ASCII whitespace trimmed at its ends, base64 decoded strictly, and a tag before
 * the first {@code |}. Every file that reader could ever accept therefore still reaches it, and an
 * older licence with its machine edited is still its business to call tampering. The signature
 * half is not looked at: that is the older reader's question, and the older reader asks it.
 *
 * <p>It is a class of its own rather than a method of {@link LicenseEnvelope} because that record
 * and {@link LicenseTerms} are copied into the licence server character for character.
 */
final class OlderLicenceFile {

    static final String TAG = "HAMZA_ACCOUNT|";

    private OlderLicenceFile() {
    }

    static boolean claims(byte[] fileBytes) {
        if (fileBytes == null) {
            return false;
        }
        int start = hasUtf8ByteOrderMark(fileBytes) ? 3 : 0;
        int dot = indexOf(fileBytes, (byte) '.', start);
        if (dot < 0) {
            return false;
        }
        String payloadHalf = new String(trimAsciiWhitespace(Arrays.copyOfRange(fileBytes, start, dot)),
                StandardCharsets.US_ASCII);
        try {
            byte[] payload = Base64.getDecoder().decode(payloadHalf);
            return new String(payload, StandardCharsets.UTF_8).startsWith(TAG);
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    private static boolean hasUtf8ByteOrderMark(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private static int indexOf(byte[] bytes, byte target, int from) {
        for (int i = from; i < bytes.length; i++) {
            if (bytes[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] trimAsciiWhitespace(byte[] bytes) {
        int start = 0;
        int end = bytes.length;
        while (start < end && isAsciiWhitespace(bytes[start])) {
            start++;
        }
        while (end > start && isAsciiWhitespace(bytes[end - 1])) {
            end--;
        }
        return Arrays.copyOfRange(bytes, start, end);
    }

    private static boolean isAsciiWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }
}
