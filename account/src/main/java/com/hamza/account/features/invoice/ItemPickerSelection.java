package com.hamza.account.features.invoice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent selection state for the item picker.
 * <p>
 * The catalog table replaces its rows whenever a search or page changes. Keeping the
 * chosen ids here, rather than in {@code TableView.getSelectionModel()}, makes a choice
 * survive both operations and gives mouse and keyboard entry the same rule.
 */
public final class ItemPickerSelection {

    private final Map<Integer, ItemPickRequest> selected = new LinkedHashMap<>();

    public boolean select(int itemId, String itemName) {
        return selected.putIfAbsent(itemId, new ItemPickRequest(itemId, itemName, 1)) == null;
    }

    public boolean deselect(int itemId) {
        return selected.remove(itemId) != null;
    }

    /** @return {@code true} when the item is selected after the toggle. */
    public boolean toggle(int itemId, String itemName) {
        if (contains(itemId)) {
            deselect(itemId);
            return false;
        }
        select(itemId, itemName);
        return true;
    }

    public boolean contains(int itemId) {
        return selected.containsKey(itemId);
    }

    public int size() {
        return selected.size();
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public void clear() {
        selected.clear();
    }

    /** Stable insertion order and no mutable view of the internal map. */
    public List<ItemPickRequest> requests() {
        return List.copyOf(selected.values());
    }
}
