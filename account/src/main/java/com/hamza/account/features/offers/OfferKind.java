package com.hamza.account.features.offers;

/**
 * What an offer does to the lines it reaches (docs/pricing-and-offers-plan.md §4.2). Phase B built the three
 * that work on one line at a time, phase C the two that count a quantity across the lines of an item, and
 * phase D (V87) the bundle and the offer on the invoice's total.
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
    BUY_GET,
    /**
     * Components, each in its quantity, for one price: "the Ramadan bundle at 150" (ق-ع١٢). Never above what
     * they charge. A bundle may carry a barcode, which puts its components on the invoice when scanned.
     */
    BUNDLE,
    /**
     * A percentage or an amount off an invoice whose lines come to a threshold: "5% from 1,000". Given on the
     * lines no other offer took, by their value - never in the invoice's own discount box.
     */
    INVOICE;

    /** The three that work on each line alone. */
    public boolean single() {
        return this == PERCENT || this == AMOUNT || this == PRICE;
    }

    /** The two that count a quantity of an item: a group bought, and its price or what it earns. */
    public boolean countsAQuantity() {
        return this == QUANTITY_PRICE || this == BUY_GET;
    }

    /**
     * Whether the offer takes whole groups of units across lines and shares its discount among them: the two
     * that count a quantity and the bundle. The invoice offer works across lines too, but last and on what the
     * others left (ق-ع٥).
     */
    public boolean pooled() {
        return countsAQuantity() || this == BUNDLE;
    }
}
