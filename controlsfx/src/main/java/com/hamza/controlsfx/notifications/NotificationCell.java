package com.hamza.controlsfx.notifications;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * One row of the inbox.
 * <p>
 * Cells are recycled, so everything set here is also cleared in the empty branch
 * of {@link #updateItem} - a stale style class on a reused cell is the usual way
 * a list like this ends up showing a red border on a harmless message.
 */
@Log4j2
class NotificationCell extends ListCell<AppNotification> {

    private static final DateTimeFormatter ABSOLUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Duration SNOOZE = Duration.ofHours(8);

    private final NotificationCenter center;
    private final Consumer<AppNotification> onOpened;

    private final FontAwesomeIconView icon = new FontAwesomeIconView();
    private final Label title = new Label();
    private final Label message = new Label();
    private final Label timestamp = new Label();
    private final Label counter = new Label();
    private final Button openButton = new Button();
    private final Button snoozeButton = new Button();
    private final Button dismissButton = new Button();
    private final HBox root;

    NotificationCell(NotificationCenter center, Consumer<AppNotification> onOpened) {
        this.center = center;
        this.onOpened = onOpened;

        icon.setGlyphSize(16);

        title.getStyleClass().add("notification-item-title");
        title.setWrapText(true);
        message.getStyleClass().add("notification-item-message");
        message.setWrapText(true);
        timestamp.getStyleClass().add("notification-item-time");
        counter.getStyleClass().add("notification-item-counter");

        iconButton(openButton, FontAwesomeIcon.EXTERNAL_LINK, "فتح");
        iconButton(snoozeButton, FontAwesomeIcon.CLOCK_ALT, "تأجيل 8 ساعات");
        iconButton(dismissButton, FontAwesomeIcon.TIMES, "إخفاء");

        HBox meta = new HBox(6, timestamp, counter);
        meta.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(2, title, message, meta);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);

        Region spacer = new Region();
        HBox actions = new HBox(2, openButton, snoozeButton, dismissButton);
        actions.setAlignment(Pos.TOP_LEFT);

        root = new HBox(8, icon, text, spacer, actions);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(8, 10, 8, 10));
        root.getStyleClass().add("notification-item");

        setPadding(Insets.EMPTY);
    }

    private void iconButton(Button button, FontAwesomeIcon glyph, String tooltip) {
        FontAwesomeIconView view = new FontAwesomeIconView(glyph);
        view.setGlyphSize(12);
        button.setGraphic(view);
        button.getStyleClass().add("notification-item-action");
        button.setTooltip(new Tooltip(tooltip));
    }

    @Override
    protected void updateItem(AppNotification notification, boolean empty) {
        super.updateItem(notification, empty);

        getStyleClass().removeIf(styleClass -> styleClass.startsWith("notification-"));
        getStyleClass().add("notification-row");

        if (empty || notification == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        icon.setIcon(glyphFor(notification.severity()));
        icon.getStyleClass().setAll("notification-item-icon", notification.severity().styleClass());

        title.setText(notification.title());
        message.setText(notification.message());
        message.setVisible(!notification.message().isBlank());
        message.setManaged(!notification.message().isBlank());
        timestamp.setText(relativeTime(notification.getLastOccurredAt()));
        Tooltip.install(timestamp, new Tooltip(notification.getLastOccurredAt().format(ABSOLUTE)));

        boolean repeated = notification.getOccurrences() > 1;
        counter.setText("×" + notification.getOccurrences());
        counter.setVisible(repeated);
        counter.setManaged(repeated);

        boolean hasAction = notification.hasAction();
        openButton.setVisible(hasAction);
        openButton.setManaged(hasAction);
        openButton.setOnAction(event -> open(notification));

        snoozeButton.setOnAction(event -> center.snooze(notification, SNOOZE));
        dismissButton.setOnAction(event -> center.dismiss(notification));

        getStyleClass().add(notification.severity().styleClass());
        if (!notification.isRead()) {
            getStyleClass().add("notification-unread");
        }

        // Clicking the row itself opens it, which is what a user expects from a
        // notification; the buttons stay for the cases where the row has no action.
        root.setOnMouseClicked(event -> {
            center.markRead(notification);
            if (hasAction) {
                open(notification);
            }
        });

        setGraphic(root);
    }

    private void open(AppNotification notification) {
        NotificationCommand command = notification.onOpen();
        center.markRead(notification);
        onOpened.accept(notification);
        if (command == null) {
            return;
        }
        try {
            command.run();
        } catch (Exception e) {
            log.error("Opening notification '{}' failed", notification.key(), e);
        }
    }

    private FontAwesomeIcon glyphFor(NotificationSeverity severity) {
        return switch (severity) {
            case INFO -> FontAwesomeIcon.INFO_CIRCLE;
            case SUCCESS -> FontAwesomeIcon.CHECK_CIRCLE;
            case WARNING -> FontAwesomeIcon.EXCLAMATION_TRIANGLE;
            case ERROR -> FontAwesomeIcon.TIMES_CIRCLE;
            case CRITICAL -> FontAwesomeIcon.EXCLAMATION_CIRCLE;
        };
    }

    /**
     * "منذ 5 دقائق" reads better than a timestamp for anything recent, which is
     * what most of the inbox is. Older entries fall back to the date.
     */
    private String relativeTime(LocalDateTime at) {
        Duration age = Duration.between(at, LocalDateTime.now());
        if (age.isNegative() || age.toMinutes() < 1) {
            return "الآن";
        }
        if (age.toHours() < 1) {
            return "منذ " + age.toMinutes() + " دقيقة";
        }
        if (age.toDays() < 1) {
            return "منذ " + age.toHours() + " ساعة";
        }
        if (age.toDays() < 7) {
            return "منذ " + age.toDays() + " يوم";
        }
        return at.format(ABSOLUTE);
    }
}
