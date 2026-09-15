package com.hamza.account.features.users;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportRecoveryChallengeTest {

    private static final LocalDateTime MOMENT = LocalDateTime.of(2026, 9, 7, 14, 5, 33);

    @Test
    void theSignedTextBindsTheMachineTheNonceAndTheMoment() {
        SupportRecoveryChallenge challenge = new SupportRecoveryChallenge("A1B2C3D4E5F60718", "PC-01", MOMENT);

        assertEquals("HAMZA_RECOVERY|PC-01|A1B2C3D4E5F60718|2026-09-07 14:05:33", challenge.signedText());
    }

    /**
     * {@code license.dat} is signed by the same key over {@code HAMZA_ACCOUNT|<machine>}.
     * Without a tag of our own a customer's own licence file would verify here, and every
     * customer holding a licence would hold a way into the administrator account.
     */
    @Test
    void theTagIsNotTheOneTheLicenceUses() {
        String signed = new SupportRecoveryChallenge("N", "PC-01", MOMENT).signedText();

        assertTrue(signed.startsWith("HAMZA_RECOVERY|"));
        assertNotEquals("HAMZA_ACCOUNT", signed.split("\\|")[0]);
    }

    @Test
    void aNonceIsNotReissued() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(SupportRecoveryChallenge.issue("PC-01", MOMENT).nonce());
        }

        assertEquals(500, seen.size());
    }

    /**
     * The stored column has no sub-second part, so a challenge carrying one would never
     * equal the row it was issued from and every response would be refused.
     */
    @Test
    void theIssueTimeIsTruncatedToTheSecondTheDatabaseKeeps() {
        SupportRecoveryChallenge challenge =
                SupportRecoveryChallenge.issue("PC-01", MOMENT.withNano(123_456_789));

        assertEquals(0, challenge.issuedAt().getNano());
        assertEquals(MOMENT, challenge.issuedAt());
    }

    @Test
    void whatTheOperatorSendsCarriesTheThreeFieldsAndNoTag() {
        String display = new SupportRecoveryChallenge("A1B2", "PC-01", MOMENT).displayText();

        assertEquals("PC-01 | A1B2 | 2026-09-07 14:05:33", display);
    }

    /**
     * The signing screen reads the request back out of what the customer's window displayed.
     * Whatever it parses has to rebuild the very bytes the customer's database will compare,
     * or support signs something that is refused on arrival.
     */
    @Test
    void whatTheOperatorSendsReadsBackToTheSameSignedText() {
        SupportRecoveryChallenge sent = SupportRecoveryChallenge.issue("PC-01", MOMENT);

        SupportRecoveryChallenge received = SupportRecoveryChallenge.parseDisplayText(sent.displayText()).orElseThrow();

        assertEquals(sent, received);
        assertEquals(sent.signedText(), received.signedText());
    }

    /** What a chat application does to a pasted line: spacing, surrounding blanks, case. */
    @Test
    void aRequestSurvivesTheWayItArrivesThroughAMessage() {
        SupportRecoveryChallenge received = SupportRecoveryChallenge
                .parseDisplayText("  PC-01|a1b2c3d4e5f60718 |2026-09-07 14:05:33\n").orElseThrow();

        assertEquals("HAMZA_RECOVERY|PC-01|A1B2C3D4E5F60718|2026-09-07 14:05:33", received.signedText());
    }

    @Test
    void textThatIsNotARequestIsNotRead() {
        for (String text : new String[]{null, "", "PC-01 | A1B2C3D4E5F60718",
                "PC-01 | A1B2C3D4E5F60718 | 2026-09-07 14:05:33 | extra",
                " | A1B2C3D4E5F60718 | 2026-09-07 14:05:33",
                "PC-01 | A1B2 | 2026-09-07 14:05:33",
                "PC-01 | Z1B2C3D4E5F60718 | 2026-09-07 14:05:33",
                "PC-01 | A1B2C3D4E5F60718 | 2026-09-07",
                "PC-01 | A1B2C3D4E5F60718 | 07/09/2026 14:05:33"}) {
            assertFalse(SupportRecoveryChallenge.parseDisplayText(text).isPresent(), String.valueOf(text));
        }
    }

    @Test
    void aNonceIsSixteenUpperCaseHexDigits() {
        assertTrue(SupportRecoveryChallenge.newNonce().matches("[0-9A-F]{16}"));
    }
}
