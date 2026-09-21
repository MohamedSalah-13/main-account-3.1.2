package com.hamza.account.features.stockopening;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the opening-balances screen holds between typing and saving. */
class WarehouseOpeningDraftTest {

    private static WarehouseOpeningRow open(int itemId, double opening) {
        return new WarehouseOpeningRow(itemId, "C" + itemId, "item " + itemId, "piece", opening, false);
    }

    @Test
    @DisplayName("a typed figure is shown on its row and saved with what was read beside it")
    void aTypedFigureIsKept() {
        WarehouseOpeningDraft draft = new WarehouseOpeningDraft();
        WarehouseOpeningRow row = open(5, 0);

        draft.set(row, 12);

        assertEquals(12, draft.shown(row));
        assertTrue(draft.isChanged(5));
        assertEquals(new WarehouseOpeningDraft.Change(5, "item 5", 0, 12), draft.changes().getFirst());
    }

    @Test
    @DisplayName("typing the stored figure back is no change")
    void typingItBackUndoesIt() {
        WarehouseOpeningDraft draft = new WarehouseOpeningDraft();
        WarehouseOpeningRow row = open(5, 3);

        draft.set(row, 7);
        draft.set(row, 3.0001);

        assertTrue(draft.isEmpty());
        assertEquals(3, draft.shown(row));
    }

    /**
     * The page is read again whenever it is turned; the save has to compare against the figure the
     * operator started from, or a change made by somebody else in between would be written over.
     */
    @Test
    @DisplayName("what was read the first time is kept when the row comes back with another figure")
    void theFirstReadIsKept() {
        WarehouseOpeningDraft draft = new WarehouseOpeningDraft();
        draft.set(open(5, 0), 12);

        draft.set(open(5, 4), 15);

        assertEquals(0, draft.changes().getFirst().read());
        assertEquals(15, draft.changes().getFirst().typed());
    }

    @Test
    @DisplayName("a row whose item has moved in the warehouse takes no figure")
    void aMovedRowIsNotEditable() {
        WarehouseOpeningRow moved = new WarehouseOpeningRow(5, "C5", "item 5", "piece", 3, true);

        assertFalse(WarehouseOpeningDraft.editable(moved));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseOpeningDraft().set(moved, 9));
    }

    @Test
    @DisplayName("changes survive every page and are cleared only when asked")
    void changesAcrossPages() {
        WarehouseOpeningDraft draft = new WarehouseOpeningDraft();
        draft.set(open(5, 0), 1);
        draft.set(open(9, 0), 2);

        assertEquals(2, draft.size());
        draft.clear();
        assertTrue(draft.isEmpty());
    }
}
