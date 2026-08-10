package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * An invoice on {@code side} was saved or deleted, so the totals, account and
 * statement screens showing that side are out of date.
 * <p>
 * Listeners receive both sides and check {@link #side()} - it replaces
 * {@code DataInterface.publisherPurchaseOrSales()}, which routed to one of two
 * publishers to say the same thing.
 */
public record InvoiceSaved(InvoiceSide side) implements AppEvent {
}
