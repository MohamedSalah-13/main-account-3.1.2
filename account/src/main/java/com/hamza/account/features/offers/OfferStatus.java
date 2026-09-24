package com.hamza.account.features.offers;

/**
 * Where an offer stands. A draft reaches no invoice and may be deleted; an active one reaches every sale in
 * its dates; a stopped one reaches none - and once a saved line names an offer it is history: stopped, never
 * deleted, and its terms never edited (docs/pricing-and-offers-plan.md ق-ع٧).
 */
public enum OfferStatus {
    DRAFT,
    ACTIVE,
    STOPPED
}
