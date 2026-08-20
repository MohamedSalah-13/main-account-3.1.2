package com.hamza.account.features.itemmerge;

import java.util.List;

/**
 * What one merge actually did - the row written to {@code item_merge}, handed back to
 * the caller so the screen can say it without reading the log again.
 *
 * @param mergeId  the log row's id
 * @param preview  the counts taken inside the transaction, immediately before the moves
 */
public record ItemMergeResult(int mergeId, ItemMergePreview preview) {

    public int sourceId() {
        return preview.source().id();
    }

    public String sourceName() {
        return preview.source().name();
    }

    public int targetId() {
        return preview.target().id();
    }

    /** The totals of a batch, for the one sentence a screen shows at the end of it. */
    public static int totalRows(List<ItemMergeResult> results) {
        return results.stream().mapToInt(result -> result.preview().totalRows()).sum();
    }
}
