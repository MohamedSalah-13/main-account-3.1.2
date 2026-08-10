package com.hamza.controlsfx.observer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One place to publish and listen to {@link AppEvent}s, keyed by event type.
 * <p>
 * It replaces passing a bag of publishers down through every constructor: a
 * screen asks the registry for the bus and says which event it cares about, and
 * the compiler checks what it receives - where a bag of a dozen
 * {@code Publisher<String>} fields could only be told apart by the name of the
 * field a caller happened to pick.
 * <p>
 * Each event type gets its own {@link Publisher}, so the delivery rules are
 * exactly the ones already in use: listeners run on the JavaFX application
 * thread, one throwing does not rob the others, and a subscription is closed
 * through the handle it returned - see {@link Subscriptions}.
 * <p>
 * One thing the bag did for free is gone, though. It belonged to the main screen
 * and was thrown away at logout, which quietly disposed of any listener nobody
 * had unsubscribed. The bus is registered once for the life of the process, so a
 * listener stays until its subscription is closed.
 */
public final class EventBus {

    private final Map<Class<?>, Publisher<AppEvent>> byType = new ConcurrentHashMap<>();

    /**
     * Listens for events of exactly {@code type} - a subtype is a different event
     * and is not delivered here.
     *
     * @return the handle that stops the listening, to be kept in a {@link Subscriptions}
     */
    public <E extends AppEvent> Subscription subscribe(Class<E> type, Consumer<E> listener) {
        return publisherFor(type).subscribe(event -> listener.accept(type.cast(event)));
    }

    public void publish(AppEvent event) {
        publisherFor(event.getClass()).publish(event);
    }

    private Publisher<AppEvent> publisherFor(Class<?> type) {
        return byType.computeIfAbsent(type, ignored -> new Publisher<>());
    }
}
