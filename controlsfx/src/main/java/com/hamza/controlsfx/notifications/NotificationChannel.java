package com.hamza.controlsfx.notifications;

import com.hamza.controlsfx.language.LanguageManager;

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
    IN_APP(LanguageManager.getInstance().getString("notification.channel.in.app")),

    /** A Windows notification-area balloon, visible even when the app is behind something. */
    WINDOWS(LanguageManager.getInstance().getString("notification.channel.windows")),

    BOTH(LanguageManager.getInstance().getString("notification.channel.both")),

    /** Inbox only - the entry is recorded behind the bell but nothing pops up. */
    SILENT(LanguageManager.getInstance().getString("notification.channel.silent"));

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
