package com.hamza.account.features.items;

import com.hamza.account.opening.OpeningBalanceGuard.Verdict;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bulk editor's opening balance, decided for the whole batch before anything is written.
 * The verdicts are handed in, so no database is needed: what each one means is
 * {@code OpeningBalanceGuardTest}'s.
 */
class BulkOpeningBalanceTest {

    private static final String CORRECTION = "use the stock count";

    private static BulkOpeningBalance.Verdicts verdicts(Map<Integer, Verdict> byId) {
        return (id, incoming) -> byId.getOrDefault(id, Verdict.OPEN);
    }

    private static BulkOpeningBalance.Item item(int id) {
        return new BulkOpeningBalance.Item(id, "item " + id, 2);
    }

    @Test
    @DisplayName("items nothing has moved all take the balance")
    void unmovedItemsAreWritten() throws Exception {
        Set<Integer> writable = BulkOpeningBalance.writableIds(List.of(item(1), item(2)),
                verdicts(Map.of()), CORRECTION);

        assertEquals(Set.of(1, 2), writable);
    }

    @Test
    @DisplayName("a moved item that already holds the value is neither written nor refused")
    void anUnchangedMovedItemIsLeftAlone() throws Exception {
        Set<Integer> writable = BulkOpeningBalance.writableIds(List.of(item(1), item(2)),
                verdicts(Map.of(2, Verdict.UNCHANGED)), CORRECTION);

        assertEquals(Set.of(1), writable);
    }

    @Test
    @DisplayName("one moved item that would change refuses the whole batch, and says which and what to do")
    void oneRefusalRefusesTheBatch() {
        BusinessRuleException refusal = assertThrows(BusinessRuleException.class,
                () -> BulkOpeningBalance.writableIds(List.of(item(1), item(2), item(3)),
                        verdicts(Map.of(2, Verdict.REFUSED)), CORRECTION));

        String message = refusal.getMessage();
        assertTrue(message.contains("item 2"), message);
        assertFalse(message.contains("item 1"), message);
        assertTrue(message.contains(CORRECTION), message);
    }

    @Test
    @DisplayName("every item is asked before refusing, so the count is the batch's and not the first one found")
    void theCountCoversTheWholeBatch() {
        List<BulkOpeningBalance.Item> items = new ArrayList<>();
        for (int id = 1; id <= 9; id++) {
            items.add(item(id));
        }
        Map<Integer, Verdict> refused = new java.util.HashMap<>();
        for (int id = 1; id <= 7; id++) {
            refused.put(id, Verdict.REFUSED);
        }

        String message = assertThrows(BusinessRuleException.class,
                () -> BulkOpeningBalance.writableIds(items, verdicts(refused), CORRECTION)).getMessage();

        assertTrue(message.contains("7"), message);
        assertTrue(message.contains("item " + BulkOpeningBalance.NAMES_SHOWN), message);
        assertFalse(message.contains("item " + (BulkOpeningBalance.NAMES_SHOWN + 1)),
                "only the first names are spelled out: " + message);
        assertTrue(message.contains("…"), message);
    }
}
