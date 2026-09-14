package com.hamza.account.features.unitprices;

import java.util.List;

/** Every row one press of the save button changes, and who is saving them. */
public record UnitPriceSaveCommand(List<PriceChange> changes, int userId) {

    public UnitPriceSaveCommand {
        changes = changes == null ? List.of()
                : changes.stream().filter(change -> change != null && !change.isEmpty()).toList();
    }

    public boolean touchesItems() {
        return changes.stream().anyMatch(PriceChange::isItem);
    }

    public boolean touchesUnits() {
        return changes.stream().anyMatch(change -> !change.isItem());
    }

    public boolean touchesCost() {
        return changes.stream().anyMatch(change -> change.changedFields().contains(PriceField.BUY));
    }
}
