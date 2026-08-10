package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * Items changed in bulk - an import from Excel, or a reload of everything - so
 * whatever lists them should read them again. Carries nothing; for a single item
 * see {@link ItemSaved}.
 */
public record ItemsChanged() implements AppEvent {
}
