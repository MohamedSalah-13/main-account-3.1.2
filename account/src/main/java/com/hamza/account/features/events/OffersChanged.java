package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * An offer was created, edited, switched on, stopped or deleted (V85, docs/pricing-and-offers-plan.md ق-ع١٣).
 * Carries nothing, so another till rebuilds its snapshot of the offers in force from the database and it
 * travels under the {@code offers} topic. A till that has not heard yet only shows a stale preview: the
 * save reads the offers afresh and refuses a sale that disagrees with them.
 */
public record OffersChanged() implements AppEvent {
}
