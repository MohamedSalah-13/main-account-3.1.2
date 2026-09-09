package com.hamza.account.features.totals;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboFilterTest {

    private static final List<String> NAMES = List.of(
            "الكل", "أحمد حامد", "احمد حسن", "حمزة عبد الله", "يحيى مرسي", "Ahmed Ali");

    @Test
    void nothingTypedLeavesTheWholeList() {
        assertEquals(NAMES, ComboFilter.matching(NAMES, null));
        assertEquals(NAMES, ComboFilter.matching(NAMES, "   "));
    }

    @Test
    void aFragmentMatchesAnywhereInTheName() {
        assertEquals(List.of("حمزة عبد الله"), ComboFilter.matching(NAMES, "عبد"));
        assertEquals(List.of("أحمد حامد"), ComboFilter.matching(NAMES, "حامد"));
    }

    /** The keyboard habit that writes أحمد and احمد must not hide one from the other. */
    @Test
    void hamzaCarriersAreTheSameLetterForSearching() {
        assertEquals(List.of("أحمد حامد", "احمد حسن"), ComboFilter.matching(NAMES, "احمد"));
        assertEquals(List.of("أحمد حامد", "احمد حسن"), ComboFilter.matching(NAMES, "أحمد"));
    }

    @Test
    void taMarbutaAndAlefMaqsuraAreFoldedToo() {
        assertEquals(List.of("حمزة عبد الله"), ComboFilter.matching(NAMES, "حمزه"));
        assertEquals(List.of("يحيى مرسي"), ComboFilter.matching(NAMES, "يحيي"));
    }

    @Test
    void diacriticsAndTatweelAreIgnoredOnBothSides() {
        assertTrue(ComboFilter.matches("أَحْمَد", "احمد"));
        assertTrue(ComboFilter.matches("احمــــد", "احمد"));
        assertEquals("احمد", ComboFilter.normalize("أَحْمــــَد"));
    }

    @Test
    void latinNamesMatchWithoutRegardToCase() {
        assertEquals(List.of("Ahmed Ali"), ComboFilter.matching(NAMES, "ahmed a"));
    }

    @Test
    void spacingIsNotPartOfAName() {
        assertTrue(ComboFilter.matches("حمزة  عبد   الله", " حمزة عبد الله "));
        assertFalse(ComboFilter.matches("أحمد حامد", "حسن"));
    }

    /** Accepting a typed name is only safe when it names one person. */
    @Test
    void aFragmentIsAcceptedOnlyWhenItNamesExactlyOne() {
        assertEquals("حمزة عبد الله", ComboFilter.soleMatch(NAMES, "حمزه"));
        assertNull(ComboFilter.soleMatch(NAMES, "احمد"), "two candidates name nobody");
        assertNull(ComboFilter.soleMatch(NAMES, "لا أحد"));
    }
}
