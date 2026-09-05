package com.hamza.controlsfx.notifications;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Shows an announced notification as a corner toast.
 * <p>
 * A {@link NotificationListener} rather than something the centre calls directly,
 * so the toast can be turned off, replaced, or joined by other presenters without
 * the centre knowing. Whether a given notification gets here at all is decided by
 * {@link NotificationPolicy#shouldToast}.
 *
 * <h2>Why this is hand-rolled rather than {@code org.controlsfx.control.Notifications}</h2>
 * ControlsFX installs its own stylesheet <b>into the owner window's scene</b> the
 * first time a toast is shown over it:
 * <pre>
 *     Scene ownerScene = ownerWindow.getScene();
 *     if (!ownerScene.getStylesheets().contains(url)) {
 *         ownerScene.getStylesheets().add(0, url);
 *     }
 * </pre>
 * Adding a stylesheet at index 0 forces a full CSS reapplication of that scene, and
 * a {@code TableView} rebuilds its cells when that happens - <b>which destroys an open
 * cell editor</b>. On the quick invoice that is the operator's entry row: the first
 * toast of the session silently cancelled the edit mid-scan, the keystrokes that
 * followed went to the scene instead of the cell, and Enter reached the "new invoice"
 * button. A confirmation dialog was all that stood between a stock alert and a
 * discarded invoice.
 * <p>
 * There is no way to prevent that from outside: ControlsFX offers no switch for it,
 * and the stylesheet cannot be pre-installed because its module opens
 * {@code org.controlsfx.control} only to {@code org.controlsfx.fxsampler}, so the
 * resource is encapsulated from this module on the module path.
 * <p>
 * So the toast is drawn here instead. <b>The two properties that matter: it never
 * touches the owner's scene, and it never takes focus.</b> A {@link Popup} is not an
 * activated window, its content is not focus-traversable, and the stylesheet it needs
 * goes onto the popup's own scene - which this module owns.
 */
@Log4j2
public class NotificationToaster implements NotificationListener {

    /**
     * Marks a window that must never have a toast appear over it. Set it on the window
     * ({@code stage.getProperties().put(SUPPRESS_TOASTS, true)}) and while that window is
     * showing, no toast is shown at all - not over it, and not over anything behind it,
     * since a popup owned by a window underneath a full-screen one still draws on top.
     * <p>
     * It exists because the price-check screen hangs on a shop wall facing customers, and
     * the first time it was run a toast announced "709 items are running low" to whoever
     * was standing in front of it. Which business facts a screen may show is the
     * application's judgement, not this module's, so the window declares it rather than
     * this class knowing any screen by name.
     * <p>
     * Nothing is lost: the notification is already in the inbox behind the bell, which is
     * where the operator reads it.
     */
    public static final String SUPPRESS_TOASTS = "app.notifications.suppressToasts";

    private static final String STYLESHEET = "/com/hamza/controlsfx/css/notification-toast.css";
    private static final double WIDTH = 380;
    private static final double SCREEN_MARGIN = 16;
    private static final double GAP_BETWEEN_TOASTS = 8;
    private static final Duration FADE = Duration.millis(220);

    private final NotificationPolicy policy;

    /** The toasts currently on screen, oldest first, so they can be stacked. */
    private final List<Popup> showing = new ArrayList<>();

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

        if (toastsSuppressedByAnyWindow()) {
            log.debug("A window is suppressing toasts; '{}' stays in the inbox only", notification.key());
            return;
        }

        Window owner = ownerWindow();
        if (owner == null) {
            // Nothing to hang a popup on - the inbox behind the bell still has the entry.
            log.debug("No window to show the toast for '{}' over", notification.key());
            return;
        }

        if (soundEnabled && notification.severity().atLeast(NotificationSeverity.WARNING)) {
            beep();
        }
        show(owner, notification);
    }

    /**
     * Whether any window on screen has declared {@link #SUPPRESS_TOASTS}. Static and public
     * because {@code WindowsNotifier} asks the same question - a tray balloon saying over
     * the taskbar what the toast was stopped from saying is the same leak.
     */
    public static boolean toastsSuppressedByAnyWindow() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && Boolean.TRUE.equals(window.getProperties().get(SUPPRESS_TOASTS))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The window to hang the popup on: the focused one, or any showing one. Popups are
     * skipped - a popup owned by a popup dies with it, and the toast should outlive a
     * combo list or another toast.
     * <p>
     * The owner is used for nothing but ownership. Position comes from the screen, and
     * the owner's scene is never read or written - see the class comment.
     */
    private Window ownerWindow() {
        Window fallback = null;
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupWindow || !window.isShowing()) {
                continue;
            }
            if (window.isFocused()) {
                return window;
            }
            fallback = window;
        }
        return fallback;
    }

    private void show(Window owner, AppNotification notification) {
        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);
        popup.getContent().add(toastNode(popup, notification));

        // Transparent until it has been positioned: Popup.show puts it at 0,0 and the
        // corner is decided a few statements later, which would otherwise flash.
        popup.getContent().getFirst().setOpacity(0);
        popup.show(owner);
        if (popup.getScene() != null) {
            var stylesheet = getClass().getResource(STYLESHEET);
            if (stylesheet != null) {
                popup.getScene().getStylesheets().add(stylesheet.toExternalForm());
            }
            popup.getScene().setNodeOrientation(owner.getScene() == null
                    ? javafx.geometry.NodeOrientation.INHERIT
                    : owner.getScene().getNodeOrientation());
        }

        showing.add(popup);
        // A popup dies with the window that owns it - an invoice being closed, say - and
        // that happens without going through hide(), so the list has to be told.
        popup.showingProperty().addListener((observable, wasShowing, isShowing) -> {
            if (!isShowing) {
                forget(popup);
            }
        });
        layoutToasts(owner);
        fadeIn(popup);
        scheduleHide(popup, notification);
    }

    private Region toastNode(Popup popup, AppNotification notification) {
        Label title = new Label(notification.title());
        title.getStyleClass().add("toast-title");
        title.setWrapText(true);

        Label body = new Label(bodyOf(notification));
        body.getStyleClass().add("toast-body");
        body.setWrapText(true);

        VBox text = new VBox(2, title, body);
        text.setFillWidth(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox layout = new HBox(10);
        Node graphic = graphicFactory.apply(notification);
        if (graphic != null) {
            layout.getChildren().add(graphic);
        }
        layout.getChildren().add(text);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setPadding(new Insets(12, 14, 12, 14));
        layout.setPrefWidth(WIDTH);
        layout.setMaxWidth(WIDTH);
        // The severity is a style class, so a theme can colour a warning differently
        // from an error without any code change here - as it was with ControlsFX.
        layout.getStyleClass().addAll("notification-toast", notification.severity().styleClass());
        // A toast is never a focus target: it must not take the caret from the till.
        layout.setFocusTraversable(false);

        layout.setOnMouseClicked(event -> {
            open(notification);
            hide(popup);
        });
        return layout;
    }

    /** Stacks the toasts against the chosen corner, newest nearest the edge it grows from. */
    private void layoutToasts(Window owner) {
        Rectangle2D bounds = screenFor(owner);
        boolean fromTop = position == Pos.TOP_LEFT || position == Pos.TOP_CENTER || position == Pos.TOP_RIGHT;
        double offset = 0;
        for (Popup popup : showing) {
            double height = popup.getContent().getFirst().prefHeight(WIDTH);
            popup.setX(anchorX(bounds));
            popup.setY(fromTop
                    ? bounds.getMinY() + SCREEN_MARGIN + offset
                    : bounds.getMaxY() - SCREEN_MARGIN - height - offset);
            offset += height + GAP_BETWEEN_TOASTS;
        }
    }

    private double anchorX(Rectangle2D bounds) {
        return switch (position) {
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> bounds.getMaxX() - WIDTH - SCREEN_MARGIN;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> bounds.getMinX() + (bounds.getWidth() - WIDTH) / 2;
            default -> bounds.getMinX() + SCREEN_MARGIN;
        };
    }

    private Rectangle2D screenFor(Window owner) {
        return Screen.getScreensForRectangle(owner.getX(), owner.getY(), 1, 1).stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private void fadeIn(Popup popup) {
        Node node = popup.getContent().getFirst();
        FadeTransition fade = new FadeTransition(FADE, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * A severity whose {@code toastSeconds} is zero stays until it is clicked -
     * {@code CRITICAL} is the case, and a message that vanishes on its own is no use
     * there.
     */
    private void scheduleHide(Popup popup, AppNotification notification) {
        int seconds = notification.severity().toastSeconds();
        if (seconds <= 0) {
            return;
        }
        Node node = popup.getContent().getFirst();
        FadeTransition fade = new FadeTransition(FADE, node);
        fade.setFromValue(1);
        fade.setToValue(0);
        SequentialTransition hide = new SequentialTransition(
                new PauseTransition(Duration.seconds(seconds)), fade);
        hide.setOnFinished(event -> hide(popup));
        hide.play();
    }

    private void hide(Popup popup) {
        if (!showing.contains(popup)) {
            return;
        }
        popup.hide();
    }

    /** Drops a popup that has gone, however it went, and closes the gap it left. */
    private void forget(Popup popup) {
        if (!showing.remove(popup)) {
            return;
        }
        Window owner = ownerWindow();
        if (owner != null) {
            layoutToasts(owner);
        }
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
