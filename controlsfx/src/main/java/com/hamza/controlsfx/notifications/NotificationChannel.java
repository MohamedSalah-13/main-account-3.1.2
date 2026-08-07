package com.hamza.controlsfx.notifications;

/**
 * Where an announced notification is shown.
 * <p>
 * The inbox behind the bell is not a channel - everything the policy lets through
 * lands there regardless. This only decides what interrupts the user: the
 * in-application toast, the Windows notification area, or both.
 * <p>
 * The distinction matters because the two behave differently. The in-app toast
 * only exists while the window is open and on top; a Windows notification reaches
 * a cashier who has the browser in front of them, and survives in the Action
 * Center after it fades. Which one is wanted depends on how the machine is used,
 * so it is a setting rather than a decision made here.
 */
public enum NotificationChannel {

    /** The toast drawn by the application itself, in the corner of its own window. */
    IN_APP("داخل البرنامج"),

    /** A Windows notification-area balloon, visible even when the app is behind something. */
    WINDOWS("إشعارات ويندوز"),

    BOTH("الاثنان معًا"),

    /** Inbox only - the entry is recorded behind the bell but nothing pops up. */
    SILENT("بدون تنبيه منبثق");

    private final String displayName;

    NotificationChannel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean includesInApp() {
        return this == IN_APP || this == BOTH;
    }

    public boolean includesWindows() {
        return this == WINDOWS || this == BOTH;
    }
}
