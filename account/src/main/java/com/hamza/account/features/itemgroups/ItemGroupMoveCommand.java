package com.hamza.account.features.itemgroups;

import java.util.List;

/** All changes in one user operation. They commit together or are all rolled back. */
public record ItemGroupMoveCommand(List<ItemGroupChange> changes, int userId) {

    public ItemGroupMoveCommand {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public ItemGroupMoveCommand reverse() {
        return new ItemGroupMoveCommand(changes.stream().map(ItemGroupChange::reverse).toList(), userId);
    }
}
