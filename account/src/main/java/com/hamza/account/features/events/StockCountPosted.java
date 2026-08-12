package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A stock count was posted, so every balance in the shop may have moved.
 * <p>
 * Separate from {@link InvoiceSaved} because nothing was bought or sold: this is a
 * correction, and a listener that only cares about trade should be able to say so.
 * The inventory sheet listens to both, since either changes what it reports.
 *
 * @param countId    which sheet was posted
 * @param linesMoved how many of its lines actually differed - a full count where
 *                   everything matched posts nothing and is worth saying so
 */
public record StockCountPosted(int countId, int linesMoved) implements AppEvent {
}
