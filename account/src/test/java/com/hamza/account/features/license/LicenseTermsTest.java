package com.hamza.account.features.license;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseTermsTest {

    private static final LocalDate ISSUED = LocalDate.of(2026, 10, 1);
    private static final LocalDate UPDATES = LocalDate.of(2027, 10, 1);

    @Test
    void thePerpetualTextIsPinned() {
        LicenseTerms terms = new LicenseTerms("PC-01", "C-0042", "PRO", ISSUED, UPDATES, null);
        assertEquals("HAMZA_LICENSE2|PC-01|C-0042|PRO|2026-10-01|2027-10-01|-", terms.encode());
        assertTrue(terms.perpetual());
    }

    @Test
    void whatIsEncodedIsReadBackUnchanged() {
        LicenseTerms subscription = new LicenseTerms("PC-01", "C-0042", "PRO", ISSUED, UPDATES,
                LocalDate.of(2027, 9, 30));
        assertEquals(subscription, LicenseTerms.parse(subscription.encode()).orElseThrow());
        assertFalse(subscription.perpetual());
    }

    /**
     * The three texts the two keys sign must not be readable as one another. A licence file
     * every customer already holds says {@code HAMZA_ACCOUNT|machine}, and a recovery
     * response says {@code HAMZA_RECOVERY|...}; neither may parse as server-issued terms.
     */
    @Test
    void theOtherSignedTextsAreNotLicenceTerms() {
        assertTrue(LicenseTerms.parse("HAMZA_ACCOUNT|PC-01").isEmpty());
        assertTrue(LicenseTerms.parse("HAMZA_RECOVERY|PC-01|nonce|2026-09-07 14:05:33").isEmpty());
        assertTrue(LicenseTerms.parse("HAMZA_ACCOUNT|PC-01|C-0042|PRO|2026-10-01|2027-10-01|-").isEmpty());
    }

    /** A format that grows takes a new tag; an eighth field is not quietly ignored. */
    @Test
    void theFieldCountIsStrict() {
        assertTrue(LicenseTerms.parse("HAMZA_LICENSE2|PC-01|C-0042|PRO|2026-10-01|2027-10-01").isEmpty());
        assertTrue(LicenseTerms.parse("HAMZA_LICENSE2|PC-01|C-0042|PRO|2026-10-01|2027-10-01|-|extra").isEmpty());
    }

    @Test
    void rubbishIsEmptyRatherThanThrown() {
        assertTrue(LicenseTerms.parse(null).isEmpty());
        assertTrue(LicenseTerms.parse("").isEmpty());
        assertTrue(LicenseTerms.parse("HAMZA_LICENSE2||C-0042|PRO|2026-10-01|2027-10-01|-").isEmpty());
        assertTrue(LicenseTerms.parse("HAMZA_LICENSE2|PC-01|C-0042|PRO|01/10/2026|2027-10-01|-").isEmpty());
        assertTrue(LicenseTerms.parse("HAMZA_LICENSE2|PC-01|C-0042|PRO|2026-10-01|2027-10-01|never").isEmpty());
    }

    /** A separator inside a field would shift every field after it once encoded. */
    @Test
    void aFieldMayNotCarryTheSeparator() {
        assertThrows(IllegalArgumentException.class,
                () -> new LicenseTerms("PC-01", "C|42", "PRO", ISSUED, UPDATES, null));
    }
}
