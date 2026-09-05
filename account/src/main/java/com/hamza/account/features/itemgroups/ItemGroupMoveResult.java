package com.hamza.account.features.itemgroups;

import java.util.List;

public record ItemGroupMoveResult(List<ItemGroupChange> changes) {

    public ItemGroupMoveResult {
        changes = List.copyOf(changes);
    }

    public int movedCount() {
        return changes.size();
    }

    public ItemGroupMoveCommand undoCommand(int userId) {
        return new ItemGroupMoveCommand(changes.stream().map(ItemGroupChange::reverse).toList(), userId);
    }
}
