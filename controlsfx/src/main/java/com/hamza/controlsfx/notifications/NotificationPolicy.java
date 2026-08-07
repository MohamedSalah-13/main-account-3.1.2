package com.hamza.controlsfx.notifications;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides what happens to a notification before it reaches the inbox.
 * <p>
 * This is the part that keeps the feature usable rather than annoying. A rule
 * that polls every five minutes reports the same twelve low-stock items every
 * five minutes; without a policy the inbox is nothing but that one condition
 * repeated, the toast pops every five minutes, and the user turns the whole
 * feature off. So a repeat of a condition already in the inbox is folded into the
 * existing entry, and a repeat inside the cooldown window is not re-announced at
 * all.
 * <p>
 * Deliberately free of JavaFX and of any storage: it takes the candidate, the
 * entry already in the inbox under that key (or {@code null}), and the current
 * time, and returns a decision. That makes every rule here testable without a
 * toolkit or a database.
 */
public class NotificationPolicy {

    /** Long enough that a five-minute poll of an unchanged condition stays quiet. */
    public static final Duration DEFAULT_COOLDOWN = Duration.ofHours(4);

    /** Past this the oldest entries are discarded; an unbounded inbox is a leak. */
    public static final int DEFAULT_MAX_INBOX = 200;

    private final Map<String, Duration> cooldownByCategory = new ConcurrentHashMap<>();
    private final Set<String> mutedCategories = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> snoozedUntil = new ConcurrentHashMap<>();
    private final Map<String, NotificationChannel> channelByScope = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile Duration defaultCooldown = DEFAULT_COOLDOWN;
    private volatile int maxInbox = DEFAULT_MAX_INBOX;
    private volatile NotificationSeverity toastThreshold = NotificationSeverity.WARNING;
    private volatile NotificationChannel defaultChannel = NotificationChannel.IN_APP;

    /**
     * What the centre should do with a candidate.
     *
     * @param outcome     add a row, fold it into the existing one, or drop it
     * @param announce    whether the presenters (toast, sound) should be told;
     *                    false for a repeat that is still inside its cooldown, which
     *                    updates the inbox silently
     */
    public record Decision(Outcome outcome, boolean announce) {

        public boolean isDropped() {
            return outcome == Outcome.DROP;
        }
    }

    public enum Outcome {
        ADD,
        COALESCE,
        DROP
    }

    /**
     * @param candidate the notification a source or a call site produced
     * @param existing  the entry already in the inbox under the same key, if any
     * @param now       the current time, passed in so tests do not have to wait
     */
    public Decision decide(@NotNull AppNotification candidate,
                           @Nullable AppNotification existing,
                           @NotNull LocalDateTime now) {
        if (!enabled) {
            return new Decision(Outcome.DROP, false);
        }
        if (mutedCategories.contains(candidate.category())) {
            return new Decision(Outcome.DROP, false);
        }
        if (isSnoozed(candidate.key(), now)) {
            return new Decision(Outcome.DROP, false);
        }

        // CRITICAL is exempt from cooldown: the whole point of the level is that the
        // user has to see it, and a condition that keeps re-firing at that level is
        // one that keeps being true.
        if (existing == null) {
            return new Decision(Outcome.ADD, true);
        }

        boolean withinCooldown = candidate.severity() != NotificationSeverity.CRITICAL
                && existing.getLastOccurredAt() != null
                && existing.getLastOccurredAt().plus(cooldownFor(candidate.category())).isAfter(now);

        return new Decision(Outcome.COALESCE, !withinCooldown);
    }

    /**
     * Whether an announced notification is loud enough to interrupt at all.
     * Everything reaches the inbox; only what clears the threshold pops up, on
     * whichever channel {@link #channelFor} resolves to.
     */
    public boolean shouldToast(@NotNull AppNotification notification) {
        return enabled && notification.severity().atLeast(toastThreshold);
    }

    /**
     * Where to show it. Most specific wins: a setting on the rule that produced it,
     * then one on its category, then the global default.
     * <p>
     * Two levels rather than one because the useful cases differ - "send everything
     * about the treasury to Windows" is a category choice, "only the low-stock
     * warning, the rest can stay in the app" is a rule choice.
     */
    @NotNull
    public NotificationChannel channelFor(@NotNull AppNotification notification) {
        String sourceId = notification.sourceId();
        if (sourceId != null) {
            NotificationChannel bySource = channelByScope.get(sourceId);
            if (bySource != null) {
                return bySource;
            }
        }
        return channelByScope.getOrDefault(notification.category(), defaultChannel);
    }

    /**
     * Pins a channel for one rule id or one category. Passing {@code null} removes
     * the override so the scope falls back to the level above it.
     */
    public void setChannel(@NotNull String scope, NotificationChannel channel) {
        if (channel == null) {
            channelByScope.remove(scope);
        } else {
            channelByScope.put(scope, channel);
        }
    }

    @Nullable
    public NotificationChannel channelOverride(@NotNull String scope) {
        return channelByScope.get(scope);
    }

    @NotNull
    public NotificationChannel getDefaultChannel() {
        return defaultChannel;
    }

    public void setDefaultChannel(@NotNull NotificationChannel defaultChannel) {
        this.defaultChannel = defaultChannel;
    }

    public boolean isSnoozed(@NotNull String key, @NotNull LocalDateTime now) {
        LocalDateTime until = snoozedUntil.get(key);
        if (until == null) {
            return false;
        }
        if (until.isAfter(now)) {
            return true;
        }
        snoozedUntil.remove(key);
        return false;
    }

    /** Silences one condition for a while without muting everything in its category. */
    public void snooze(@NotNull String key, @NotNull Duration duration, @NotNull LocalDateTime now) {
        snoozedUntil.put(key, now.plus(duration));
    }

    public void clearSnooze(@NotNull String key) {
        snoozedUntil.remove(key);
    }

    public Duration cooldownFor(@NotNull String category) {
        return cooldownByCategory.getOrDefault(category, defaultCooldown);
    }

    /** Lets a noisy category be given a longer window than the global default. */
    public NotificationPolicy cooldownFor(@NotNull String category, @NotNull Duration cooldown) {
        cooldownByCategory.put(category, cooldown);
        return this;
    }

    public boolean isMuted(@NotNull String category) {
        return mutedCategories.contains(category);
    }

    public void setMuted(@NotNull String category, boolean muted) {
        if (muted) {
            mutedCategories.add(category);
        } else {
            mutedCategories.remove(category);
        }
    }

    public Set<String> mutedCategories() {
        return Set.copyOf(mutedCategories);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultCooldown() {
        return defaultCooldown;
    }

    public void setDefaultCooldown(@NotNull Duration defaultCooldown) {
        this.defaultCooldown = defaultCooldown;
    }

    public int getMaxInbox() {
        return maxInbox;
    }

    public void setMaxInbox(int maxInbox) {
        this.maxInbox = Math.max(1, maxInbox);
    }

    public NotificationSeverity getToastThreshold() {
        return toastThreshold;
    }

    public void setToastThreshold(@NotNull NotificationSeverity toastThreshold) {
        this.toastThreshold = toastThreshold;
    }
}
