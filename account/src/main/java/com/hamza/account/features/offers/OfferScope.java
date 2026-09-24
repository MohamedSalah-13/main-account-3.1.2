package com.hamza.account.features.offers;

/** What an offer's target names (docs/pricing-and-offers-plan.md ق-ع٦). */
public enum OfferScope {
    /** One item - in one of its units, when the target names a unit. */
    ITEM,
    /** Every item of a sub group. */
    SUB_GROUP,
    /** Every item of a main group. */
    MAIN_GROUP,
    /** Every item. */
    ALL
}
