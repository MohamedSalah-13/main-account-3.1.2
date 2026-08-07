package com.hamza.account.features.notification;

import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationCenter;
import com.hamza.controlsfx.notifications.NotificationCommand;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import org.jetbrains.annotations.NotNull;

/**
 * The short way to notify about something that just happened.
 * <p>
 * A {@link com.hamza.controlsfx.notifications.NotificationSource} is for a
 * condition someone has to go and check; this is for an event the code already
 * knows about - a backup that failed, an import that finished, a shift that was
 * closed. Safe from any thread and from anywhere in the application, because
 * everything ends up in {@link NotificationCenter#publish}.
 * <p>
 * Each call needs a key. Use a constant one ({@code "backup.manual"}) when
 * repeats of the same event should collapse into a single entry, and a unique one
 * ({@code "import." + fileName}) when each occurrence deserves its own row.
 */
public final class AppNotifications {

    private AppNotifications() {
    }

    public static void info(@NotNull String key, @NotNull String category,
                            @NotNull String title, @NotNull String message) {
        publish(key, category, NotificationSeverity.INFO, title, message);
    }

    public static void success(@NotNull String key, @NotNull String category,
                               @NotNull String title, @NotNull String message) {
        publish(key, category, NotificationSeverity.SUCCESS, title, message);
    }

    public static void warn(@NotNull String key, @NotNull String category,
                            @NotNull String title, @NotNull String message) {
        publish(key, category, NotificationSeverity.WARNING, title, message);
    }

    public static void error(@NotNull String key, @NotNull String category,
                             @NotNull String title, @NotNull String message) {
        publish(key, category, NotificationSeverity.ERROR, title, message);
    }

    /**
     * The one the user cannot miss and that will not time out on its own. Reserve
     * it for states the application cannot keep working correctly in.
     */
    public static void critical(@NotNull String key, @NotNull String category,
                                @NotNull String title, @NotNull String message) {
        publish(key, category, NotificationSeverity.CRITICAL, title, message);
    }

    /** As above but the entry opens a screen when clicked. */
    public static void withAction(@NotNull String key, @NotNull String category,
                                  @NotNull NotificationSeverity severity,
                                  @NotNull String title, @NotNull String message,
                                  @NotNull String actionLabel, @NotNull NotificationCommand action) {
        NotificationCenter.getInstance().publish(AppNotification.builder(key)
                .category(category)
                .severity(severity)
                .title(title)
                .message(message)
                .onOpen(actionLabel, action)
                .build());
    }

    private static void publish(String key, String category, NotificationSeverity severity,
                                String title, String message) {
        NotificationCenter.getInstance().publish(key, category, severity, title, message);
    }
}
