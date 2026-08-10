package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A customer or supplier was added, edited or deleted, so the lists and combo
 * boxes naming them are out of date.
 */
public record NameChanged(PartyKind kind) implements AppEvent {
}
