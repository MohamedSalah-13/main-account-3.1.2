package com.hamza.account.features.users;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

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

    public static SupportRecoveryChallenge issue(String machineId, LocalDateTime now) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return new SupportRecoveryChallenge(HexFormat.of().formatHex(bytes).toUpperCase(), machineId,
                now.withNano(0));
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
