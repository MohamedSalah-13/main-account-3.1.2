package com.hamza.account.features.export;

import org.junit.jupiter.api.Test;

import static com.hamza.account.features.export.ArabicTextHelper.LEFT_TO_RIGHT_ISOLATE;
import static com.hamza.account.features.export.ArabicTextHelper.POP_DIRECTIONAL_ISOLATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a PDF prints for a number, a date and a percentage, in a cell and inside a sentence.
 * <p>
 * Every case here printed wrongly until the numbers were isolated before the bidi pass: the
 * dates of a report's subtitle backwards, its rates with the percent sign in front, and every
 * negative amount in every table with its minus trailing.
 */
class ArabicTextHelperTest {

    /** "من" and "إلى" - "from" and "to" - the words a report's subtitle puts around its dates. */
    private static final String FROM = "من";
    private static final String TO = "إلى";

    @Test
    void aNegativeAmountKeepsItsSignInFront() {
        assertEquals("-11,995.00", ArabicTextHelper.shape("-11,995.00"));
        assertEquals("39,045.00", ArabicTextHelper.shape("39,045.00"));
    }

    @Test
    void aDateInsideAnArabicSentenceIsNotReversed() {
        String shaped = ArabicTextHelper.shape(FROM + " 2025-10-01 " + TO + " 2026-09-11");

        assertTrue(shaped.contains("2025-10-01"), shaped);
        assertTrue(shaped.contains("2026-09-11"), shaped);
        assertFalse(shaped.contains("01-10-2025"), shaped);
    }

    @Test
    void aPercentageKeepsItsSignAfterTheNumber() {
        String shaped = ArabicTextHelper.shape(FROM + ": 113.44%");

        assertTrue(shaped.contains("113.44%"), shaped);
        assertFalse(shaped.contains("%113.44"), shaped);
    }

    @Test
    void aTimeStaysWhole() {
        assertTrue(ArabicTextHelper.shape(FROM + " 13:37:32").contains("13:37:32"));
    }

    /** The isolates steer the bidi pass and must not reach the page, where a font draws a box. */
    @Test
    void theIsolatesAreRemovedFromWhatIsPrinted() {
        String shaped = ArabicTextHelper.shape(FROM + " 2025-10-01 " + TO + " -5");

        assertFalse(shaped.contains(LEFT_TO_RIGHT_ISOLATE), "LRI left in the output");
        assertFalse(shaped.contains(POP_DIRECTIONAL_ISOLATE), "PDI left in the output");
    }

    /** A full stop after a number ends the sentence; it is not part of the number. */
    @Test
    void aSentencesFullStopIsNotPulledIntoTheNumber() {
        assertEquals(isolated("120") + ".", ArabicTextHelper.isolateNumbers("120."));
        assertEquals(isolated("2025-10-01"), ArabicTextHelper.isolateNumbers("2025-10-01"));
        assertEquals(isolated("-11,995.00"), ArabicTextHelper.isolateNumbers("-11,995.00"));
    }

    private static String isolated(String number) {
        return LEFT_TO_RIGHT_ISOLATE + number + POP_DIRECTIONAL_ISOLATE;
    }
}
