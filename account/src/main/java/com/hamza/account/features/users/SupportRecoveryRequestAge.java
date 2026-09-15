package com.hamza.account.features.users;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * How old a request looks from the desk that is about to sign it.
 *
 * <p>Measured against support's own clock, which is not the clock that expires the request
 * - that is the customer's database - so this warns and never refuses. Two machines in the
 * same country are usually minutes apart at most; {@link #CLOCK_TOLERANCE_MINUTES} absorbs
 * that before calling a request stale or from the future. The warning matters for the one
 * habit the script's comment already names: signing a request kept from an earlier call.
 *
 * <p>Every message takes two {@code %d} arguments, minutes and the validity window, so the
 * screen formats all three the same way; {@code SupportRecoveryResponseCheckTest} checks the
 * keys and their placeholders against the bundles.
 */
public enum SupportRecoveryRequestAge {

    FRESH("support.recovery.signer.request.age"),
    PROBABLY_EXPIRED("support.recovery.signer.request.old"),
    FROM_THE_FUTURE("support.recovery.signer.request.future");

    static final int CLOCK_TOLERANCE_MINUTES = 5;

    private final String messageKey;

    SupportRecoveryRequestAge(String messageKey) {
        this.messageKey = messageKey;
    }

    public static SupportRecoveryRequestAge of(LocalDateTime issuedAt, LocalDateTime now) {
        long minutes = minutesSince(issuedAt, now);
        if (minutes < -CLOCK_TOLERANCE_MINUTES) return FROM_THE_FUTURE;
        if (minutes > SupportRecoveryChallenge.VALID_FOR_MINUTES + CLOCK_TOLERANCE_MINUTES) return PROBABLY_EXPIRED;
        return FRESH;
    }

    /** Whole minutes from issue to now; negative when the request claims a later moment. */
    public static long minutesSince(LocalDateTime issuedAt, LocalDateTime now) {
        return Duration.between(issuedAt, now).toMinutes();
    }

    public String messageKey() {
        return messageKey;
    }

    public boolean warns() {
        return this != FRESH;
    }
}
