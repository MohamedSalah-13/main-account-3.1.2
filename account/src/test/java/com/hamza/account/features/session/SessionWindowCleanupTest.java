package com.hamza.account.features.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SessionWindowCleanupTest {

    @Test
    void logoutClosesEverySecondaryWindowAndRetainsThePrimaryWindow() {
        Object primary = new Object();
        Object sales = new Object();
        Object inventory = new Object();
        List<Object> openWindows = new ArrayList<>(List.of(primary, sales, inventory));

        SessionWindowCleanup.closeSecondaryWindows(primary, openWindows, openWindows::remove);

        assertEquals(List.of(primary), openWindows);
    }

    @Test
    void windowsEqualByValueAreStillClosedWhenTheyAreNotThePrimaryInstance() {
        String primary = new String("window");
        String secondary = new String("window");
        List<String> closed = new ArrayList<>();

        SessionWindowCleanup.closeSecondaryWindows(primary, List.of(primary, secondary), closed::add);

        assertEquals(List.of(secondary), closed);
    }

    @Test
    void oneBrokenWindowCannotPreventTheRestOfTheSessionFromClosing() {
        Object primary = new Object();
        Object broken = new Object();
        Object sales = new Object();
        List<Object> closed = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("broken window");

        List<RuntimeException> failures = SessionWindowCleanup.closeSecondaryWindows(
                primary,
                List.of(primary, broken, sales),
                window -> {
                    if (window == broken) throw failure;
                    closed.add(window);
                });

        assertEquals(List.of(sales), closed);
        assertEquals(1, failures.size());
        assertSame(failure, failures.getFirst());
    }
}
