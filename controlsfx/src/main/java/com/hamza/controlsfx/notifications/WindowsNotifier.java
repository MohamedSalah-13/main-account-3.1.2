package com.hamza.controlsfx.notifications;

import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

/**
 * Delivers a notification through the Windows notification area, so it reaches
 * the user even when the application window is behind something else.
 * <p>
 * AWT's {@link SystemTray} is used rather than the Windows 10 toast API because
 * the latter needs a registered AppUserModelID and a shortcut in the Start menu -
 * neither of which this application installs. A tray balloon is what is available
 * to a plain JVM, and Windows routes it into the Action Center like any other
 * notification.
 * <p>
 * Two consequences worth knowing:
 * <ul>
 *   <li>A tray icon has to exist for a balloon to come from it. It is created on
 *       the first Windows-channel notification rather than at startup, so a user
 *       who never enables this channel never gets an extra icon in their tray.</li>
 *   <li>Everything here runs on the AWT event thread, not the JavaFX one. Clicking
 *       the balloon therefore hops back to JavaFX before running the action.</li>
 * </ul>
 * If the tray is unsupported or refuses the icon, this reports nothing and returns
 * quietly - {@link #isAvailable()} lets the settings screen say so up front, and
 * the in-app toast remains as the channel that always works.
 */
@Log4j2
public class WindowsNotifier implements NotificationListener {

    private final NotificationPolicy policy;
    private final Supplier<Image> iconSupplier;
    private final Runnable uiThreadHop;

    private TrayIcon trayIcon;
    private boolean trayFailed;

    /**
     * @param iconSupplier the tray icon image; the tray needs one and will not
     *                     accept null
     * @param uiThreadHop  how to get back onto the application's UI thread when the
     *                     user clicks the balloon - injected because this module
     *                     should not assume the notification action is a JavaFX one
     */
    public WindowsNotifier(@NotNull NotificationPolicy policy,
                           @NotNull Supplier<Image> iconSupplier,
                           @NotNull Runnable uiThreadHop) {
        this.policy = policy;
        this.iconSupplier = iconSupplier;
        this.uiThreadHop = uiThreadHop;
    }

    /** Whether this machine can show tray balloons at all. */
    public static boolean isAvailable() {
        try {
            return SystemTray.isSupported();
        } catch (Throwable t) {
            // Headless JVMs throw rather than returning false.
            return false;
        }
    }

    @Override
    public void onNotification(AppNotification notification) {
        if (!policy.shouldToast(notification) || !policy.channelFor(notification).includesWindows()) {
            return;
        }
        EventQueue.invokeLater(() -> display(notification));
    }

    private void display(AppNotification notification) {
        TrayIcon icon = trayIcon();
        if (icon == null) {
            return;
        }

        // Replaced each time: the balloon's action is whatever the newest
        // notification carries, and a stale listener would open the wrong screen.
        for (ActionListener listener : icon.getActionListeners()) {
            icon.removeActionListener(listener);
        }
        if (notification.hasAction()) {
            icon.addActionListener(event -> uiThreadHop.run());
            pendingAction = notification;
        } else {
            pendingAction = null;
        }

        icon.displayMessage(notification.title(), bodyOf(notification), messageTypeOf(notification.severity()));
    }

    /**
     * The notification whose action the next balloon click should run. Held here
     * because {@link TrayIcon}'s listener fires on the AWT thread with no reference
     * to what produced the balloon.
     */
    private volatile AppNotification pendingAction;

    /**
     * Runs the action of the balloon the user just clicked, if it still has one.
     * Called by whatever {@code uiThreadHop} posts onto the UI thread.
     */
    public void runPendingAction() {
        AppNotification notification = pendingAction;
        if (notification == null || notification.onOpen() == null) {
            return;
        }
        try {
            notification.onOpen().run();
        } catch (Exception e) {
            log.error("Opening notification '{}' from the Windows tray failed", notification.key(), e);
        }
    }

    private String bodyOf(AppNotification notification) {
        String body = notification.getOccurrences() > 1
                ? notification.message() + "  (×" + notification.getOccurrences() + ")"
                : notification.message();
        // Windows silently drops a balloon with an empty body.
        return body.isBlank() ? notification.title() : body;
    }

    private TrayIcon.MessageType messageTypeOf(NotificationSeverity severity) {
        return switch (severity) {
            case INFO, SUCCESS -> TrayIcon.MessageType.INFO;
            case WARNING -> TrayIcon.MessageType.WARNING;
            case ERROR, CRITICAL -> TrayIcon.MessageType.ERROR;
        };
    }

    private synchronized TrayIcon trayIcon() {
        if (trayIcon != null) {
            return trayIcon;
        }
        if (trayFailed || !isAvailable()) {
            return null;
        }
        try {
            Image image = iconSupplier.get();
            if (image == null) {
                log.warn("No tray icon image; Windows notifications are unavailable");
                trayFailed = true;
                return null;
            }
            TrayIcon icon = new TrayIcon(image, LanguageManager.getInstance().getString("notification.panel.title"));
            icon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
            return trayIcon;
        } catch (AWTException | RuntimeException e) {
            // Tried once. Retrying on every notification would log the same failure
            // forever on a machine whose tray is simply not going to accept it.
            log.error("Could not add the tray icon; Windows notifications are unavailable", e);
            trayFailed = true;
            return null;
        }
    }

    /** Takes the icon back out of the tray. */
    public synchronized void dispose() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }
}
