package com.hamza.controlsfx.notifications;

import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * The list behind the bell: everything in the inbox, newest first, with the
 * actions that apply to the whole list at the top and the per-entry actions in
 * each row.
 * <p>
 * Bound to the centre's observable list, so entries appear, coalesce and
 * disappear without this class subscribing to anything.
 */
public class NotificationPanel extends VBox {

    private static final double PANEL_WIDTH = 380;
    private static final double PANEL_HEIGHT = 460;

    private final NotificationCenter center;
    private final NotificationScheduler scheduler;
    private final ListView<AppNotification> listView = new ListView<>();

    private Runnable onClosed = () -> {
    };

    public NotificationPanel(@NotNull NotificationCenter center,
                             @Nullable NotificationScheduler scheduler,
                             @NotNull String title) {
        this.center = center;
        this.scheduler = scheduler;

        setPrefSize(PANEL_WIDTH, PANEL_HEIGHT);
        setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        getStyleClass().add("notification-panel");
        setSpacing(0);

        getChildren().addAll(buildHeader(title), buildList());
    }

    /** Called when a row's action opens a screen, so the popup gets out of the way. */
    public void setOnClosed(@NotNull Runnable onClosed) {
        this.onClosed = onClosed;
    }

    private HBox buildHeader(String title) {
        Label heading = new Label(title);
        heading.getStyleClass().add("notification-panel-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 12, 10, 12));
        header.getStyleClass().add("notification-panel-header");
        header.getChildren().addAll(heading, spacer);

        if (scheduler != null) {
            header.getChildren().add(linkButton("تحديث", scheduler::runAllNow));
        }
        header.getChildren().addAll(
                linkButton("تعليم الكل كمقروء", center::markAllRead),
                linkButton("مسح الكل", center::clearAll));

        return header;
    }

    private ListView<AppNotification> buildList() {
        // Sorted rather than relying on insertion order: coalescing moves an entry
        // back to the top, and the comparator keeps that consistent no matter how
        // the list was mutated.
        SortedList<AppNotification> sorted = new SortedList<>(
                center.getInbox(),
                Comparator.comparing(AppNotification::getLastOccurredAt).reversed());

        listView.setItems(sorted);
        listView.setCellFactory(view -> new NotificationCell(center, this::openAndClose));
        listView.setPlaceholder(new Label("لا توجد إشعارات"));
        listView.getStyleClass().add("notification-list");
        VBox.setVgrow(listView, Priority.ALWAYS);
        return listView;
    }

    private void openAndClose(AppNotification notification) {
        onClosed.run();
    }

    private Button linkButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("notification-link-button");
        button.setOnAction(event -> action.run());
        return button;
    }
}
