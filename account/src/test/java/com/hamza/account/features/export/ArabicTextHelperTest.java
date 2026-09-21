package com.hamza.account.features.export;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.Bidi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    /**
     * A product code is one word, not a word and a number. "نوته NC7013" printed on the invoice as
     * "7013NC": the digits were isolated on their own, and an isolate is a separate run the
     * right-to-left paragraph then places before the letters. 45 of 1,836 items on a real
     * database carry such a code in their name.
     */
    @Test
    void aCodeMixingLettersAndDigitsIsNotSplit() {
        String shaped = ArabicTextHelper.shape("نوته NC7013");
        assertTrue(shaped.contains("NC7013"), shaped);
        assertFalse(shaped.contains("7013NC"), shaped);

        assertTrue(ArabicTextHelper.shape("قلم A4B2").contains("A4B2"));
        assertTrue(ArabicTextHelper.shape("علبة 12X").contains("12X"));
        assertEquals(isolated("X9"), ArabicTextHelper.isolateNumbers("X9"), "a code is isolated whole");
    }

    /**
     * A Latin word and the digits after it are one left-to-right piece. The digits used to be
     * isolated alone, and the right-to-left paragraph then set them apart from their word: the thermos
     * "owala-5250" printed "5250-owala" on every invoice while every screen showed "owala-5250".
     */
    @Test
    void aLatinWordKeepsTheDigitsAfterIt() {
        String code = ArabicTextHelper.shape("ترمس 510مل owala-5250");
        assertTrue(code.contains("owala-5250"), code);
        assertFalse(code.contains("5250-owala"), code);

        String size = ArabicTextHelper.shape("بيبسي Pepsi 330 مل");
        assertTrue(size.contains("Pepsi 330"), size);
        assertFalse(size.contains("330 Pepsi"), size);

        assertTrue(ArabicTextHelper.shape("راديو RX-929/939").contains("RX-929/939"));
        assertTrue(ArabicTextHelper.shape("مضرب usb 2*1").contains("usb 2*1"));
    }

    /**
     * The rule the paper keeps is the screen's: whatever JavaFX draws for a name - plain Unicode
     * bidi, with no isolates - is what the PDF prints, for a Latin word with digits after it and for
     * digits before one alike. The names are real, from a database of 1,840 items; on it, every name
     * this change touched came to agree with the screen and none that agreed stopped.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "ترمس 510مل owala-5250",
            "ترمس fashion 500ml-240",
            "راديو RX-929/939",
            "قلم رصاص NO:HW_1791",
            "كشكول Subject 1 96 مبترا ورقة",
            "سبورة تعليمية GE2535 35*25",
            "قصافة ع*12 NO: 3211",
            "مج اوهايو 6*1 21 CL",
            "جلو ستيك 8 جرام ع*30 PASCO",
            "برطمان براية اشكال SH_103 20*ع",
            "استيكر اكليرك ديربي 74-AY",
            "قلم CD رقم 6130_S",
            "iPhone 15 Pro",
            "Total: 5",
    })
    void aNameWithLatinInItPrintsWhatTheScreenDraws(String name) throws Exception {
        assertEquals(screen(name), ArabicTextHelper.shape(name));
    }

    /**
     * A line that is all English keeps its left-to-right paragraph. Asking the isolated text for its
     * direction would skip the one Latin run it holds and set "Total: 5" right to left.
     */
    @Test
    void anEnglishLineStaysLeftToRight() {
        assertEquals("Total: 5", ArabicTextHelper.shape("Total: 5"));
        assertEquals("From 2025-10-01 to 2026-09-11", ArabicTextHelper.shape("From 2025-10-01 to 2026-09-11"));
        assertEquals("Balance -5", ArabicTextHelper.shape("Balance -5"));
    }

    /** Plain Unicode bidi over the shaped text - the order a JavaFX label draws it in. */
    private static String screen(String text) throws Exception {
        String shaped = new ArabicShaping(ArabicShaping.LETTERS_SHAPE | ArabicShaping.LENGTH_GROW_SHRINK
                | ArabicShaping.TEXT_DIRECTION_LOGICAL).shape(text);
        Bidi bidi = new Bidi(shaped.length(), 0);
        bidi.setPara(shaped, Bidi.getBaseDirection(text) == Bidi.LTR ? (byte) 0 : (byte) 1, null);
        return bidi.writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS);
    }

    private static String isolated(String number) {
        return LEFT_TO_RIGHT_ISOLATE + number + POP_DIRECTIONAL_ISOLATE;
    }
}
