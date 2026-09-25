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
     * @param offer        what an offer in force says about one of this unit at the screen's tier, or null
     *                     (docs/pricing-and-offers-plan.md phase E) - a price the till will charge, or an
     *                     offer told in words
     */
    record Found(int itemId, String itemName, String unitName, double price, double quantity,
                 double total, double balance, boolean scaleBarcode, LocalDate nearestExpiry,
                 byte[] image, com.hamza.account.features.offers.OfferPriceTag offer) implements PriceCheckResult {

        /** An answer no offer reaches. */
        public Found(int itemId, String itemName, String unitName, double price, double quantity, double total,
                     double balance, boolean scaleBarcode, LocalDate nearestExpiry, byte[] image) {
            this(itemId, itemName, unitName, price, quantity, total, balance, scaleBarcode, nearestExpiry, image,
                    null);
        }

        /** What a weighed packet comes to under the offer's price for the unit, or null without one. */
        public Double offerTotal() {
            return offer == null || !offer.hasPrice() ? null
                    : offer.offerPrice().multiply(java.math.BigDecimal.valueOf(quantity))
                    .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
        }
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
