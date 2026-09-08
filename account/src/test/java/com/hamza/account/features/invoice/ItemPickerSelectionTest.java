package com.hamza.account.features.invoice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPickerSelectionTest {

    @Test
    void selectionsSurviveWhateverRowsTheCurrentPageContains() {
        ItemPickerSelection selection = new ItemPickerSelection();

        selection.select(7, "first page");
        selection.select(82, "second page");

        assertEquals(List.of(7, 82), selection.requests().stream()
                .map(ItemPickRequest::itemId).toList());
    }

    @Test
    void selectingTheSameItemTwiceDoesNotCreateDuplicateInvoiceRequests() {
        ItemPickerSelection selection = new ItemPickerSelection();

        assertTrue(selection.select(7, "item"));
        assertFalse(selection.select(7, "renamed row"));

        assertEquals(1, selection.size());
        assertEquals("item", selection.requests().getFirst().itemName());
    }

    @Test
    void toggleAndClearHaveOnePredictableRuleForMouseAndKeyboard() {
        ItemPickerSelection selection = new ItemPickerSelection();

        assertTrue(selection.toggle(7, "item"));
        assertFalse(selection.toggle(7, "item"));
        assertTrue(selection.isEmpty());

        selection.select(7, "one");
        selection.select(8, "two");
        selection.clear();

        assertTrue(selection.isEmpty());
    }

    @Test
    void returnedRequestsCannotMutateTheSelection() {
        ItemPickerSelection selection = new ItemPickerSelection();
        selection.select(7, "item");

        assertThrows(UnsupportedOperationException.class,
                () -> selection.requests().add(new ItemPickRequest(8, "other", 1)));
        assertEquals(1, selection.size());
    }
}
