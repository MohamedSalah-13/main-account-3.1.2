package com.hamza.account.features.itemgroups;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemGroupMovePolicyTest {

    @Test
    void rejectsAnEmptySelection() {
        assertEquals("item.group.manager.error.select.items",
                ItemGroupMovePolicy.rejectionKey(new ItemGroupMoveCommand(List.of(), 1)));
    }

    @Test
    void rejectsTheSameItemTwice() {
        var command = new ItemGroupMoveCommand(List.of(
                new ItemGroupChange(7, 2, 3),
                new ItemGroupChange(7, 4, 3)), 1);

        assertEquals("item.group.manager.error.duplicate.item",
                ItemGroupMovePolicy.rejectionKey(command));
    }

    @Test
    void removesNoOpMovesAndReversesTheRest() {
        var command = new ItemGroupMoveCommand(List.of(
                new ItemGroupChange(7, 2, 2),
                new ItemGroupChange(8, 2, 3)), 4);

        assertNull(ItemGroupMovePolicy.rejectionKey(command));
        assertEquals(List.of(new ItemGroupChange(8, 2, 3)),
                ItemGroupMovePolicy.effectiveChanges(command));
        assertEquals(new ItemGroupChange(8, 3, 2),
                new ItemGroupMoveResult(ItemGroupMovePolicy.effectiveChanges(command))
                        .undoCommand(4).changes().getFirst());
    }
}
