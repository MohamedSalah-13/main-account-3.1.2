package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.AppVersionInfo;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.startup.StartupLine;
import com.hamza.account.features.startup.StartupLogTail;
import com.hamza.account.features.startup.StartupProgress;
import com.hamza.account.features.startup.StartupSnapshot;
import com.hamza.account.features.startup.StartupTracker;
import com.hamza.controlsfx.error.ErrorReport;
import com.hamza.controlsfx.error.ErrorReporter;
import com.hamza.controlsfx.alert.MessageLines;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.awt.Desktop;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * What the program is doing between being launched and showing the sign-in window: a heading naming the
 * step, a line per step - and per migration while the database is updated - with its time, a bar, and the
 * program's own log behind "technical details".
 *
 * <p>It replaced a spinner over one sentence, "preparing the program", that did not change for the whole
 * start. A first install runs eighty migrations and an update takes a {@code mysqldump} first, and on a
 * slow machine that was a minute of the same sentence; a server that did not answer looked exactly like
 * one that was working.</p>
 *
 * <p>The lines are the program's words in the user's language ({@code features/startup} decides them,
 * without JavaFX). The technical log is the file support reads, in English and left to right, and is
 * closed until asked for: a stack trace or a SQL fragment is not something to put in front of a cashier,
 * but it is exactly what a technician on the phone needs. A failure says where the start stopped, the
 * sentence and reference code {@link ErrorReporter} gives it, and offers the details to copy.</p>
 */
@Log4j2
public final class StartupWindow {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** A step quicker than this is not worth a figure beside it. */
    private static final long SHOW_ELAPSED_FROM_SECONDS = 1;
    /** How tall the window grows to when a start fails and the log opens under the message. */
    private static final double FAILED_HEIGHT = 660;

    private final Stage stage;
    private final StartupTracker tracker;
    private final StartupLogTail logTail = StartupLogTail.from(Path.of("logs", "app.log"));

    private final Label heading = new Label();
    private final Label percent = new Label("0%");
    private final ProgressBar bar = new ProgressBar(0);
    private final ListView<StartupLine> lines = new ListView<>();
    private final ToggleButton detailsToggle = new ToggleButton(text("startup.details.show"));
    private final TextArea details = new TextArea();
    private final VBox failure = new VBox(8);
    private final Label failureMessage = new Label();
    private final Label failureReference = new Label();
    private final Timeline ticker;

    private StartupSnapshot shown;
    private String version = "";

    public StartupWindow(Stage stage) {
        this.stage = stage;
        this.tracker = new StartupTracker(Clock.systemDefaultZone(),
                snapshot -> Platform.runLater(() -> render(snapshot)));
        this.ticker = new Timeline(new KeyFrame(Duration.millis(500), event -> tick()));
        this.ticker.setCycleCount(Timeline.INDEFINITE);

        Scene scene = new Scene(build(), 680, 470);
        ThemeManager.apply(scene);
        scene.getStylesheets().add(Objects.requireNonNull(Main.class.getResource("css/startup.css")).toExternalForm());
        ChangeOrientation.sceneOrientation(scene);

        stage.setTitle(text("startup.title"));
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(400);
        stage.setOnCloseRequest(event -> Platform.exit());
        try (InputStream icon = Main.class.getResourceAsStream("image/tools.png")) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        } catch (Exception e) {
            log.debug("No window icon for the startup window", e);
        }
        render(tracker.snapshot());
        stage.show();
        ticker.play();
    }

    /** What the start-up work reports to. */
    public StartupProgress progress() {
        return tracker;
    }

    /** The start failed: the running line turns red and the window says why, with a reference to quote. */
    public void failed(Throwable error) {
        tracker.fail();
        ErrorReport report = ErrorReporter.shared().report(text("startup.operation"), error);
        // Broken into lines here: JavaFX on Linux clips a wrapped Arabic sentence (see MessageLines).
        Text measure = new Text();
        measure.setFont(failureMessage.getFont());
        failureMessage.setText(MessageLines.wrap(report.message(), stage.getWidth() - 100, line -> {
            measure.setText(line);
            return measure.getLayoutBounds().getWidth();
        }));
        // The reporter's sentence usually names the reference itself; said twice, it reads as two errors.
        boolean referenceApart = report.hasReferenceId() && !report.message().contains(report.referenceId());
        failureReference.setText(report.hasReferenceId()
                ? LanguageManager.getInstance().getString("startup.reference", report.referenceId()) : "");
        failureReference.setVisible(referenceApart);
        failureReference.setManaged(referenceApart);
        failure.setVisible(true);
        failure.setManaged(true);
        // Whoever is reading a failure is about to be asked what the log says - and the steps, the
        // message and the log need more room than a start that is going well.
        detailsToggle.setSelected(true);
        showDetails(true);
        double room = Screen.getPrimary().getVisualBounds().getHeight() - 40;
        if (stage.getHeight() < FAILED_HEIGHT && room > stage.getHeight()) {
            stage.setHeight(Math.min(FAILED_HEIGHT, room));
        }
    }

    /** The window's work is over - the sign-in is about to take the stage. */
    public void close() {
        ticker.stop();
    }

    // ---- the window -------------------------------------------------------------------

    private VBox build() {
        Label name = new Label(text("startup.app.name"));
        name.getStyleClass().add("startup-product-name");
        Label versionLabel = new Label(versionText());
        versionLabel.getStyleClass().add("startup-version");
        HBox brand = new HBox(12, logo(), new VBox(2, name, versionLabel));
        brand.setAlignment(Pos.CENTER_LEFT);

        heading.getStyleClass().add("startup-heading");
        heading.setWrapText(true);

        lines.getStyleClass().add("startup-log");
        lines.setId("startup-log");
        lines.setFocusTraversable(false);
        lines.setCellFactory(list -> new LineCell());
        VBox.setVgrow(lines, Priority.ALWAYS);

        details.getStyleClass().add("startup-details");
        details.setId("startup-details");
        details.setEditable(false);
        details.setWrapText(false);
        details.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        details.setPrefRowCount(8);
        details.setMinHeight(130);
        VBox.setVgrow(details, Priority.ALWAYS);
        details.setVisible(false);
        details.setManaged(false);
        detailsToggle.setGraphic(AppIcon.INFO.graphic());
        detailsToggle.getStyleClass().add("app-neutral-button");
        detailsToggle.setId("startup-details-toggle");
        detailsToggle.setOnAction(event -> showDetails(detailsToggle.isSelected()));

        bar.getStyleClass().add("startup-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);
        percent.getStyleClass().add("startup-percent");
        percent.setMinWidth(Region.USE_PREF_SIZE);
        HBox progressRow = new HBox(10, bar, percent);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolsRow = new HBox(8, detailsToggle, spacer);
        toolsRow.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, brand, heading, lines, details, failurePane(), toolsRow, progressRow);
        root.getStyleClass().addAll("app-root", "startup-root");
        return root;
    }

    private VBox failurePane() {
        failureMessage.getStyleClass().add("startup-failure-message");
        failureMessage.setMinHeight(Region.USE_PREF_SIZE);
        failureReference.getStyleClass().add("startup-failure-reference");

        Button copy = new Button(text("startup.copy"), AppIcon.DUPLICATE.graphic());
        copy.getStyleClass().add("app-neutral-button");
        copy.setOnAction(event -> copyDetails());
        Button openLogs = new Button(text("startup.open.logs"), AppIcon.HISTORY.graphic());
        openLogs.getStyleClass().add("app-neutral-button");
        openLogs.setOnAction(event -> openLogsFolder());
        openLogs.setDisable(!logTail.file().getParent().toFile().isDirectory());
        Button close = new Button(text("common.close"), AppIcon.CLOSE.graphic());
        close.getStyleClass().add("app-danger-button");
        close.setOnAction(event -> Platform.exit());

        HBox actions = new HBox(8, copy, openLogs, close);
        actions.setAlignment(Pos.CENTER_LEFT);
        failure.getChildren().setAll(failureMessage, failureReference, actions);
        failure.getStyleClass().add("startup-failure");
        failure.setVisible(false);
        failure.setManaged(false);
        return failure;
    }

    private Node logo() {
        try (InputStream icon = Main.class.getResourceAsStream("image/tools.png")) {
            if (icon != null) {
                ImageView view = new ImageView(new Image(icon));
                view.setFitWidth(44);
                view.setFitHeight(44);
                view.setPreserveRatio(true);
                return view;
            }
        } catch (Exception e) {
            log.debug("No logo for the startup window", e);
        }
        return AppIcon.SETTINGS.graphic(40);
    }

    private String versionText() {
        try {
            version = new AppVersionInfo().getAppVersion();
            return LanguageManager.getInstance().getString("startup.version", version);
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---- drawing ----------------------------------------------------------------------

    private void render(StartupSnapshot snapshot) {
        shown = snapshot;
        lines.getItems().setAll(snapshot.lines());
        if (!lines.getItems().isEmpty()) {
            lines.scrollTo(lines.getItems().size() - 1);
        }
        bar.setProgress(snapshot.progress());
        if (snapshot.outcome() == StartupSnapshot.Outcome.FAILED && !bar.getStyleClass().contains("startup-progress-failed")) {
            bar.getStyleClass().add("startup-progress-failed");
        }
        percent.setText(Math.round(snapshot.progress() * 100) + "%");
        heading.setText(switch (snapshot.outcome()) {
            case READY -> text("startup.ready");
            case FAILED -> text("startup.failed");
            case RUNNING -> snapshot.current() == null ? text("startup.preparing") : text(snapshot.current().messageKey());
        });
    }

    /** Twice a second: the running line's time moves on, and the log panel reads what was written. */
    private void tick() {
        if (shown != null && shown.outcome() == StartupSnapshot.Outcome.RUNNING) {
            lines.refresh();
        }
        if (details.isVisible() && logTail.poll()) {
            fillDetails();
        }
    }

    private void showDetails(boolean show) {
        details.setVisible(show);
        details.setManaged(show);
        if (show) {
            logTail.poll();
            fillDetails();
        }
    }

    private void fillDetails() {
        List<String> logged = logTail.lines();
        details.setText(logged.isEmpty() ? text("startup.details.empty") : String.join("\n", logged));
        details.positionCaret(details.getLength());
        details.setScrollTop(Double.MAX_VALUE);
    }

    /** What support needs from a failed start, in one paste: where, what, the reference and the log. */
    private void copyDetails() {
        logTail.poll();
        StringBuilder text = new StringBuilder()
                .append(text("startup.title")).append(' ').append(version).append('\n')
                .append(LocalDateTime.now().format(STAMP)).append('\n')
                .append(heading.getText()).append('\n')
                .append(failureMessage.getText()).append('\n');
        if (!failureReference.getText().isEmpty()) {
            text.append(failureReference.getText()).append('\n');
        }
        text.append('\n');
        for (StartupLine line : shown == null ? List.<StartupLine>of() : shown.lines()) {
            text.append(line.state() == StartupLine.State.FAILED ? "[x] " : "[ok] ")
                    .append(lineText(line)).append('\n');
        }
        text.append('\n').append(String.join("\n", logTail.lines()));
        ClipboardContent content = new ClipboardContent();
        content.putString(text.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void openLogsFolder() {
        File folder = logTail.file().getParent().toAbsolutePath().toFile();
        Thread opener = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(folder);
                }
            } catch (Exception e) {
                log.warn("Could not open the logs folder {}", folder, e);
            }
        }, "startup-open-logs");
        opener.setDaemon(true);
        opener.start();
    }

    private static String lineText(StartupLine line) {
        return line.arguments().isEmpty()
                ? text(line.messageKey())
                : LanguageManager.getInstance().getString(line.messageKey(), line.arguments().toArray());
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    /** A line of the log: what it is doing, and how long it took once that is worth saying. */
    private static final class LineCell extends ListCell<StartupLine> {

        private final Label message = new Label();
        private final Label elapsed = new Label();
        private final Region spacer = new Region();
        private final HBox row = new HBox(8);

        LineCell() {
            HBox.setHgrow(spacer, Priority.ALWAYS);
            elapsed.getStyleClass().add("startup-elapsed");
            message.setMinWidth(0);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(StartupLine line, boolean empty) {
            super.updateItem(line, empty);
            setText(null);
            if (empty || line == null) {
                setGraphic(null);
                return;
            }
            message.setText(lineText(line));
            message.getStyleClass().removeAll("startup-line-detail", "startup-line-failed");
            if (line.state() == StartupLine.State.FAILED) {
                message.getStyleClass().add("startup-line-failed");
            } else if (line.detail()) {
                message.getStyleClass().add("startup-line-detail");
            }
            long seconds = line.elapsed(Instant.now()).toSeconds();
            elapsed.setText(seconds >= SHOW_ELAPSED_FROM_SECONDS
                    ? LanguageManager.getInstance().getString("startup.seconds", String.valueOf(seconds)) : "");
            row.getChildren().setAll(icon(line), message, spacer, elapsed);
            row.setPadding(new javafx.geometry.Insets(0, 0, 0, line.detail() ? 22 : 0));
            setGraphic(row);
        }

        private static Node icon(StartupLine line) {
            return switch (line.state()) {
                case RUNNING -> {
                    ProgressIndicator spinning = new ProgressIndicator();
                    spinning.setPrefSize(14, 14);
                    spinning.setMaxSize(14, 14);
                    yield spinning;
                }
                case DONE -> styled(AppIcon.CONFIRM.graphic(14), "startup-line-done-icon");
                case FAILED -> styled(AppIcon.CLOSE.graphic(14), "startup-line-failed-icon");
            };
        }

        private static Node styled(Node node, String styleClass) {
            node.getStyleClass().add(styleClass);
            return node;
        }
    }
}
