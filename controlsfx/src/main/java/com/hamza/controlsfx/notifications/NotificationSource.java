package com.hamza.controlsfx.notifications;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * A condition worth watching, checked on a schedule.
 * <p>
 * This is the extension point: to notify about something new, write one of these
 * and register it with {@link NotificationScheduler}. Nothing else changes - not
 * the centre, not the bell, not the settings screen, which discovers categories
 * from the sources themselves.
 * <p>
 * {@link #poll()} runs on a background thread, so it may hit the database
 * directly, and it must not touch JavaFX controls. Returning an empty list means
 * "nothing to report"; throwing is fine and is logged by the scheduler.
 * <p>
 * For something that is not a polled condition but a one-off event - an invoice
 * saved, a backup that just failed - publish straight to
 * {@link NotificationCenter} instead; a source is only needed when someone has to
 * go and look.
 */
public interface NotificationSource {

    /** Stable identifier, used for logging and for running one source on demand. */
    @NotNull
    String id();

    /** The category the notifications belong to, so the settings screen can list it. */
    @NotNull
    String category();

    /** What the user sees this rule called in the settings screen. */
    @NotNull
    String displayName();

    /** How often to check. Nothing shorter than a minute is scheduled. */
    @NotNull
    Duration interval();

    /**
     * How long to wait before the first check. The default keeps startup clear: the
     * login screen and the main window should be up before the rules start querying.
     */
    @NotNull
    default Duration initialDelay() {
        return Duration.ofSeconds(30);
    }

    /**
     * Whether this rule runs at all. Backed by whatever the source wants - a
     * preference, a permission, a licence check.
     */
    default boolean enabled() {
        return true;
    }

    /**
     * @return the notifications this condition currently warrants, empty if none
     * @throws Exception freely; the scheduler logs it and keeps the rule scheduled
     */
    @NotNull
    List<AppNotification> poll() throws Exception;
}
