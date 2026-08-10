package com.hamza.controlsfx.observer;

/**
 * The handle returned by {@link Subject#subscribe(Observer)}: closing it removes
 * the observer again.
 * <p>
 * Screens that are opened more than once - every dialog, and every controller
 * built fresh by its {@code Application} - used to register observers in their
 * constructor and never remove them. The observers stayed registered after the
 * window closed, holding the controller and its whole scene graph alive, and the
 * next notification ran them all: ten openings meant ten refreshes of a table
 * nobody could see. Keep the subscriptions and close them from
 * {@code stage.setOnHidden(...)}.
 * <p>
 * It extends {@link AutoCloseable} without a checked exception so it can also be
 * used in try-with-resources.
 */
@FunctionalInterface
public interface Subscription extends AutoCloseable {

    void unsubscribe();

    @Override
    default void close() {
        unsubscribe();
    }

    /**
     * Bundles several subscriptions into one, so a screen can keep a single field
     * and close everything it registered in one call.
     */
    static Subscription of(Subscription... subscriptions) {
        var copy = subscriptions.clone();
        return () -> {
            for (Subscription subscription : copy) {
                if (subscription != null) subscription.unsubscribe();
            }
        };
    }
}
