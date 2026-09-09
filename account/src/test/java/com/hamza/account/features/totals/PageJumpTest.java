package com.hamza.account.features.totals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageJumpTest {

    @Test
    void whatTheOperatorCallsPageOneIsTheFirstPage() {
        assertEquals(0, PageJump.targetPage("1", 24).orElseThrow());
        assertEquals(4, PageJump.targetPage("5", 24).orElseThrow());
        assertEquals(23, PageJump.targetPage("24", 24).orElseThrow());
    }

    /** Typing 500 into a list of 24 pages means the last one, not an error message. */
    @Test
    void aNumberPastEitherEndIsTheNearestRealPage() {
        assertEquals(23, PageJump.targetPage("500", 24).orElseThrow());
        assertEquals(0, PageJump.targetPage("0", 24).orElseThrow());
        assertEquals(23, PageJump.targetPage("9999999999999", 24).orElseThrow(),
                "a number too large for an int must still land on a real page");
    }

    /**
     * A minus sign is not a page number. Clamping "-3" to the first page would be guessing
     * at something nobody means to type, so the field simply keeps the page it is on.
     */
    @Test
    void aSignedNumberIsNotAPageNumber() {
        assertTrue(PageJump.targetPage("-3", 24).isEmpty());
        assertTrue(PageJump.targetPage("+3", 24).isEmpty());
    }

    @Test
    void nothingTypedAndNothingNumericAskForNoJump() {
        assertTrue(PageJump.targetPage(null, 24).isEmpty());
        assertTrue(PageJump.targetPage("   ", 24).isEmpty());
        assertTrue(PageJump.targetPage("abc", 24).isEmpty());
        assertTrue(PageJump.targetPage("12a", 24).isEmpty());
    }

    /** An Arabic keyboard produces Arabic-Indic digits, and they are the same numbers. */
    @Test
    void arabicIndicDigitsAreDigits() {
        assertEquals(4, PageJump.targetPage("٥", 24).orElseThrow());
        assertEquals(11, PageJump.targetPage("١٢", 24).orElseThrow());
        assertEquals(6, PageJump.targetPage("۷", 24).orElseThrow());
    }

    // ---- what the field will accept while it is being typed -------------------------

    @Test
    void thePageFieldTakesDigitsAndNothingElse() {
        assertTrue(PageJump.isTypablePageText("7"));
        assertTrue(PageJump.isTypablePageText("124"));
        assertFalse(PageJump.isTypablePageText("a"));
        assertFalse(PageJump.isTypablePageText("12a"));
        assertFalse(PageJump.isTypablePageText("1 2"));
        assertFalse(PageJump.isTypablePageText("."));
    }

    /** The two the general numeric filter gets wrong for this field, in both directions. */
    @Test
    void aSignIsRefusedAndArabicIndicDigitsAreNot() {
        assertFalse(PageJump.isTypablePageText("+3"), "the parser refuses a sign, so the field must");
        assertFalse(PageJump.isTypablePageText("-3"));
        assertTrue(PageJump.isTypablePageText("٥"), "an Arabic keyboard produces these");
        assertTrue(PageJump.isTypablePageText("١٢"));
        assertTrue(PageJump.isTypablePageText("۷"));
    }

    /** A field that cannot be emptied cannot be changed to a different number. */
    @Test
    void anEmptyFieldIsAllowedOnTheWayToAnotherNumber() {
        assertTrue(PageJump.isTypablePageText(""));
        assertTrue(PageJump.isTypablePageText(null));
    }

    @Test
    void anAbsurdlyLongRunOfDigitsIsNotAPageNumber() {
        assertTrue(PageJump.isTypablePageText("123456789"));
        assertFalse(PageJump.isTypablePageText("1234567890"));
    }

    /**
     * The property that matters: nothing the field lets through is refused by the parser,
     * so a page box can never be typed into to no effect.
     */
    @Test
    void everythingTheFieldAcceptsTheParserUnderstands() {
        for (String typed : new String[]{"1", "9", "24", "500", "٥", "١٢", "۷", "123456789"}) {
            assertTrue(PageJump.isTypablePageText(typed), typed);
            assertTrue(PageJump.targetPage(typed, 24).isPresent(),
                    typed + " is typable but means nothing");
        }
    }

    @Test
    void aResultThatFitsOnOnePageHasOnlyThatPage() {
        assertEquals(0, PageJump.targetPage("7", 1).orElseThrow());
        assertEquals(0, PageJump.targetPage("7", 0).orElseThrow());
    }
}
