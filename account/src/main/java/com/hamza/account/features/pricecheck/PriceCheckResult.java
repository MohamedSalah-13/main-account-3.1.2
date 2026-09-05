package com.hamza.account.features.pricecheck;

import java.time.LocalDate;

/**
 * The answer to one scan. Read-only by construction: nothing here can be saved, and
 * the screen showing it has no path back into the database.
 */
public sealed interface PriceCheckResult {

    /**
     * @param quantity     1 for an ordinary barcode, and the weight the scale printed
     *                     for a scale barcode
     * @param total        {@code price * quantity} - the number the customer pays, which
     *                     is the price itself for everything that is not weighed
     * @param balance      what is on hand in {@code unitName}, already converted from
     *                     base units; shown only when the settings allow it
     * @param nearestExpiry the earliest expiry date still holding stock, or null when the
     *                     item does not track batches, none is left, or the setting is off
     * @param image        the item's picture bytes, or null - loaded with the item, so it
     *                     costs no query of its own
     */
    record Found(int itemId, String itemName, String unitName, double price, double quantity,
                 double total, double balance, boolean scaleBarcode, LocalDate nearestExpiry,
                 byte[] image) implements PriceCheckResult {
    }

    /**
     * No price can be shown for this code.
     * <p>
     * Covers the ordinary case - the code belongs to no item in this warehouse - and the
     * rare one where the item exists but carries no usable unit, since an item that
     * cannot be priced gives the customer the same answer either way: ask the staff.
     */
    record NotFound(String code) implements PriceCheckResult {
    }
}
