package com.hamza.controlsfx.observer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the observers inline via the injected executor, so none of this needs a
 * JavaFX toolkit.
 */
class PublisherTest {

    private static <T> Publisher<T> publisher() {
        return new Publisher<>(Runnable::run);
    }

    @Nested
    @DisplayName("delivery")
    class Delivery {

        @Test
        @DisplayName("hands every observer the message that was published")
        void deliversToAll() {
            var publisher = PublisherTest.<String>publisher();
            List<String> seen = new ArrayList<>();
            publisher.subscribe(seen::add);
            publisher.subscribe(seen::add);

            publisher.publish("saved");

            assertEquals(List.of("saved", "saved"), seen);
        }

        @Test
        @DisplayName("delivers null for a signal that carries nothing")
        void deliversNullForSignal() {
            var publisher = PublisherTest.<String>publisher();
            List<String> seen = new ArrayList<>();
            publisher.subscribe(seen::add);

            publisher.publish();

            assertEquals(1, seen.size());
            assertNull(seen.getFirst());
        }

        @Test
        @DisplayName("keeps nothing, so a later signal does not repeat an earlier message")
        void doesNotResendTheLastMessage() {
            var publisher = PublisherTest.<String>publisher();
            List<String> seen = new ArrayList<>();
            publisher.publish("mohamed");
            publisher.subscribe(seen::add);

            publisher.publish();

            assertNull(seen.getFirst(), "the signal must not carry the name published before it");
        }
    }

    @Nested
    @DisplayName("subscriptions")
    class Subscriptions {

        @Test
        @DisplayName("stop delivery once closed")
        void unsubscribeStopsDelivery() {
            var publisher = PublisherTest.<String>publisher();
            List<String> seen = new ArrayList<>();
            var subscription = publisher.subscribe(seen::add);

            publisher.publish("first");
            subscription.unsubscribe();
            publisher.publish("second");

            assertEquals(List.of("first"), seen);
        }

        @Test
        @DisplayName("are closed one at a time, leaving the others listening")
        void unsubscribeLeavesTheOthers() {
            var publisher = PublisherTest.<String>publisher();
            List<String> kept = new ArrayList<>();
            var dropped = publisher.subscribe(message -> fail("should not be notified"));
            publisher.subscribe(kept::add);

            dropped.unsubscribe();
            publisher.publish("saved");

            assertEquals(List.of("saved"), kept);
        }

        @Test
        @DisplayName("can be closed by an observer while it is being notified")
        void unsubscribeDuringDispatch() {
            var publisher = PublisherTest.<String>publisher();
            List<String> seen = new ArrayList<>();
            var subscription = new Subscription[1];
            subscription[0] = publisher.subscribe(message -> {
                seen.add(message);
                subscription[0].unsubscribe();
            });

            publisher.publish("first");
            publisher.publish("second");

            assertEquals(List.of("first"), seen);
        }
    }

    @Nested
    @DisplayName("a failing observer")
    class FailingObserver {

        @Test
        @DisplayName("does not stop the ones after it")
        void isolatesFailures() {
            var publisher = PublisherTest.<String>publisher();
            List<String> seen = new ArrayList<>();
            publisher.subscribe(message -> {
                throw new IllegalStateException("boom");
            });
            publisher.subscribe(seen::add);

            assertDoesNotThrow(() -> publisher.publish("saved"));
            assertEquals(List.of("saved"), seen);
        }
    }
}
