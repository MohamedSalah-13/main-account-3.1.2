package com.hamza.account.features.events;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.observer.AppEvent;

/**
 * One item was added or edited, and {@code item} is how it now stands - the POS
 * reads it to drop the button of an item that has just been deactivated.
 * <p>
 * A bulk change carries no single item and is {@link ItemsChanged} instead.
 * Splitting the two is what lets this one promise a non-null item: the same
 * publisher used to serve both, so every listener had to guard against a null it
 * could not otherwise explain.
 */
public record ItemSaved(ItemsModel item) implements AppEvent {
}
