package com.hamza.controlsfx.observer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private final EventBus bus = new EventBus(Runnable::run);

    record ItemAdded(String name) implements AppEvent {
    }

    record UsersChanged() implements AppEvent {
    }

    @Test
    @DisplayName("delivers an event to the listeners of its own type, typed")
    void deliversByType() {
        List<String> seen = new ArrayList<>();
        bus.subscribe(ItemAdded.class, event -> seen.add(event.name()));

        bus.publish(new ItemAdded("sugar"));

        assertEquals(List.of("sugar"), seen);
    }

    @Test
    @DisplayName("leaves the listeners of other event types alone")
    void doesNotCrossTypes() {
        bus.subscribe(UsersChanged.class, event -> fail("a different event was delivered"));
        List<ItemAdded> seen = new ArrayList<>();
        bus.subscribe(ItemAdded.class, seen::add);

        bus.publish(new ItemAdded("sugar"));

        assertEquals(1, seen.size());
    }

    @Test
    @DisplayName("publishes to nobody without complaint")
    void publishingWithoutListeners() {
        assertDoesNotThrow(() -> bus.publish(new UsersChanged()));
    }

    @Test
    @DisplayName("stops delivering once the subscription is closed")
    void unsubscribe() {
        List<UsersChanged> seen = new ArrayList<>();
        var subscription = bus.subscribe(UsersChanged.class, seen::add);

        bus.publish(new UsersChanged());
        subscription.unsubscribe();
        bus.publish(new UsersChanged());

        assertEquals(1, seen.size());
    }

    @Test
    @DisplayName("gives every listener of the same event its turn")
    void severalListeners() {
        List<String> seen = new ArrayList<>();
        bus.subscribe(ItemAdded.class, event -> seen.add("first"));
        bus.subscribe(ItemAdded.class, event -> seen.add("second"));

        bus.publish(new ItemAdded("sugar"));

        assertEquals(List.of("first", "second"), seen);
    }
}
