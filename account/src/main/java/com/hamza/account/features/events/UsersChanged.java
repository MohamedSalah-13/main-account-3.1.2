package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A user was added, deleted, renamed, or activated: whatever shows the list of
 * users should reload it. Carries nothing - the listener re-reads.
 */
public record UsersChanged() implements AppEvent {
}
