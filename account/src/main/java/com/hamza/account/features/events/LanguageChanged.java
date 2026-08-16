package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

import java.util.Locale;

/**
 * The active language was switched. Screens built from {@code %key} FXML bindings
 * or from {@link com.hamza.controlsfx.language.LanguageManager#getString} directly
 * need to rebuild themselves to show the new language - there is no automatic
 * re-bind for either mechanism once a control has been built.
 */
public record LanguageChanged(Locale locale) implements AppEvent {
}
