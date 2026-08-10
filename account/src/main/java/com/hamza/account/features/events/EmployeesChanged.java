package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * An employee was added, edited or deleted, so the employees list and the
 * delegate combo boxes on the invoice screens are out of date. Carries nothing.
 */
public record EmployeesChanged() implements AppEvent {
}
