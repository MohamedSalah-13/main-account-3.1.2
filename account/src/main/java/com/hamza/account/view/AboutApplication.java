package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.MachineId;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.DialogButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.about.AboutBuild;
import com.hamza.account.features.about.AboutLicense;
import com.hamza.account.features.license.online.ActivationResult;
import com.hamza.account.features.license.online.PurchaseCode;
import com.hamza.account.features.license.online.ServerRefusal;
import com.hamza.account.features.license.online.ServerReply;
import com.hamza.account.features.productprofile.ProductFeatureCatalog;
import com.hamza.account.features.productprofile.ProductProfile;
import com.hamza.account.service.version.SystemInfoDialog;
import com.hamza.account.trial.OnlineLicensing;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * The About window: which build this is, what the licence amounts to, which edition this shop has.
 * <p>
 * It used to be a column 347 points wide and 680 tall - a stack of centred sentences in fonts the
 * program does not ship ({@code Grand Hotel}, {@code Gafata}), coloured green, orange and red by
 * name, so the dark theme could not answer for them - with each figure inside an Arabic sentence
 * that moved its parts: the version's colon landed after the number, the build's timestamp was
 * Maven's {@code 2026-09-24T04:46:00Z}. The version it showed was the one this computer last ran,
 * a preference answering {@code 1.0.0} until it is first written.
 * <p>
 * Now it is one wide, short window: a header with the product and its build, two cards side by
 * side - the licence and the edition - and a footer. Every line is a caption and a value of its
 * own, and a value in Latin is laid out left to right.
 * <p>
 * <b>Activating a licence checks the file before it replaces anything</b> -
 * {@link TrialManager#install}: the start-up reads {@code license.dat} strictly, and a wrong file
 * copied over a working licence used to end the install at the next start. That holds for both ways
 * in: a file somebody chose, and one the licence server sent for a purchase code.
 */
@Log4j2
public class AboutApplication extends Application {

    private static final double CARD_WIDTH = 300;
    /** What {@code TrialManager.install} accepts; anything larger is refused before it is read. */
    private static final long LICENSE_FILE_MAX_BYTES = 64 * 1024;

    private final LanguageManager language = LanguageManager.getInstance();
    private final AboutBuild build = AboutBuild.current();

    private final Label status = new Label();
    private final Label remaining = new Label();
    private final Label licenseFile = new Label();
    private final Label machineCode = new Label();
    private final Button copyMachineCode = new Button();
    private Optional<Image> programImage;

    /** What the licence card shows, read together off the JavaFX thread. */
    private record Loaded(TrialManager.TrialDisplayInfo info, Optional<String> machine) {
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        VBox body = new VBox(12, header(), cards(), footer(stage));
        body.setPadding(new Insets(16));
        body.getStyleClass().add("app-container");

        StackPane root = new StackPane(body);
        root.getStyleClass().addAll("app-root", "about-screen");

        Scene scene = new SceneAll(root);
        stage.setScene(scene);
        stage.setTitle(text("nav.about"));
        programImage().ifPresent(stage.getIcons()::add);
        stage.setResizable(false);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();

        refreshStatus();
    }

    // ---- the header -----------------------------------------------------------------------------

    private HBox header() {
        Label product = new Label(text("startup.app.name"));
        product.getStyleClass().add("about-product-name");

        String development = text("about.build.development");
        HBox facts = new HBox(18,
                fact("about.caption.version", build.version().orElse(development), true),
                fact("about.caption.build", build.built().map(this::longDate).orElse(development), false));
        facts.setAlignment(Pos.CENTER_LEFT);

        VBox titles = new VBox(4, product, facts);
        titles.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titles, Priority.ALWAYS);

        HBox header = new HBox(14, logo(), titles);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("about-header");
        return header;
    }

    /** A caption and its value on one line, the value its own label so the paragraph cannot move it. */
    private HBox fact(String captionKey, String value, boolean latin) {
        Label caption = new Label(text(captionKey));
        caption.getStyleClass().add("about-header-caption");
        Label shown = new Label(value);
        shown.getStyleClass().add("about-header-value");
        if (latin) {
            shown.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        }
        HBox fact = new HBox(6, caption, shown);
        fact.setAlignment(Pos.BASELINE_LEFT);
        return fact;
    }

    private Node logo() {
        return programImage().<Node>map(image -> {
            ImageView view = new ImageView(image);
            view.setFitWidth(52);
            view.setFitHeight(52);
            view.setPreserveRatio(true);
            StackPane box = new StackPane(view);
            box.getStyleClass().add("about-logo");
            return box;
        }).orElseGet(() -> AppIcon.INFO.graphic(44));
    }

    /**
     * The program's own picture, read once and the stream closed. Not {@code new Image_Setting()},
     * which opens every one of its forty-odd resource streams to hand out one and closes none.
     */
    private Optional<Image> programImage() {
        if (programImage == null) {
            try (InputStream icon = Main.class.getResourceAsStream("image/tools.png")) {
                programImage = icon == null ? Optional.empty() : Optional.of(new Image(icon));
            } catch (Exception e) {
                log.debug("No picture for the About window", e);
                programImage = Optional.empty();
            }
        }
        return programImage;
    }

    // ---- the two cards --------------------------------------------------------------------------

    private HBox cards() {
        VBox licence = licenceCard();
        VBox profile = profileCard();
        HBox cards = new HBox(12, licence, profile);
        HBox.setHgrow(licence, Priority.ALWAYS);
        HBox.setHgrow(profile, Priority.ALWAYS);
        return cards;
    }

    private VBox licenceCard() {
        Label heading = new Label(text("about.license.heading"));
        heading.getStyleClass().add("app-section-title");
        status.getStyleClass().add("about-badge");
        HBox top = new HBox(10, heading, spacer(), status);
        top.setAlignment(Pos.CENTER_LEFT);

        GridPane rows = rows();
        addRow(rows, 0, "about.caption.remaining", remaining);
        addRow(rows, 1, "about.caption.license", licenseFile);
        addRow(rows, 2, "about.caption.machine", machineCodeRow());

        Button byCode = new Button(text("about.license.activate.code"), AppIcon.SECURITY.graphic());
        byCode.getStyleClass().add("app-primary-button");
        Button byFile = new Button(text("about.license.activate"), AppIcon.SECURITY.graphic());
        byFile.getStyleClass().add("app-neutral-button");
        byCode.setOnAction(event -> activateByCode(byCode, byFile));
        byFile.setOnAction(event -> activate(byFile));
        HBox actions = new HBox(8, byCode, byFile);
        actions.setAlignment(Pos.CENTER_LEFT);

        return card(top, rows, actions);
    }

    private VBox profileCard() {
        Label heading = new Label(text("product.profile.about.heading"));
        heading.getStyleClass().add("app-section-title");
        GridPane rows = rows();

        ProductProfile profile = ServiceRegistry.get(ProductProfile.class);
        if (profile == null) {
            Label unavailable = new Label(text("product.profile.about.unavailable"));
            unavailable.getStyleClass().add("form-hint");
            unavailable.setWrapText(true);
            return card(heading, unavailable);
        }
        boolean legacy = profile.legacyFallback();
        addRow(rows, 0, "product.profile.about.customer",
                value(legacy ? text("product.profile.about.legacy.customer") : profile.customerName()));
        addRow(rows, 1, "product.profile.about.edition",
                value(legacy ? text("product.profile.about.legacy.edition") : profile.profileName()));
        addRow(rows, 2, "product.profile.about.features", value(language.getString(
                "product.profile.about.features.value",
                profile.enabledFeatures().size(), ProductFeatureCatalog.standard().keys().size())));
        if (!legacy) {
            addRow(rows, 3, "product.profile.about.issued",
                    value(longDate(profile.issuedAt().atZone(ZoneId.systemDefault()).toLocalDate())));
        }
        return card(heading, rows);
    }

    private VBox card(Node... children) {
        VBox card = new VBox(10, children);
        card.getStyleClass().add("app-card");
        card.setPadding(new Insets(12, 14, 14, 14));
        card.setPrefWidth(CARD_WIDTH);
        return card;
    }

    private static GridPane rows() {
        GridPane rows = new GridPane();
        rows.setHgap(12);
        rows.setVgap(8);
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        rows.getColumnConstraints().addAll(captions, values);
        return rows;
    }

    private void addRow(GridPane rows, int row, String captionKey, Label value) {
        value.getStyleClass().add("about-value");
        addRow(rows, row, captionKey, (Node) value);
    }

    private void addRow(GridPane rows, int row, String captionKey, Node value) {
        Label caption = new Label(text(captionKey));
        caption.getStyleClass().add("form-label");
        rows.add(caption, 0, row);
        rows.add(value, 1, row);
    }

    /**
     * This computer's {@code MachineGuid} - what a licence is issued for. A shop without internet
     * reads it to support over the telephone, and support types it into the licence server's
     * "issue a file for a machine" form; the licence then licenses this computer and no other.
     * It is written left to right and copied whole, because one wrong character is another machine.
     * <p>
     * <b>It is never cut short</b>: the card is 300 points and the code 36 characters, and the first
     * draft drew {@code c373b698-a8fb-483c-9...} beside a button reading "..." - half a code to read
     * down a telephone. The label keeps its whole width and the window grows for it, and the button
     * is an icon whose word is its tooltip.
     */
    private HBox machineCodeRow() {
        machineCode.getStyleClass().add("about-machine-code");
        machineCode.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        machineCode.setMinWidth(Region.USE_PREF_SIZE);
        copyMachineCode.setGraphic(AppIcon.DUPLICATE.graphic());
        copyMachineCode.setTooltip(new Tooltip(text("about.machine.copy")));
        copyMachineCode.setMinWidth(Region.USE_PREF_SIZE);
        copyMachineCode.getStyleClass().add("app-neutral-button");
        copyMachineCode.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(machineCode.getText());
            Clipboard.getSystemClipboard().setContent(content);
            copyMachineCode.setGraphic(AppIcon.CONFIRM.graphic());
            copyMachineCode.getTooltip().setText(text("about.machine.copied"));
        });
        HBox row = new HBox(8, machineCode, copyMachineCode);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label value(String text) {
        Label value = new Label(text);
        value.setWrapText(true);
        return value;
    }

    // ---- the footer -----------------------------------------------------------------------------

    private HBox footer(Stage stage) {
        VBox credits = new VBox(2,
                credit("about.caption.developer",
                        language.getString("about.copyright.line", build.copyrightYear(LocalDate.now()))),
                credit("about.caption.support",
                        Setting_Language.PROGRAM_NAME_EN + "  ·  " + Setting_Language.PROGRAM_TEL));
        HBox.setHgrow(credits, Priority.ALWAYS);

        Button systemInfo = new Button(text("about.system.info"), AppIcon.INFO.graphic());
        systemInfo.getStyleClass().add("app-neutral-button");
        systemInfo.setOnAction(event -> new SystemInfoDialog().show());
        Button close = new Button(text("close"), AppIcon.CLOSE.graphic());
        close.getStyleClass().add("app-neutral-button");
        close.setCancelButton(true);
        close.setOnAction(event -> stage.close());

        HBox footer = new HBox(10, credits, systemInfo, close);
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }

    /** The developer and the support line: Latin text, laid out left to right beside an Arabic caption. */
    private HBox credit(String captionKey, String latin) {
        Label caption = new Label(text(captionKey));
        caption.getStyleClass().add("about-credit-caption");
        Label value = new Label(latin);
        value.getStyleClass().add("about-credit");
        value.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        HBox line = new HBox(6, caption, value);
        line.setAlignment(Pos.BASELINE_LEFT);
        return line;
    }

    // ---- the licence ----------------------------------------------------------------------------

    /**
     * Reads the trial state off the JavaFX thread, on a pooled connection it gives straight back,
     * and the machine's code with it: {@code MachineId} answers from what the start-up read, and
     * on a miss starts a {@code reg} process, which is not the JavaFX thread's to wait for. The code
     * needs no database, so a failure to read the trial state does not take it away.
     */
    private void refreshStatus() {
        show(null, true);
        Task<Loaded> read = new Task<>() {
            @Override
            protected Loaded call() {
                Optional<String> machine = MachineId.current();
                TrialManager.TrialDisplayInfo info = null;
                try {
                    info = withTrialManager(TrialManager::getDisplayInfo);
                } catch (RuntimeException unread) {
                    log.warn("The licence state was not read for the About window", unread);
                }
                return new Loaded(info, machine);
            }
        };
        read.setOnSucceeded(event -> show(read.getValue(), false));
        read.setOnFailed(event -> show(null, false));
        Thread thread = new Thread(read, "about-licence");
        thread.setDaemon(true);
        thread.start();
    }

    private void show(Loaded loaded, boolean loading) {
        if (loading) {
            status.setText("…");
            remaining.setText("…");
            licenseFile.setText("…");
            machineCode.setText("…");
            copyMachineCode.setGraphic(AppIcon.DUPLICATE.graphic());
            copyMachineCode.getTooltip().setText(text("about.machine.copy"));
            copyMachineCode.setDisable(true);
            return;
        }
        Optional<String> machine = loaded == null ? Optional.empty() : loaded.machine();
        machineCode.setText(machine.orElse(text("about.machine.unavailable")));
        copyMachineCode.setDisable(machine.isEmpty());

        AboutLicense license = AboutLicense.of(loaded == null ? null : loaded.info());
        status.setText(text(license.statusKey()));
        remaining.setText(license.remainingDays() == null
                ? text(license.remainingKey())
                : language.getString(license.remainingKey(), license.remainingDays()));
        licenseFile.setText(text(license.fileKey()));
        status.getStyleClass().removeAll("about-badge-good", "about-badge-warning", "about-badge-bad");
        status.getStyleClass().add(switch (license.tone()) {
            case GOOD -> "about-badge-good";
            case WARNING -> "about-badge-warning";
            case BAD -> "about-badge-bad";
        });
    }

    private void activate(Button activate) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("about.license.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(text("about.license.file.type"), "*.dat"));
        File file = chooser.showOpenDialog(activate.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            // Judged by its size before a byte of it is read: a licence is a line of text, and a
            // renamed file of gigabytes read whole would freeze this window or exhaust the heap.
            if (file.length() > LICENSE_FILE_MAX_BYTES) {
                log.warn("The licence file {} was not installed: {} bytes", file, file.length());
                throw new UserValidationException(text("about.license.refused"));
            }
            byte[] chosen = Files.readAllBytes(file.toPath());
            Optional<String> refused = withTrialManager(trial -> {
                try {
                    return trial.install(chosen);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            if (refused.isPresent()) {
                log.warn("The licence file {} was not installed: {}", file, refused.get());
                throw new UserValidationException(text("about.license.refused"));
            }
            AllAlerts.alertSaveWithMessage(text("about.license.activated"));
            refreshStatus();
        } catch (Exception e) {
            AllAlerts.handleError(text("about.license.activate"), e);
        }
    }

    /**
     * Activation with the purchase code the vendor sent ({@code OnlineLicensing}). The code is checked on
     * this computer before the dialog closes - one wrong character is said at once, and never costs one of
     * the server's ten attempts an hour - then sent once, off the JavaFX thread. The file that comes back is
     * installed only after {@link TrialManager#install} has judged it, so no answer can replace a working
     * licence with one this build does not accept.
     */
    private void activateByCode(Button byCode, Button byFile) {
        Optional<String> code = askForCode(byCode);
        if (code.isEmpty()) {
            return;
        }
        byCode.setDisable(true);
        byFile.setDisable(true);
        Task<ActivationResult> request = new Task<>() {
            @Override
            protected ActivationResult call() {
                return OnlineLicensing.activation().activate(code.get());
            }
        };
        request.setOnSucceeded(event -> {
            byCode.setDisable(false);
            byFile.setDisable(false);
            showActivation(request.getValue());
        });
        request.setOnFailed(event -> {
            byCode.setDisable(false);
            byFile.setDisable(false);
            AllAlerts.handleError(text("about.license.activate.code"), request.getException());
        });
        Thread thread = new Thread(request, "about-activate-by-code");
        thread.setDaemon(true);
        thread.start();
    }

    /** The code, typed or pasted - refused in place while it fails its own check. */
    private Optional<String> askForCode(Button owner) {
        TextField field = new TextField();
        field.setPromptText("AK-XXXX-XXXX-XXXX-XXXX");
        field.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        field.setPrefColumnCount(26);
        field.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().length() <= 40 ? change : null));
        Label prompt = new Label(text("about.license.code.prompt"));
        prompt.getStyleClass().add("form-label");
        Label problem = new Label();
        problem.getStyleClass().add("form-error");
        problem.setWrapText(true);
        problem.managedProperty().bind(problem.visibleProperty());
        problem.setVisible(false);
        field.textProperty().addListener((observable, before, after) -> problem.setVisible(false));

        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner.getScene().getWindow());
        dialog.setTitle(text("about.license.activate.code"));
        dialog.setHeaderText(text("about.license.code.header"));
        dialog.getDialogPane().setContent(new VBox(8, prompt, field, problem));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogButtons.changeNameAndGraphic(dialog.getDialogPane());
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(Bindings.createBooleanBinding(
                () -> field.getText() == null || field.getText().isBlank(), field.textProperty()));
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            if (PurchaseCode.normalise(field.getText()).isEmpty()) {
                problem.setText(text("license.online.code.invalid"));
                problem.setVisible(true);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? field.getText() : null);
        ChangeOrientation.sceneOrientation(dialog.getDialogPane().getScene());
        ThemeManager.apply(dialog.getDialogPane().getScene());
        Platform.runLater(field::requestFocus);
        return dialog.showAndWait().map(String::strip).filter(typed -> !typed.isEmpty());
    }

    private void showActivation(ActivationResult result) {
        if (result.kind() == ActivationResult.Kind.ACTIVATED) {
            ServerReply.Activation licence = result.activation();
            AllAlerts.alertSaveWithMessage(licence == null
                    ? text("about.license.activated")
                    : language.getString("license.online.activated",
                    licence.customerName() == null ? "-" : licence.customerName(),
                    licence.seatsUsed(), licence.seatsTotal(),
                    licence.updatesUntil() == null ? "-" : longDate(licence.updatesUntil())));
            refreshStatus();
            return;
        }
        ServerReply.Refused refused = result.refused();
        String message;
        if (refused != null && refused.refusal() == ServerRefusal.SEATS_FULL) {
            message = language.getString(result.messageKey(),
                    refused.seatsUsed() == null ? 0 : refused.seatsUsed(),
                    refused.seatsTotal() == null ? 0 : refused.seatsTotal());
        } else if (refused != null && refused.refusal() == ServerRefusal.SERVER_ERROR) {
            message = language.getString(result.messageKey(), refused.reference() == null ? "-" : refused.reference());
        } else {
            message = text(result.messageKey());
        }
        AllAlerts.handleError(text("about.license.activate.code"), new UserValidationException(message));
    }

    /**
     * A TrialManager on a pooled connection, returned as soon as the work is done. Keeping one - and
     * with it an open connection - for the life of this window leaked a pooled connection each time
     * the window was opened.
     */
    private static <T> T withTrialManager(java.util.function.Function<TrialManager, T> work) {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.apply(new TrialManager(connection));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    // ---- small things ---------------------------------------------------------------------------

    /** "24 سبتمبر 2026": a date after an Arabic word is never written 2026-09-24, which the paragraph reverses. */
    private String longDate(LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", language.getCurrentLocale()).format(day);
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private String text(String key) {
        return language.getString(key);
    }
}
