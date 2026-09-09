package com.hamza.account.features.totals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void aResultThatFitsOnOnePageHasOnlyThatPage() {
        assertEquals(0, PageJump.targetPage("7", 1).orElseThrow());
        assertEquals(0, PageJump.targetPage("7", 0).orElseThrow());
    }
}
