package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * The list of treasuries changed. Carries nothing.
 * <p>
 * Nothing publishes it yet, exactly as nothing published the publisher it
 * replaced: the settings screen deletes a treasury and drops the row from its
 * own list, and its {@code addData()} is still a TODO. The listener is in place
 * for when adding one is implemented.
 */
public record TreasuriesChanged() implements AppEvent {
}
