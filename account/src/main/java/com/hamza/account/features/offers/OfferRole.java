package com.hamza.account.features.offers;

/**
 * What a target is to its offer ({@code offer_target.role}, V86): what the customer buys to earn the offer,
 * or - on a "buy and get" of another item - the gift it earns (docs/pricing-and-offers-plan.md ق-ع٣).
 */
public enum OfferRole {
    /** A line this target names earns the offer - or, excluded, does not. */
    QUALIFY,
    /** The item a "buy and get" gives: one item, in one unit or its base, never excluded. */
    REWARD
}
