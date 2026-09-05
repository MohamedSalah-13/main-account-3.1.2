package com.hamza.account.features.itemgroups;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure validation kept outside JavaFX and JDBC so the drag/drop rules are unit-testable. */
public final class ItemGroupMovePolicy {

    private ItemGroupMovePolicy() {
    }

    public static String rejectionKey(ItemGroupMoveCommand command) {
        if (command == null || command.changes().isEmpty()) {
            return "item.group.manager.error.select.items";
        }
        if (command.userId() <= 0) {
            return "item.group.manager.error.user";
        }
        Set<Integer> ids = new HashSet<>();
        for (ItemGroupChange change : command.changes()) {
            if (change == null || change.itemId() <= 0
                    || change.sourceSubGroupId() <= 0 || change.targetSubGroupId() <= 0) {
                return "item.group.manager.error.invalid.move";
            }
            if (!ids.add(change.itemId())) {
                return "item.group.manager.error.duplicate.item";
            }
        }
        return null;
    }

    public static List<ItemGroupChange> effectiveChanges(ItemGroupMoveCommand command) {
        return command.changes().stream()
                .filter(change -> change.sourceSubGroupId() != change.targetSubGroupId())
                .toList();
    }
}
