package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * The active UI font family was switched. {@link com.hamza.account.config.ThemeManager#apply}
 * already restamps the new family onto whichever scene it is called on, so this is only
 * needed by screens that build text eagerly and would otherwise not notice until reopened.
 */
public record FontChanged(String family) implements AppEvent {
}
