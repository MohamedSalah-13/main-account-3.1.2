package com.hamza.account.controller.users;

import com.hamza.account.Main;
import com.hamza.account.config.AppIcon;
import com.hamza.account.features.users.SupportRecoveryChallenge;
import com.hamza.account.features.users.SupportRecoveryRequestAge;
import com.hamza.account.features.users.SupportRecoverySigner;
import com.hamza.account.features.users.SupportRecoverySigningLog;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.concurrent.Task;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * Support's half of recovery, as a screen: the {@code sign-support-recovery.sh} script for a
 * support desk that has no bash, no openssl, and a customer waiting on the telephone.
 *
 * <p>It lives beside the product-profile tab in {@code AccountK-Product-Setup} because that
 * is already the tool that signs with the release private key: the technician picks the key
 * file for the operation and nothing keeps it. Shipping this in every installation gives a
 * customer nothing, for the same reason shipping the profile signer does not - without the
 * private key it signs nothing.
 *
 * <p>What the script never asked, this screen does, and it is the part that matters: <b>who
 * is this for, and did you check?</b> A request can be produced by anyone who can reach the
 * customer's login screen, so the signature is only as good as the call it answers. Signing is
 * refused until a customer is named and the check is ticked, and both go into
 * {@link SupportRecoverySigningLog}. The age of the request is shown against this machine's
 * clock ({@link SupportRecoveryRequestAge}), because signing one kept from an earlier call is
 * the habit that turns a thirty-minute window into no window.
 */
public final class SupportRecoverySignerPane {

    private static final String STYLESHEET = "css/support-recovery.css";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LanguageManager language = LanguageManager.getInstance();
    private final SupportRecoverySigner signer = SupportRecoverySigner.release();
    private final SupportRecoverySigningLog log = SupportRecoverySigningLog.forCurrentUser();

    private final Label keyPathLabel = new Label();
    private final TextField requestField = new TextField();
    private final Label machineValue = new Label();
    private final Label nonceValue = new Label();
    private final Label issuedValue = new Label();
    private final Label requestCheckLabel = new Label();
    private final TextField customerField = new TextField();
    private final CheckBox verifiedBox = new CheckBox();
    private final Button signButton = new Button();
    private final TextArea responseArea = new TextArea();
    private final Button copyButton = new Button();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final VBox root;

    private Path privateKeyFile;
    private SupportRecoveryChallenge request;
    private boolean busy;

    public SupportRecoverySignerPane() {
        Label heading = new Label(language.getString("support.recovery.signer.heading"));
        heading.getStyleClass().add("app-title");
        Label description = new Label(language.getString("support.recovery.signer.description"));
        description.getStyleClass().add("app-subtitle");
        description.setWrapText(true);

        VBox content = new VBox(14, new VBox(6, heading, description),
                keyCard(), requestCard(), identityCard(), responseCard());
        content.getStyleClass().addAll("app-root", "recovery-root");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("app-scroll-pane");

        // The sign button and its answer sit below the scrolled cards, always on screen: in the
        // first version they were the last card, under the fold of a 760-point window, so the
        // one control the tab exists for was the one nobody could see.
        root = new VBox(scroll, footer());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getStylesheets().add(Main.class.getResource(STYLESHEET).toExternalForm());

        whenEnterPressed(requestField, customerField, verifiedBox, signButton);
        refresh();
    }

    public Parent root() {
        return root;
    }

    private Node keyCard() {
        Button choose = new Button(language.getString("support.recovery.signer.key.choose"));
        choose.setGraphic(AppIcon.SECURITY.graphic());
        choose.getStyleClass().add("secondary-button");
        choose.setOnAction(event -> chooseKey());
        keyPathLabel.setText(language.getString("support.recovery.signer.key.none"));
        keyPathLabel.getStyleClass().add("text-explain");
        keyPathLabel.setWrapText(true);
        HBox row = new HBox(10, choose, keyPathLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return card("support.recovery.signer.key", row);
    }

    private Node requestCard() {
        requestField.setPromptText(language.getString("support.recovery.signer.request.prompt"));
        requestField.getStyleClass().add("recovery-code");
        requestField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        requestField.textProperty().addListener((observable, before, after) -> {
            request = SupportRecoveryChallenge.parseDisplayText(after).orElse(null);
            responseArea.clear();
            refresh();
        });
        HBox.setHgrow(requestField, Priority.ALWAYS);
        Button paste = new Button(language.getString("support.recovery.paste"));
        paste.setGraphic(AppIcon.EDIT.graphic());
        paste.getStyleClass().add("secondary-button");
        paste.setOnAction(event -> {
            String text = Clipboard.getSystemClipboard().getString();
            if (text != null) requestField.setText(text.strip());
            customerField.requestFocus();
        });
        HBox input = new HBox(8, requestField, paste);
        input.setAlignment(Pos.CENTER_LEFT);

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(6);
        fields.getStyleClass().add("recovery-fields");
        fields.addRow(0, caption("support.recovery.signer.machine"), code(machineValue));
        fields.addRow(1, caption("support.recovery.signer.nonce"), code(nonceValue));
        fields.addRow(2, caption("support.recovery.signer.issued"), code(issuedValue));
        requestCheckLabel.setWrapText(true);
        return card("support.recovery.signer.request", input, fields, requestCheckLabel);
    }

    private Node identityCard() {
        customerField.setPromptText(language.getString("support.recovery.signer.customer.prompt"));
        customerField.textProperty().addListener((observable, before, after) -> refresh());
        verifiedBox.setText(language.getString("support.recovery.signer.verified"));
        verifiedBox.setWrapText(true);
        verifiedBox.selectedProperty().addListener((observable, before, after) -> refresh());
        Label hint = new Label(language.getString("support.recovery.signer.verified.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("status-warning");
        return card("support.recovery.signer.identity",
                labelled("support.recovery.signer.customer", customerField), verifiedBox, hint);
    }

    private Node responseCard() {
        responseArea.setEditable(false);
        responseArea.setWrapText(true);
        responseArea.setPrefRowCount(5);
        responseArea.getStyleClass().add("recovery-code");
        responseArea.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        responseArea.textProperty().addListener((observable, before, after) -> refresh());
        copyButton.setText(language.getString("support.recovery.signer.copy"));
        copyButton.setGraphic(AppIcon.DUPLICATE.graphic());
        copyButton.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(responseArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
            showStatus(language.getString("support.recovery.signer.copied"), "status-success");
        });
        return card("support.recovery.signer.response", responseArea, new HBox(copyButton));
    }

    private Node footer() {
        signButton.setText(language.getString("support.recovery.signer.sign"));
        signButton.setGraphic(AppIcon.CONFIRM.graphic());
        signButton.setOnAction(event -> sign());
        progress.setMaxSize(22, 22);
        progress.setVisible(false);
        progress.setManaged(false);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        HBox footer = new HBox(10, signButton, progress, statusLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("recovery-footer");
        return footer;
    }

    private void chooseKey() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(language.getString("support.recovery.signer.key.chooser"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                language.getString("support.recovery.signer.key.file.type"), "*.pem"));
        File chosen = chooser.showOpenDialog(root.getScene().getWindow());
        if (chosen == null) return;
        privateKeyFile = chosen.toPath();
        keyPathLabel.setText(privateKeyFile.toAbsolutePath().toString());
        clearStatus();
        refresh();
    }

    private void sign() {
        if (busy || !readyToSign()) return;
        SupportRecoveryChallenge signing = request;
        String customer = customerField.getText().strip();
        Path key = privateKeyFile;
        setBusy(true);
        Task<Signed> task = new Task<>() {
            @Override
            protected Signed call() throws Exception {
                String response = signer.sign(signing, key);
                // Signed first, logged second: a log that cannot be written must not cost the
                // customer the response, but it must not pass unnoticed either.
                boolean logged;
                try {
                    log.append(LocalDateTime.now(), customer, signing);
                    logged = true;
                } catch (Exception unwritable) {
                    logged = false;
                }
                return new Signed(response, logged);
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            Signed signed = task.getValue();
            responseArea.setText(signed.response());
            String path = log.file().toAbsolutePath().toString();
            if (signed.logged()) {
                showStatus(language.getString("support.recovery.signer.signed", path), "status-success");
            } else {
                showStatus(language.getString("support.recovery.signer.log.failed", path), "status-warning");
            }
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            if (failure instanceof SupportRecoverySigner.SigningException refused) {
                showStatus(language.getString(refused.messageKey()), "status-error");
            } else {
                showStatus(language.getString("support.recovery.error.unexpected"), "status-error");
                AllAlerts.handleError(language.getString("support.recovery.signer.heading"), failure);
            }
        });
        Thread worker = new Thread(task, "support-recovery-signing");
        worker.setDaemon(true);
        worker.start();
    }

    private void refresh() {
        if (request == null) {
            machineValue.setText("");
            nonceValue.setText("");
            issuedValue.setText("");
            boolean typed = !requestField.getText().isBlank();
            requestCheckLabel.setText(typed ? language.getString("support.recovery.signer.request.invalid") : "");
            style(requestCheckLabel, "status-error");
        } else {
            machineValue.setText(request.machineId());
            nonceValue.setText(request.nonce());
            issuedValue.setText(STAMP.format(request.issuedAt()));
            LocalDateTime now = LocalDateTime.now();
            SupportRecoveryRequestAge age = SupportRecoveryRequestAge.of(request.issuedAt(), now);
            long minutes = Math.abs(SupportRecoveryRequestAge.minutesSince(request.issuedAt(), now));
            requestCheckLabel.setText(language.getString(age.messageKey(), minutes,
                    SupportRecoveryChallenge.VALID_FOR_MINUTES));
            style(requestCheckLabel, age.warns() ? "status-warning" : "status-success");
        }
        signButton.setDisable(busy || !readyToSign());
        copyButton.setDisable(responseArea.getText().isBlank());
    }

    private boolean readyToSign() {
        return privateKeyFile != null && request != null
                && !customerField.getText().isBlank() && verifiedBox.isSelected();
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisible(value);
        progress.setManaged(value);
        if (value) clearStatus();
        refresh();
    }

    private VBox card(String titleKey, Node... body) {
        Label title = new Label(language.getString(titleKey));
        title.getStyleClass().add("recovery-step");
        VBox card = new VBox(8, title);
        card.getChildren().addAll(body);
        card.getStyleClass().add("app-card");
        return card;
    }

    private VBox labelled(String key, Node field) {
        return new VBox(4, caption(key), field);
    }

    private Label caption(String key) {
        Label label = new Label(language.getString(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label code(Label value) {
        value.getStyleClass().add("recovery-code");
        value.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        return value;
    }

    private void showStatus(String text, String styleClass) {
        statusLabel.setText(text);
        style(statusLabel, styleClass);
    }

    private void clearStatus() {
        statusLabel.setText("");
    }

    private static void style(Label label, String styleClass) {
        label.getStyleClass().removeAll("status-success", "status-warning", "status-error", "text-explain");
        label.getStyleClass().add(styleClass);
    }

    private record Signed(String response, boolean logged) {
    }
}
