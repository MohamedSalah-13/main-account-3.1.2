package com.hamza.account.features.returns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnSourceSearchTest {

    @Test
    void readsAPlainDocumentNumber() {
        assertEquals(742, ReturnSourceSearch.documentNumber("742"));
        assertEquals("", ReturnSourceSearch.partyText("742"));
    }

    @Test
    void readsTheDigitsAnArabicKeyboardProduces() {
        assertEquals(742, ReturnSourceSearch.documentNumber("٧٤٢"));
    }

    @Test
    void aNameIsANameAndNamesNoNumber() {
        assertEquals(0, ReturnSourceSearch.documentNumber("محمد صلاح"));
        assertEquals("محمد صلاح", ReturnSourceSearch.partyText("محمد صلاح"));
    }

    @Test
    void bothAtOnceNarrowBothWays() {
        assertEquals(12, ReturnSourceSearch.documentNumber("محمد 12"));
        assertEquals("محمد", ReturnSourceSearch.partyText("محمد 12"));
    }

    @Test
    void aNameCarryingDigitsStillSearchesByItsLetters() {
        // "مخزن 2" is a name with a digit in it; the digit also narrows by number, which is
        // the honest reading of an ambiguous box - both conditions are ORed against nothing.
        assertEquals("مخزن", ReturnSourceSearch.partyText("مخزن 2"));
        assertEquals(2, ReturnSourceSearch.documentNumber("مخزن 2"));
    }

    @Test
    void blankAndZeroAndNegativeNameNothing() {
        assertTrue(ReturnSourceSearch.isEmpty(null));
        assertTrue(ReturnSourceSearch.isEmpty(""));
        assertTrue(ReturnSourceSearch.isEmpty("   "));
        assertEquals(0, ReturnSourceSearch.documentNumber("0"));
    }

    @Test
    void punctuationRoundANumberIsNotAName() {
        // "-4" is 4 with a stray character, not a party whose name contains a hyphen - searching
        // names for "%-%" would quietly narrow the list by something nobody typed on purpose.
        assertEquals(4, ReturnSourceSearch.documentNumber("-4"));
        assertEquals("", ReturnSourceSearch.partyText("-4"));
    }

    @Test
    void anythingTypedIsNotEmpty() {
        assertFalse(ReturnSourceSearch.isEmpty("7"));
        assertFalse(ReturnSourceSearch.isEmpty("أحمد"));
    }

    @Test
    void aNumberTooBigForADocumentIsNoNumber() {
        assertEquals(0, ReturnSourceSearch.documentNumber("99999999999999"));
    }
}
