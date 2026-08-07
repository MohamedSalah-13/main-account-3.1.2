package com.hamza.controlsfx.notifications;

/**
 * How loud a notification is. Everything that varies with importance - whether a
 * toast pops, whether the machine beeps, which colour the row gets, how long the
 * toast stays on screen - is derived from this rather than decided at each call
 * site, so a new notification only has to pick a level.
 */
public enum NotificationSeverity {

    /** Something happened that the user may want to know about. No toast by default. */
    INFO("notification-info", 0, 4),

    /** An operation finished as intended - a backup written, an invoice posted. */
    SUCCESS("notification-success", 1, 4),

    /** Something needs attention but nothing is broken - stock running low, a customer over limit. */
    WARNING("notification-warning", 2, 8),

    /** An operation failed. */
    ERROR("notification-error", 3, 10),

    /** The application cannot keep working correctly until this is dealt with. */
    CRITICAL("notification-critical", 4, 0);

    private final String styleClass;
    private final int weight;
    private final int toastSeconds;

    NotificationSeverity(String styleClass, int weight, int toastSeconds) {
        this.styleClass = styleClass;
        this.weight = weight;
        this.toastSeconds = toastSeconds;
    }

    /**
     * The CSS class applied to the row in the inbox and to the toast, so themes can
     * colour severities without any of this code knowing about colours.
     */
    public String styleClass() {
        return styleClass;
    }

    /** Higher means more important. Used for sorting and for threshold comparisons. */
    public int weight() {
        return weight;
    }

    /**
     * How long the toast stays up. {@code 0} means it stays until dismissed, which
     * is what {@link #CRITICAL} needs - a message that vanishes on its own is no
     * use for something the user has to act on.
     */
    public int toastSeconds() {
        return toastSeconds;
    }

    public boolean atLeast(NotificationSeverity other) {
        return weight >= other.weight;
    }
}
