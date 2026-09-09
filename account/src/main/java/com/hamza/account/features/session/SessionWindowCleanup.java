package com.hamza.account.features.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Selects and closes windows that belong to the session being ended. */
public final class SessionWindowCleanup {

    private SessionWindowCleanup() {
    }

    /**
     * Closes every currently open window except the primary window retained for navigation.
     * A snapshot is required because closing a JavaFX window removes it from the live windows list.
     */
    public static <T> List<RuntimeException> closeSecondaryWindows(
            T primaryWindow,
            Iterable<? extends T> openWindows,
            Consumer<? super T> closeAction
    ) {
        Objects.requireNonNull(primaryWindow, "primaryWindow");
        Objects.requireNonNull(openWindows, "openWindows");
        Objects.requireNonNull(closeAction, "closeAction");

        var snapshot = new ArrayList<T>();
        openWindows.forEach(snapshot::add);
        var failures = new ArrayList<RuntimeException>();
        snapshot.stream()
                .filter(window -> window != primaryWindow)
                .forEach(window -> {
                    try {
                        closeAction.accept(window);
                    } catch (RuntimeException failure) {
                        failures.add(failure);
                    }
                });
        return List.copyOf(failures);
    }
}
