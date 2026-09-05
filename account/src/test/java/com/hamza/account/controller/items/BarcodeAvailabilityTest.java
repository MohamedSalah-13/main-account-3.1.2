package com.hamza.account.controller.items;

import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BarcodeAvailabilityTest {

    /** Records what the lookup was asked, so the trimming can be asserted. */
    private final List<String> asked = new ArrayList<>();

    private BarcodeAvailability availability(String holder, int editedItemId) {
        return new BarcodeAvailability((code, itemId) -> {
            asked.add(code + "/" + itemId);
            return holder;
        }, () -> editedItemId);
    }

    @Test
    void aFreeCodeIsNotTaken() throws Exception {
        assertNull(availability(null, 7).takenBy("123"));
        assertEquals(List.of("123/7"), asked);
    }

    @Test
    void theCodeIsTrimmedBeforeItIsLookedUp() throws Exception {
        availability(null, 7).takenBy("  123 ");
        assertEquals(List.of("123/7"), asked);
    }

    @Test
    void aBlankCodeIsNotLookedUpAtAll() throws Exception {
        var availability = availability("some item", 7);
        assertNull(availability.takenBy(null));
        assertNull(availability.takenBy("   "));
        assertTrue(asked.isEmpty());
    }

    @Test
    void aTakenCodeAnswersTheOtherItemsName() throws Exception {
        assertEquals("سكر", availability("سكر", 7).takenBy("123"));
    }

    @Test
    void requireFreeRefusesATakenCodeAndNamesTheItemHoldingIt() {
        var e = assertThrows(BusinessRuleException.class, () -> availability("سكر", 7).requireFree("123"));
        assertTrue(e.getMessage().contains("سكر"), e.getMessage());
        assertTrue(e.getMessage().contains("123"), e.getMessage());
    }

    @Test
    void requireFreeAcceptsAFreeCode() {
        assertDoesNotThrow(() -> availability(null, 7).requireFree("123"));
    }

    /** Availability over a fixed set of taken codes, for the generator's tests. */
    private BarcodeAvailability holding(java.util.Set<String> taken) {
        return new BarcodeAvailability(
                (code, itemId) -> taken.contains(code) ? "some item" : null, () -> 0);
    }

    @Test
    void aFreeStartingNumberIsTheGeneratedCode() throws Exception {
        assertEquals("10", holding(java.util.Set.of()).firstFreeFrom(10));
    }

    @Test
    void aTakenStartingNumberIsWalkedPast() throws Exception {
        assertEquals("13", holding(java.util.Set.of("10", "11", "12")).firstFreeFrom(10));
    }

    @Test
    void theWalkStopsAtFourteenDigits() throws Exception {
        // 99999999999999 is the longest code the screen accepts; one past it there
        // is nothing left to offer.
        assertNull(holding(java.util.Set.of("99999999999999")).firstFreeFrom(99999999999999L));
    }

    @Test
    void theWalkGivesUpRatherThanQueryingForEver() throws Exception {
        int[] calls = {0};
        var availability = new BarcodeAvailability((code, itemId) -> {
            calls[0]++;
            return "some item";
        }, () -> 0);

        assertNull(availability.firstFreeFrom(1));
        assertEquals(1000, calls[0]);
    }

    @Test
    void aStartBelowOneIsRaisedToOne() throws Exception {
        assertEquals("1", holding(java.util.Set.of()).firstFreeFrom(0));
    }

    // ---------------------------------------------------------------------------
    // The generator over the batch lookup - what the application actually uses.
    // The tests above cover the one-at-a-time fallback, which is the same walk.
    // ---------------------------------------------------------------------------

    /** Records each batch it was asked about, so the number of queries can be asserted. */
    private final List<List<String>> batches = new ArrayList<>();

    private BarcodeAvailability batched(java.util.Set<String> taken, int editedItemId) {
        return new BarcodeAvailability(
                (code, itemId) -> {
                    throw new AssertionError("the batch lookup should have answered this");
                },
                (codes, itemId) -> {
                    batches.add(List.copyOf(codes));
                    asked.add("batch/" + itemId);
                    var found = new java.util.HashSet<>(codes);
                    found.retainAll(taken);
                    return found;
                },
                () -> editedItemId);
    }

    @Test
    void aFreeStartingNumberCostsOneQuery() throws Exception {
        assertEquals("10", batched(java.util.Set.of(), 0).firstFreeFrom(10));
        assertEquals(1, batches.size());
    }

    @Test
    void takenNumbersAreWalkedPastWithinTheSameQuery() throws Exception {
        assertEquals("13", batched(java.util.Set.of("10", "11", "12"), 0).firstFreeFrom(10));
        assertEquals(1, batches.size(), "one batch answers the whole walk");
    }

    @Test
    void aFullBatchIsFollowedByTheNextOne() throws Exception {
        // The first 250 candidates are all taken, so the answer is in the second batch.
        var taken = new java.util.HashSet<String>();
        for (int i = 1; i <= 250; i++) taken.add(String.valueOf(i));

        assertEquals("251", batched(taken, 0).firstFreeFrom(1));
        assertEquals(2, batches.size());
        assertEquals(250, batches.getFirst().size());
        assertEquals("1", batches.getFirst().getFirst());
        assertEquals("251", batches.get(1).getFirst());
    }

    @Test
    void theBatchedWalkGivesUpAfterTheSameThousandCandidates() throws Exception {
        var taken = new java.util.HashSet<String>();
        for (int i = 1; i <= 5000; i++) taken.add(String.valueOf(i));

        assertNull(batched(taken, 0).firstFreeFrom(1));
        assertEquals(4, batches.size(), "1000 candidates in batches of 250");
        assertEquals(1000, batches.stream().mapToInt(List::size).sum());
    }

    @Test
    void theBatchStopsAtFourteenDigitsRatherThanAskingAboutLongerCodes() throws Exception {
        // Two candidates are left below the limit; the batch must not run past it.
        assertNull(batched(java.util.Set.of("99999999999998", "99999999999999"), 0)
                .firstFreeFrom(99999999999998L));
        assertEquals(List.of(List.of("99999999999998", "99999999999999")), batches);
    }

    @Test
    void theBatchLookupIsToldWhichItemIsBeingEdited() throws Exception {
        batched(java.util.Set.of(), 42).firstFreeFrom(1);
        assertEquals(List.of("batch/42"), asked);
    }

    @Test
    void theEditedItemIdIsReadOnEachCall() throws Exception {
        int[] id = {0};
        var availability = new BarcodeAvailability((code, itemId) -> {
            asked.add(code + "/" + itemId);
            return null;
        }, () -> id[0]);

        availability.takenBy("123");
        id[0] = 42;
        availability.takenBy("123");

        assertEquals(List.of("123/0", "123/42"), asked);
    }
}
