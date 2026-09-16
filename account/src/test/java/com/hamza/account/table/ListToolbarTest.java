package com.hamza.account.table;

import com.hamza.account.table.ListToolbar.Slot;
import com.hamza.controlsfx.language.LanguageManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order of a list screen's bar, which is the whole reason the class exists: a screen names
 * its controls in whatever order its code happens to build them, and the bar comes out the same.
 */
class ListToolbarTest {

    @Test
    void theOrderIsTheSlotsOrderWhateverOrderTheyWereNamedIn() {
        List<List<Slot>> groups = ListToolbar.arrange(List.of(
                Slot.EXTRA, Slot.VIEW, Slot.PRINT, Slot.REFRESH, Slot.CLEAR, Slot.FILTERS, Slot.SEARCH,
                Slot.SEARCH_FIELD, Slot.EXPORT));

        assertEquals(List.of(
                List.of(Slot.SEARCH_FIELD, Slot.SEARCH, Slot.FILTERS, Slot.CLEAR),
                List.of(Slot.REFRESH, Slot.PRINT, Slot.EXPORT, Slot.VIEW),
                List.of(Slot.EXTRA)), groups);
    }

    @Test
    void aGroupWithNothingInItLeavesNoSeparatorBehind() {
        // A screen with nothing to find by: the list actions and the extras, one separator.
        assertEquals(List.of(List.of(Slot.REFRESH, Slot.VIEW), List.of(Slot.EXTRA)),
                ListToolbar.arrange(List.of(Slot.VIEW, Slot.EXTRA, Slot.REFRESH)));
    }

    @Test
    void nothingNamedIsAnEmptyBar() {
        assertTrue(ListToolbar.arrange(List.of()).isEmpty());
    }

    @Test
    void theToggleSaysTheListIsFilteredEvenWithThePanelClosed() {
        LanguageManager language = LanguageManager.getInstance();
        assertEquals(language.getString("invoice.search.filters"), ListToolbar.filtersCaption(0));
        assertEquals(language.getString("invoice.search.filters.count", 2), ListToolbar.filtersCaption(2));
    }
}
