package com.hamza.account.features.itemmerge;

/**
 * What a merge does with the rows a reference holds.
 * <p>
 * Every table pointing at {@code items.id} needs one of these, and which one it needs
 * is decided by its unique keys, not by what it stores: a plain {@code UPDATE} is only
 * safe where nothing stops the target owning the row as well.
 */
public enum MergeAction {

    /** Nothing else can clash, so the column is simply repointed. */
    MOVE,

    /**
     * A unique key pairs the item with something else, so the target may already have a
     * row for the same pair. The two are combined into one and the source's is dropped.
     */
    MERGE_ROW,

    /**
     * The row moves only where the target has nothing equivalent. What stays behind is
     * removed by the cascading foreign key when the source item is deleted - which is
     * why anything worth keeping off such a row (a barcode) is rescued first.
     */
    MOVE_IF_ABSENT,

    /**
     * The column is repointed and the duplicates that produces are cleaned up
     * afterwards, there being no unique key to prevent them.
     */
    MOVE_DEDUPE
}
