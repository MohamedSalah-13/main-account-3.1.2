package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A price tier was renamed, switched on or off, or given another fill rule (V84). Carries nothing, so
 * another till can rebuild it exactly and it travels under the {@code price.tiers} topic: every price a
 * screen shows is labelled with its tier's name, and a tier switched off at the back office has to
 * leave the counter's combo too.
 * <p>
 * It replaced {@code SelPriceNamesChanged}, which carried the names - so it could not be relayed - and
 * was never published: nothing could rename a tier on screen.
 */
public record PriceTiersChanged() implements AppEvent {
}
