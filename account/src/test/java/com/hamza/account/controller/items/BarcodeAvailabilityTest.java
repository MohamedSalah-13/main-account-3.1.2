package com.hamza.account.controller.items;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-code question the item screen asks while the user is still typing.
 *
 * <p>No JavaFX here: the lookup is a function and the edited item an {@code IntSupplier},
 * which is the reason the logic sits in its own class rather than inside the controller.
 */
class BarcodeAvailabilityTest {

    /** A catalogue where 100 belongs to "شاي" and 200 to "سكر". */
    private static final Map<String, String> CATALOGUE = Map.of("100", "شاي", "200", "سكر");

    private static BarcodeAvailability availability(int editedItemId) {
        return new BarcodeAvailability(
                (code, itemId) -> itemId == 7 ? null : CATALOGUE.get(code), () -> editedItemId);
    }

    @Test
    void namesTheItemHoldingTheCode() throws Exception {
        assertEquals("شاي", availability(0).takenBy("100"));
        assertEquals("سكر", availability(0).takenBy("200"));
    }

    @Test
    void aFreeCodeIsNotHeldByAnyone() throws Exception {
        assertNull(availability(0).takenBy("999"));
    }

    @Test
    void aBlankCodeIsNeverAskedAbout() throws Exception {
        assertNull(availability(0).takenBy(null));
        assertNull(availability(0).takenBy(""));
        assertNull(availability(0).takenBy("   "));
    }

    /**
     * The code is trimmed before it is asked about. The screen used to compare codes as
     * typed while the database was asked about the trimmed form, so "100 " and "100"
     * passed as two different codes and then collided on the unique index.
     */
    @Test
    void theCodeIsTrimmedBeforeItIsAskedAbout() throws Exception {
        assertEquals("شاي", availability(0).takenBy("  100  "));
    }

    /** An item does not clash with itself: item 7 is the one being edited here. */
    @Test
    void theItemBeingEditedDoesNotHoldTheCodeAgainstItself() throws Exception {
        assertNull(availability(7).takenBy("100"));
    }

    @Test
    void requireFreeRefusesATakenCodeAndSaysWhichItemHasIt() {
        Exception refusal = assertThrows(Exception.class, () -> availability(0).requireFree("100"));

        assertTrue(refusal.getMessage().contains("100"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("شاي"), refusal.getMessage());
    }

    @Test
    void requireFreeAllowsACodeNobodyHolds() {
        assertDoesNotThrow(() -> availability(0).requireFree("999"));
        assertDoesNotThrow(() -> availability(0).requireFree(" "));
    }
}
