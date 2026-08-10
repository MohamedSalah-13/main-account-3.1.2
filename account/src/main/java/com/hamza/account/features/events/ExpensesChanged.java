package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * An expense was recorded, edited or deleted. Carries nothing.
 */
public record ExpensesChanged() implements AppEvent {
}
