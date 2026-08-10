package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A payment or receipt was recorded, edited or deleted against a customer or
 * supplier account, so the balances shown for that side have moved.
 */
public record AccountChanged(PartyKind kind) implements AppEvent {
}
