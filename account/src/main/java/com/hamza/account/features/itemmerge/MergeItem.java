package com.hamza.account.features.itemmerge;

import java.math.BigDecimal;

/**
 * The little an item has to say about itself for a merge to be judged.
 * <p>
 * Not {@code ItemsModel}: that one carries an image blob, a JavaFX property or two and
 * a loaded list of units, and reading two of them to compare a unit id would be paying
 * for all of it. These six columns are the whole question.
 *
 * @param id            the row
 * @param name          copied into the log, because the source's row is gone by then
 * @param barcode       likewise - and it is what the target inherits
 * @param unitId        the base unit; the merge is refused when the two differ
 * @param hasValidity   whether the item tracks expiry batches
 * @param firstBalance  the opening balance, which is added to the target's
 */
public record MergeItem(int id, String name, String barcode, int unitId,
                        boolean hasValidity, BigDecimal firstBalance) {

    public MergeItem {
        firstBalance = firstBalance == null ? BigDecimal.ZERO : firstBalance;
    }
}
