package com.hamza.account.features.offers;

/**
 * What an offer does to the lines it reaches (docs/pricing-and-offers-plan.md §4.2). Phase B built the three
 * that work on one line at a time, phase C the two that count a quantity across the lines of an item; the
 * bundle and the invoice offers are phase D, and their kinds arrive with their migration.
 */
public enum OfferKind {
    /** A percentage off the line's price: "10% off every detergent". */
    PERCENT,
    /** An amount off each unit sold: "5 off a carton". */
    AMOUNT,
    /** A price for the unit, never above the one the line charges: "a carton at 100". */
    PRICE,
    /** A price for a number of units together: "3 for 100". Never above what they charge. */
    QUANTITY_PRICE,
    /** Buy some, get some at a discount - free at a hundred per cent - of the same item or of a gift item. */
    BUY_GET;

    /** Whether the offer counts a quantity across an item's lines, rather than working on each line alone. */
    public boolean pooled() {
        return this == QUANTITY_PRICE || this == BUY_GET;
    }
}
