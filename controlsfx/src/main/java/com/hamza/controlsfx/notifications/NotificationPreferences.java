package com.hamza.controlsfx.notifications;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.prefs.Preferences;

/**
 * Where the notification settings live between runs.
 * <p>
 * Java {@code Preferences}, the same place the backup folder, the interval and
 * the theme are kept - there is no per-install settings file in this project to
 * put them in, and they are per-machine choices rather than company data.
 * <p>
 * {@link #applyTo(NotificationPolicy)} is the only thing that reads them into the
 * running policy, so a settings screen writes here and calls that, and nothing
 * else has to know a preference was involved.
 */
@Log4j2
public final class NotificationPreferences {

    private static final Preferences PREFS = Preferences.userNodeForPackage(NotificationPreferences.class);

    private static final String KEY_ENABLED = "notifications.enabled";
    private static final String KEY_SOUND = "notifications.sound";
    private static final String KEY_TOAST_THRESHOLD = "notifications.toast.threshold";
    private static final String KEY_COOLDOWN_MINUTES = "notifications.cooldown.minutes";
    private static final String KEY_MUTED_PREFIX = "notifications.muted.";
    private static final String KEY_DEFAULT_CHANNEL = "notifications.channel.default";
    private static final String KEY_CHANNEL_PREFIX = "notifications.channel.";
    private static final String KEY_INTERVAL_PREFIX = "notifications.interval.minutes.";
    private static final String KEY_EVENT_PREFIX = "notifications.event.";

    /** Written when a scope should follow the level above it rather than pin a channel. */
    private static final String INHERIT = "";

    private NotificationPreferences() {
    }

    public static boolean isEnabled() {
        return PREFS.getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_ENABLED, enabled);
    }

    public static boolean isSoundEnabled() {
        return PREFS.getBoolean(KEY_SOUND, true);
    }

    public static void setSoundEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_SOUND, enabled);
    }

    public static NotificationSeverity getToastThreshold() {
        String stored = PREFS.get(KEY_TOAST_THRESHOLD, NotificationSeverity.WARNING.name());
        try {
            return NotificationSeverity.valueOf(stored);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown toast threshold '{}' in preferences, falling back to WARNING", stored);
            return NotificationSeverity.WARNING;
        }
    }

    public static void setToastThreshold(@NotNull NotificationSeverity severity) {
        PREFS.put(KEY_TOAST_THRESHOLD, severity.name());
    }

    public static Duration getCooldown() {
        int minutes = PREFS.getInt(KEY_COOLDOWN_MINUTES, (int) NotificationPolicy.DEFAULT_COOLDOWN.toMinutes());
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    public static void setCooldown(@NotNull Duration cooldown) {
        PREFS.putInt(KEY_COOLDOWN_MINUTES, (int) Math.max(1, cooldown.toMinutes()));
    }

    public static boolean isMuted(@NotNull String category) {
        return PREFS.getBoolean(KEY_MUTED_PREFIX + category, false);
    }

    public static void setMuted(@NotNull String category, boolean muted) {
        PREFS.putBoolean(KEY_MUTED_PREFIX + category, muted);
    }

    @NotNull
    public static NotificationChannel getDefaultChannel() {
        return readChannel(KEY_DEFAULT_CHANNEL, NotificationChannel.IN_APP);
    }

    public static void setDefaultChannel(@NotNull NotificationChannel channel) {
        PREFS.put(KEY_DEFAULT_CHANNEL, channel.name());
    }

    /**
     * The channel pinned to one rule id or category, or {@code null} when that scope
     * should inherit. Null and "inherit" are the same thing here, and the settings
     * screen offers it as an explicit choice - otherwise there is no way back to the
     * default once a rule has been pinned.
     */
    @Nullable
    public static NotificationChannel getChannel(@NotNull String scope) {
        return readChannel(KEY_CHANNEL_PREFIX + scope, null);
    }

    public static void setChannel(@NotNull String scope, @Nullable NotificationChannel channel) {
        PREFS.put(KEY_CHANNEL_PREFIX + scope, channel == null ? INHERIT : channel.name());
    }

    /**
     * How often one rule should run, or {@code null} when the rule's own interval
     * should stand.
     */
    @Nullable
    public static Duration getInterval(@NotNull String sourceId) {
        int minutes = PREFS.getInt(KEY_INTERVAL_PREFIX + sourceId, 0);
        return minutes <= 0 ? null : Duration.ofMinutes(minutes);
    }

    public static void setInterval(@NotNull String sourceId, @Nullable Duration interval) {
        if (interval == null) {
            PREFS.remove(KEY_INTERVAL_PREFIX + sourceId);
        } else {
            PREFS.putInt(KEY_INTERVAL_PREFIX + sourceId, (int) Math.max(1, interval.toMinutes()));
        }
    }

    /**
     * Whether a one-off event notification is switched on. The polled rules answer
     * this themselves through {@link NotificationSource#enabled()}; an event
     * published straight to the centre has no rule object to ask, so its switch
     * lives here under an id the publisher owns.
     */
    public static boolean isEventEnabled(@NotNull String eventId, boolean byDefault) {
        return PREFS.getBoolean(KEY_EVENT_PREFIX + eventId, byDefault);
    }

    public static void setEventEnabled(@NotNull String eventId, boolean enabled) {
        PREFS.putBoolean(KEY_EVENT_PREFIX + eventId, enabled);
    }

    private static NotificationChannel readChannel(String key, NotificationChannel fallback) {
        String stored = PREFS.get(key, null);
        if (stored == null || stored.isBlank()) {
            return fallback;
        }
        try {
            return NotificationChannel.valueOf(stored);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown notification channel '{}' stored under {}", stored, key);
            return fallback;
        }
    }

    /**
     * Copies the stored settings into a live policy.
     *
     * @param categories the categories to restore the mute flag and channel for.
     *                   Preferences cannot be enumerated by prefix cheaply, and the
     *                   caller knows which categories exist because it registered
     *                   the sources.
     */
    public static void applyTo(@NotNull NotificationPolicy policy, @NotNull String... categories) {
        policy.setEnabled(isEnabled());
        policy.setToastThreshold(getToastThreshold());
        policy.setDefaultCooldown(getCooldown());
        policy.setDefaultChannel(getDefaultChannel());
        for (String category : categories) {
            policy.setMuted(category, isMuted(category));
            policy.setChannel(category, getChannel(category));
        }
    }

    /**
     * Restores each rule's stored interval and channel. Separate from
     * {@link #applyTo} because it needs the scheduler as well - the interval is not
     * a policy setting, it is a schedule.
     */
    public static void applyTo(@NotNull NotificationPolicy policy,
                               @NotNull NotificationScheduler scheduler) {
        for (NotificationSource source : scheduler.sources()) {
            String id = source.id();
            policy.setChannel(id, getChannel(id));
            scheduler.setInterval(id, getInterval(id));
        }
    }
}
