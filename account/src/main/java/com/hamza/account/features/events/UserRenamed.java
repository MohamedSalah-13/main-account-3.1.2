package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * The signed-in user is now called {@code name} - either because they just
 * signed in, or because they renamed themselves.
 * <p>
 * Only the greeting in the toolbar listens. Do not use it to announce that the
 * list of users changed; that is {@link UsersChanged}, and the two were one
 * publisher until they were told apart.
 */
public record UserRenamed(String name) implements AppEvent {
}
