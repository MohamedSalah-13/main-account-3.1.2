package com.hamza.controlsfx.notifications;

import com.hamza.controlsfx.language.LanguageManager;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.PopOver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The bell in the toolbar: an icon, an unread badge, and a click that opens the
 * inbox panel.
 * <p>
 * Everything it displays is bound to {@link NotificationCenter}, so it never has
 * to be told that something arrived and there is no refresh call to forget. The
 * badge hides itself at zero rather than showing "0".
 */
public class NotificationBell extends StackPane {

    /** Past this the exact number tells the user nothing and stops fitting the badge. */
    private static final int BADGE_CAP = 99;

    private final NotificationCenter center;
    private final Button button = new Button();
    private final Label badge = new Label();
    private final List<String> stylesheets = new ArrayList<>();

    private NotificationScheduler scheduler;
    private PopOver popOver;
    private String panelTitle = LanguageManager.getInstance().getString("notification.panel.title");

    public NotificationBell(@NotNull NotificationCenter center) {
        this.center = center;
        getStyleClass().add("notification-bell");
        build();
    }

    private void build() {
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.BELL);
        icon.setGlyphSize(18);
        icon.getStyleClass().add("notification-bell-icon");

        button.setGraphic(icon);
        button.getStyleClass().addAll("app-neutral-button", "notification-bell-button");
        button.setTooltip(new Tooltip(panelTitle));
        button.setOnAction(event -> togglePanel());

        badge.getStyleClass().add("notification-badge");
        badge.setMouseTransparent(true);
        badge.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    int unread = center.getUnreadCount();
                    return unread > BADGE_CAP ? BADGE_CAP + "+" : Integer.toString(unread);
                },
                center.unreadCountProperty()));
        badge.visibleProperty().bind(center.unreadCountProperty().greaterThan(0));
        badge.managedProperty().bind(badge.visibleProperty());

        // The bell itself changes state, not only the badge: on a toolbar of text
        // buttons a small number in the corner is easy to miss.
        center.unreadCountProperty().addListener((observable, oldValue, newValue) -> {
            button.getStyleClass().remove("notification-bell-alert");
            if (newValue.intValue() > 0) {
                button.getStyleClass().add("notification-bell-alert");
            }
        });

        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        getChildren().addAll(button, badge);
    }

    /** The heading shown at the top of the panel. Set before the first click. */
    public NotificationBell panelTitle(@NotNull String panelTitle) {
        this.panelTitle = panelTitle;
        button.setTooltip(new Tooltip(panelTitle));
        return this;
    }

    /**
     * The scheduler the panel's refresh button drives. Optional - without one the
     * panel simply has no refresh button.
     */
    public NotificationBell scheduler(NotificationScheduler scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    /**
     * Stylesheets for the panel. A {@link PopOver} lives in its own window with its
     * own scene, so it inherits nothing from the toolbar's - without this the panel
     * renders unthemed. The paths come from the application module, which owns the
     * CSS.
     */
    public NotificationBell stylesheets(@NotNull String... urls) {
        stylesheets.addAll(List.of(urls));
        return this;
    }

    private void togglePanel() {
        if (popOver != null && popOver.isShowing()) {
            popOver.hide();
            return;
        }
        if (popOver == null) {
            NotificationPanel panel = new NotificationPanel(center, scheduler, panelTitle);
            panel.getStylesheets().addAll(stylesheets);
            popOver = new PopOver(panel);
            popOver.setDetachable(false);
            popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
            popOver.setAutoHide(true);
            popOver.getStyleClass().add("notification-popover");
            panel.setOnClosed(popOver::hide);
        }
        popOver.show(button);
    }
}
