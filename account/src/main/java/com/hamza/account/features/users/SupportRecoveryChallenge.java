package com.hamza.account.features.users;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a locked-out machine asks, and what support signs to answer it.
 *
 * <p>Three things are bound into the text so that a signature authorises one recovery and
 * not a class of them:
 *
 * <ul>
 *   <li>the <b>machine</b>, so a response obtained for one customer does nothing at another;
 *   <li>the <b>nonce</b>, so it is answerable once - the row it was issued from is marked
 *       redeemed, and a kept copy of the response is then worthless;
 *   <li>the <b>issue time</b>, so an unused one stops being answerable after
 *       {@link #VALID_FOR_MINUTES}.
 * </ul>
 *
 * <p>The tag exists so a signature made for something else cannot be presented here.
 * {@code license.dat} is signed by the same key over {@code HAMZA_ACCOUNT|<machine>}: with
 * no tag of our own, a customer's own licence file would be a valid recovery response.
 */
public record SupportRecoveryChallenge(String nonce, String machineId, LocalDateTime issuedAt) {

    /** Long enough for a support call, short enough that a written-down one goes stale. */
    public static final int VALID_FOR_MINUTES = 30;

    static final String TAG = "HAMZA_RECOVERY";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern NONCE = Pattern.compile("[0-9A-F]{16}");
    private static final Pattern SEPARATOR = Pattern.compile("\\s*\\|\\s*");

    public static SupportRecoveryChallenge issue(String machineId, LocalDateTime now) {
        return new SupportRecoveryChallenge(newNonce(), machineId, now.withNano(0));
    }

    /**
     * Sixteen hexadecimal digits - short enough to read out over a telephone.
     *
     * <p>The service takes only this from Java and the issue time from the row it writes:
     * the database's clock is the one that later decides expiry, and a till's clock that
     * disagrees with it by one second would otherwise make every response unanswerable.
     */
    public static String newNonce() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }

    /**
     * Reads back what {@link #displayText()} wrote - the request as support receives it.
     *
     * <p>Forgiving about what a copy through a chat application does to text (spaces around
     * the bars, surrounding whitespace, a lower-case nonce) and strict about everything that
     * would change the signed bytes: exactly three fields, a machine name with no bar in it,
     * a nonce of the shape {@link #newNonce()} makes, and a time to the second. Empty when
     * the text is not a request, so a signing screen can say so before anything is signed.
     */
    public static Optional<SupportRecoveryChallenge> parseDisplayText(String text) {
        if (text == null) return Optional.empty();
        String[] parts = SEPARATOR.split(text.strip(), -1);
        if (parts.length != 3) return Optional.empty();
        String machine = parts[0].strip();
        String nonce = parts[1].strip().toUpperCase(Locale.ROOT);
        if (machine.isEmpty() || !NONCE.matcher(nonce).matches()) return Optional.empty();
        try {
            return Optional.of(new SupportRecoveryChallenge(nonce, machine,
                    LocalDateTime.parse(parts[2].strip(), STAMP)));
        } catch (DateTimeParseException notATime) {
            return Optional.empty();
        }
    }

    /**
     * The exact bytes signed and verified. Both sides derive it from the same fields rather
     * than passing a blob around, so a response cannot claim a machine or a moment that the
     * stored challenge does not agree with.
     */
    public String signedText() {
        return TAG + "|" + machineId + "|" + nonce + "|" + STAMP.format(issuedAt);
    }

    /** What the operator reads out or copies to support. */
    public String displayText() {
        return machineId + " | " + nonce + " | " + STAMP.format(issuedAt);
    }
}
