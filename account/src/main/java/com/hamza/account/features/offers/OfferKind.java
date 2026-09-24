package com.hamza.account.features.offers;

/**
 * What an offer does to a line it reaches (docs/pricing-and-offers-plan.md §4.2). Phase B builds the three
 * that work on one line at a time; the quantity, the gift, the bundle and the invoice offers are phases C
 * and D, and their kinds arrive with their migrations.
 */
public enum OfferKind {
    /** A percentage off the line's price: "10% off every detergent". */
    PERCENT,
    /** An amount off each unit sold: "5 off a carton". */
    AMOUNT,
    /** A price for the unit, never above the one the line charges: "a carton at 100". */
    PRICE
}
