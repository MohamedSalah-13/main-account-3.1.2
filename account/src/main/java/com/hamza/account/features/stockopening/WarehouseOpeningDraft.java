package com.hamza.account.features.stockopening;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The openings typed on the screen and not saved yet, across every page of it.
 * <p>
 * Held apart from the table for the reason {@code UnitPriceDraft} is: a table's rows are rebuilt
 * each time a page is read, and a figure typed on page one must still be there, and still be
 * saved, after page three has been looked at. Each change keeps the value the screen <em>read</em>
 * beside the value typed, so the save can refuse a figure somebody else changed in the meantime
 * rather than write over it.
 */
public final class WarehouseOpeningDraft {

    /** {@code DECIMAL(14,3)}: typing the stored figure back, to the last digit it holds, is no change. */
    static final double TOLERANCE = 0.0005;

    /** One item's opening, as read and as typed. */
    public record Change(int itemId, String name, double read, double typed) {
    }

    private final Map<Integer, Change> changes = new LinkedHashMap<>();

    /**
     * Whether the row's opening may be typed into: not once anything has moved the item in this
     * warehouse. The save asks the same question again, inside its transaction.
     */
    public static boolean editable(WarehouseOpeningRow row) {
        return !row.moved();
    }

    /** Records what was typed for a row; typing the stored figure back undoes the change. */
    public void set(WarehouseOpeningRow row, double typed) {
        if (!editable(row)) {
            throw new IllegalArgumentException("the opening of a moved item is not typed into: " + row.itemId());
        }
        Change earlier = changes.get(row.itemId());
        // What was read the first time is kept: the page may have been read again since, and the
        // save has to compare against what the operator started from.
        double read = earlier == null ? row.opening() : earlier.read();
        if (Math.abs(typed - read) < TOLERANCE) {
            changes.remove(row.itemId());
        } else {
            changes.put(row.itemId(), new Change(row.itemId(), row.name(), read, typed));
        }
    }

    /** What the row shows: the figure typed, or the one stored. */
    public double shown(WarehouseOpeningRow row) {
        Change change = changes.get(row.itemId());
        return change == null ? row.opening() : change.typed();
    }

    public boolean isChanged(int itemId) {
        return changes.containsKey(itemId);
    }

    public List<Change> changes() {
        return new ArrayList<>(changes.values());
    }

    public int size() {
        return changes.size();
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public void clear() {
        changes.clear();
    }
}
