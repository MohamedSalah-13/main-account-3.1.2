package com.hamza.account.features.itemgroups;

/** One optimistic group reassignment. The source guards against overwriting a concurrent move. */
public record ItemGroupChange(int itemId, int sourceSubGroupId, int targetSubGroupId) {

    public ItemGroupChange reverse() {
        return new ItemGroupChange(itemId, targetSubGroupId, sourceSubGroupId);
    }
}
