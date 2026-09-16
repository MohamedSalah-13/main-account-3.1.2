package com.hamza.account.features.itemmerge;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemMergeSearchTest {

    private static ItemMergeCandidate row(int id, String name, String barcode, String group) {
        return new ItemMergeCandidate(id, name, barcode, 1, "قطعة", false,
                BigDecimal.TEN, BigDecimal.ZERO, group, 3, null);
    }

    private static final List<ItemMergeCandidate> ROWS = List.of(
            row(1, "باميه لافيستا", "6224003158122", "باميه"),
            row(2, "باميه لافسيتا", "6224003158382", "باميه"),
            row(3, "سمن كريستال", "6222000553728", "سمن"));

    @Test
    void noTextNarrowsNothing() {
        assertNull(ItemMergeSearch.groupsMatching(ROWS, "  "));
    }

    @Test
    void aMatchOnOneRowKeepsItsWholeGroup() {
        // Only row 1 contains "لافيستا"; row 2 is its misspelt twin and must stay beside it.
        assertEquals(Set.of("باميه"), ItemMergeSearch.groupsMatching(ROWS, "لافيستا"));
    }

    @Test
    void aBarcodeFindsItsGroup() {
        assertEquals(Set.of("سمن"), ItemMergeSearch.groupsMatching(ROWS, "553728"));
    }

    @Test
    void nothingMatchingIsAnEmptySetNotEverything() {
        assertEquals(Set.of(), ItemMergeSearch.groupsMatching(ROWS, "zzz"));
    }
}
