package com.hamza.account.features.users;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportRecoveryRequestAgeTest {

    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 9, 7, 14, 0, 0);

    @Test
    void aRequestInsideItsWindowAndTheClockToleranceIsFresh() {
        int edge = SupportRecoveryChallenge.VALID_FOR_MINUTES + SupportRecoveryRequestAge.CLOCK_TOLERANCE_MINUTES;

        assertEquals(SupportRecoveryRequestAge.FRESH, SupportRecoveryRequestAge.of(ISSUED, ISSUED));
        assertEquals(SupportRecoveryRequestAge.FRESH, SupportRecoveryRequestAge.of(ISSUED, ISSUED.plusMinutes(edge)));
        assertEquals(SupportRecoveryRequestAge.FRESH, SupportRecoveryRequestAge.of(ISSUED, ISSUED.minusMinutes(5)));
        assertFalse(SupportRecoveryRequestAge.FRESH.warns());
    }

    /** The habit the script's own comment warns about: signing a request kept from an earlier call. */
    @Test
    void aRequestPastItsWindowAndTheToleranceProbablyExpired() {
        int edge = SupportRecoveryChallenge.VALID_FOR_MINUTES + SupportRecoveryRequestAge.CLOCK_TOLERANCE_MINUTES;

        assertEquals(SupportRecoveryRequestAge.PROBABLY_EXPIRED,
                SupportRecoveryRequestAge.of(ISSUED, ISSUED.plusMinutes(edge + 1)));
        assertTrue(SupportRecoveryRequestAge.PROBABLY_EXPIRED.warns());
    }

    @Test
    void aRequestFromAheadOfThisClockBeyondTheToleranceIsFlagged() {
        assertEquals(SupportRecoveryRequestAge.FROM_THE_FUTURE, SupportRecoveryRequestAge.of(ISSUED, ISSUED.minusMinutes(6)));
        assertEquals(-6, SupportRecoveryRequestAge.minutesSince(ISSUED, ISSUED.minusMinutes(6)));
    }
}
