package com.hamza.account.features.itemmerge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a merge would move, counted before anything is written.
 * <p>
 * The screen shows this and asks; the service also takes one immediately before it
 * writes, and that second one is what the log records. Both are the same shape because
 * they are the same question - a merge that moved a different number of rows than the
 * user was shown is worth being able to see afterwards.
 *
 * @param source            the item that will be deleted
 * @param target            the item that inherits its history
 * @param rows              rows per {@link ItemReference#qualified()}, in registry order
 * @param lockedPeriodLines document lines dated inside a closed period; reported, not refused
 */
public record ItemMergePreview(MergeItem source, MergeItem target,
                               Map<String, Integer> rows, int lockedPeriodLines) {

    public ItemMergePreview {
        // Not Map.copyOf: that one does not keep the order, and the order here is the
        // registry's - which is the order the screen lists the tables in and the order
        // the log rows are written.
        rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows == null ? Map.of() : rows));
    }

    public int rowsFor(ItemReference reference) {
        return rows.getOrDefault(reference.qualified(), 0);
    }

    /** Everything that would move, over every reference. */
    public int totalRows() {
        return rows.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** The four documents alone - the number a user recognises as "the operations on this item". */
    public int documentLines() {
        return ItemReferenceRegistry.DOCUMENTS.stream().mapToInt(this::rowsFor).sum();
    }

    /** Whether the source has any history at all. A merge is still allowed if it has none. */
    public boolean isEmpty() {
        return totalRows() == 0;
    }
}
