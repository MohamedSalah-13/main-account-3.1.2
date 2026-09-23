package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A currency or one of its rates was added, changed or removed. Carries nothing, so another till can
 * rebuild it exactly and it travels under the {@code currencies} topic: a rate recorded at the back
 * office has to reach the screen at the counter, or two machines convert one amount two ways.
 */
public record CurrenciesChanged() implements AppEvent {
}
