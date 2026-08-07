package com.hamza.controlsfx.notifications;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.Notifications;

import java.awt.Toolkit;
import java.util.function.Function;

/**
 * Shows an announced notification as a corner toast.
 * <p>
 * A {@link NotificationListener} rather than something the centre calls directly,
 * so the toast can be turned off, replaced, or joined by other presenters without
 * the centre knowing. Whether a given notification gets here at all is decided by
 * {@link NotificationPolicy#shouldToast}.
 */
@Log4j2
public class NotificationToaster implements NotificationListener {

    private final NotificationPolicy policy;

    private Pos position = Pos.BOTTOM_LEFT;
    private boolean soundEnabled = true;
    private Function<AppNotification, Node> graphicFactory = notification -> null;

    public NotificationToaster(NotificationPolicy policy) {
        this.policy = policy;
    }

    /** Where toasts appear. RTL screens read better with the toast on the left. */
    public NotificationToaster position(Pos position) {
        this.position = position;
        return this;
    }

    public NotificationToaster sound(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        return this;
    }

    /**
     * Supplies the icon. Kept as a callback because the images live in the
     * application module, which this one cannot see.
     */
    public NotificationToaster graphic(Function<AppNotification, Node> graphicFactory) {
        this.graphicFactory = graphicFactory;
        return this;
    }

    @Override
    public void onNotification(AppNotification notification) {
        if (!policy.shouldToast(notification) || !policy.channelFor(notification).includesInApp()) {
            return;
        }

        Notifications toast = Notifications.create()
                .title(notification.title())
                .text(bodyOf(notification))
                .position(position)
                .hideAfter(hideAfter(notification))
                .graphic(graphicFactory.apply(notification))
                .onAction(event -> open(notification));

        // The severity ends up on the popup as a style class, so themes can colour
        // a warning differently from an error without any code change here.
        toast.styleClass(notification.severity().styleClass());

        if (soundEnabled && notification.severity().atLeast(NotificationSeverity.WARNING)) {
            beep();
        }
        toast.show();
    }

    /**
     * A repeat says so rather than looking identical to the first one - otherwise
     * the user cannot tell a stale toast from a condition that just re-fired.
     */
    private String bodyOf(AppNotification notification) {
        if (notification.getOccurrences() > 1) {
            return notification.message() + "  (×" + notification.getOccurrences() + ")";
        }
        return notification.message();
    }

    private Duration hideAfter(AppNotification notification) {
        int seconds = notification.severity().toastSeconds();
        return seconds <= 0 ? Duration.INDEFINITE : Duration.seconds(seconds);
    }

    private void open(AppNotification notification) {
        NotificationCommand command = notification.onOpen();
        if (command == null) {
            return;
        }
        try {
            command.run();
        } catch (Exception e) {
            log.error("Opening notification '{}' failed", notification.key(), e);
        }
    }

    private void beep() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception | Error e) {
            // Headless machines and some Windows sound configurations throw here.
            // A missing beep is not worth failing the notification over.
            log.debug("Could not beep for the notification", e);
        }
    }
}
