package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.users.SupportRecoveryChallenge;
import com.hamza.account.features.users.SupportRecoveryResponseCheck;
import com.hamza.account.features.users.SupportRecoveryService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.security.ReleaseSigningKey;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserFacingException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The customer's half of support recovery: three numbered steps in the order the operator
 * works through them - send the request, paste the answer, choose the password.
 *
 * <p>Reached from the login screen's "forgot the administrator's password" link, and still
 * from the {@code --support-recovery} flag. It used to be reachable <i>only</i> from that flag,
 * which is to say from a command line, by the one person in the shop least likely to have
 * one open: the owner who cannot sign in. Showing the link to everyone costs nothing - the
 * protection is the signature, not the link being hard to find.
 *
 * <p>Four things it does that the dialog it replaced did not:
 * <ul>
 *   <li><b>It says it worked.</b> The old dialog closed on success and the process exited,
 *       with no word either way - the operator was left to find out by trying to sign in.
 *       It now names the account, which may no longer be called {@code admin}.</li>
 *   <li><b>It checks the response as it is pasted</b> ({@link SupportRecoveryResponseCheck}):
 *       half a response, a response to an earlier request, text that is not signed at all.
 *       Five refusals lock recovery for fifteen minutes, and an operator pasting the wrong
 *       thing five times was the likeliest way to spend them.</li>
 *   <li><b>It counts down</b> the request's validity, and offers a new request in place
 *       rather than a relaunch when it runs out. The count is from when this window received
 *       the challenge, not from its issue time, so a till whose clock differs from the
 *       database's does not count down wrongly; the database still decides.</li>
 *   <li><b>Nothing touches the database on the JavaFX thread.</b></li>
 * </ul>
 */
public final class SupportRecoveryView {

    private static final String STYLESHEET = "css/support-recovery.css";

    private final SupportRecoveryService service;
    private final LanguageManager language = LanguageManager.getInstance();
    private final Stage stage = new Stage();

    private final TextField requestField = new TextField();
    private final Label remainingLabel = new Label();
    private final Button copyButton = new Button();
    private final Button newRequestButton = new Button();
    private final TextArea responseArea = new TextArea();
    private final Label responseCheckLabel = new Label();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField confirmationField = new PasswordField();
    private final Label passwordCheckLabel = new Label();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button submitButton = new Button();
    private final Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));

    private SupportRecoveryChallenge challenge;
    private Instant deadline;
    private boolean busy;
    private String recoveredUserName;

    private SupportRecoveryView(SupportRecoveryService service, Window owner) {
        this.service = service;
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(language.getString("support.recovery.title"));
        stage.setScene(buildScene());
        stage.setOnHidden(event -> countdown.stop());
        countdown.setCycleCount(Timeline.INDEFINITE);
    }

    /**
     * Opens the window and waits for it to close.
     *
     * @return the administrator's user name when a recovery succeeded, so the login screen can
     * put it in the field; empty when the operator closed the window without recovering.
     */
    public static Optional<String> showAndWait(Window owner, DaoFactory daoFactory) {
        SupportRecoveryView view = new SupportRecoveryView(new SupportRecoveryService(daoFactory), owner);
        view.issueChallenge();
        view.countdown.play();
        view.stage.showAndWait();
        return Optional.ofNullable(view.recoveredUserName);
    }

    private Scene buildScene() {
        Label title = new Label(language.getString("support.recovery.title"));
        title.getStyleClass().add("app-title");
        Label subtitle = new Label(language.getString("support.recovery.subtitle"));
        subtitle.getStyleClass().add("app-subtitle");
        subtitle.setWrapText(true);

        VBox content = new VBox(14, new VBox(4, title, subtitle), requestStep(), responseStep(), passwordStep());
        content.getStyleClass().addAll("app-root", "recovery-root");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("app-scroll-pane");

        VBox root = new VBox(scroll, footer());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getStylesheets().add(Main.class.getResource(STYLESHEET).toExternalForm());

        Scene scene = new Scene(root, 640, 760);
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);
        whenEnterPressed(passwordField, confirmationField, submitButton);
        refresh();
        return scene;
    }

    private Node requestStep() {
        requestField.setEditable(false);
        requestField.getStyleClass().add("recovery-code");
        // Latin text: a right-to-left field would put the bars and the time in reverse order
        // on screen while the clipboard held them the right way round.
        requestField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        requestField.setPromptText(language.getString("support.recovery.issuing"));

        copyButton.setText(language.getString("support.recovery.copy"));
        copyButton.setGraphic(AppIcon.DUPLICATE.graphic());
        copyButton.setOnAction(event -> {
            if (challenge == null) return;
            copyToClipboard(challenge.displayText());
            showStatus(language.getString("support.recovery.copied"), "status-success");
        });
        newRequestButton.setText(language.getString("support.recovery.new.request"));
        newRequestButton.setGraphic(AppIcon.REFRESH.graphic());
        newRequestButton.getStyleClass().add("secondary-button");
        newRequestButton.setOnAction(event -> {
            responseArea.clear();
            issueChallenge();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, copyButton, newRequestButton, spacer, remainingLabel);
        actions.setAlignment(Pos.CENTER_LEFT);
        return step("support.recovery.step.request", "support.recovery.step.request.hint", requestField, actions);
    }

    private Node responseStep() {
        responseArea.setWrapText(true);
        responseArea.setPrefRowCount(5);
        responseArea.getStyleClass().add("recovery-code");
        responseArea.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        responseArea.setPromptText(language.getString("support.recovery.response.hint"));
        responseArea.textProperty().addListener((observable, before, after) -> refresh());

        Button paste = new Button(language.getString("support.recovery.paste"));
        paste.setGraphic(AppIcon.EDIT.graphic());
        paste.getStyleClass().add("secondary-button");
        paste.setOnAction(event -> {
            String text = Clipboard.getSystemClipboard().getString();
            if (text != null) responseArea.setText(text.strip());
            passwordField.requestFocus();
        });
        responseCheckLabel.setWrapText(true);
        HBox actions = new HBox(8, paste, responseCheckLabel);
        actions.setAlignment(Pos.CENTER_LEFT);
        return step("support.recovery.step.response", null, responseArea, actions);
    }

    private Node passwordStep() {
        passwordField.setPromptText(language.getString("support.recovery.password"));
        confirmationField.setPromptText(language.getString("user.password.confirm"));
        passwordField.textProperty().addListener((observable, before, after) -> refresh());
        confirmationField.textProperty().addListener((observable, before, after) -> refresh());
        passwordCheckLabel.setWrapText(true);
        return step("support.recovery.step.password", null,
                labelled("support.recovery.password", passwordField),
                labelled("user.password.confirm", confirmationField),
                passwordCheckLabel);
    }

    private Node footer() {
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        progress.setMaxSize(22, 22);
        progress.setVisible(false);
        progress.setManaged(false);

        submitButton.setText(language.getString("support.recovery.submit"));
        submitButton.setGraphic(AppIcon.SECURITY.graphic());
        submitButton.setDefaultButton(true);
        submitButton.setOnAction(event -> redeem());
        Button close = new Button(language.getString("common.close"));
        close.getStyleClass().add("secondary-button");
        close.setCancelButton(true);
        close.setOnAction(event -> stage.close());

        HBox footer = new HBox(10, progress, statusLabel, submitButton, close);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("recovery-footer");
        return footer;
    }

    private void issueChallenge() {
        challenge = null;
        deadline = null;
        requestField.clear();
        clearStatus();
        run(service::issueChallenge, issued -> {
            challenge = issued;
            deadline = Instant.now().plusSeconds(SupportRecoveryChallenge.VALID_FOR_MINUTES * 60L);
            requestField.setText(issued.displayText());
        });
    }

    private void redeem() {
        SupportRecoveryResponseCheck check = responseCheck();
        if (busy || challenge == null || expired() || !check.submittable() || !passwordsAcceptable()) return;
        String response = responseArea.getText();
        String password = passwordField.getText();
        run(() -> service.redeem(response, password), userName -> {
            recoveredUserName = userName;
            passwordField.clear();
            confirmationField.clear();
            announceSuccess(userName);
            stage.close();
        });
    }

    /** Recomputes every derived label and the submit button from what is on screen now. */
    private void refresh() {
        boolean expired = challenge != null && expired();
        if (challenge == null) {
            remainingLabel.setText("");
        } else if (expired) {
            remainingLabel.setText(language.getString("support.recovery.expired"));
            style(remainingLabel, "status-error");
        } else {
            long seconds = java.time.Duration.between(Instant.now(), deadline).getSeconds();
            remainingLabel.setText(language.getString("support.recovery.remaining",
                    String.format("%02d:%02d", seconds / 60, seconds % 60)));
            style(remainingLabel, seconds < 300 ? "status-warning" : "text-explain");
        }

        SupportRecoveryResponseCheck check = responseCheck();
        responseCheckLabel.setText(language.getString(check.messageKey()));
        style(responseCheckLabel, switch (check) {
            case MATCHES -> "status-success";
            case EMPTY -> "text-explain";
            default -> "status-error";
        });

        String password = passwordField.getText();
        if (password.isEmpty()) {
            passwordCheckLabel.setText(language.getString("support.recovery.password.hint"));
            style(passwordCheckLabel, "text-explain");
        } else if (password.length() < 8) {
            passwordCheckLabel.setText(language.getString("user.password.minimum"));
            style(passwordCheckLabel, "status-error");
        } else if (!password.equals(confirmationField.getText())) {
            passwordCheckLabel.setText(language.getString("support.recovery.error.confirm"));
            style(passwordCheckLabel, "status-error");
        } else {
            passwordCheckLabel.setText("");
        }

        copyButton.setDisable(busy || challenge == null);
        newRequestButton.setDisable(busy);
        submitButton.setDisable(busy || challenge == null || expired || !check.submittable() || !passwordsAcceptable());
    }

    private SupportRecoveryResponseCheck responseCheck() {
        return SupportRecoveryResponseCheck.of(responseArea.getText(), challenge, ReleaseSigningKey::verifies);
    }

    private boolean passwordsAcceptable() {
        String password = passwordField.getText();
        return password.length() >= 8 && password.equals(confirmationField.getText());
    }

    private boolean expired() {
        return deadline != null && !Instant.now().isBefore(deadline);
    }

    private <T> void run(Callable<T> operation, Consumer<T> succeeded) {
        setBusy(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            succeeded.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            if (failure instanceof UserFacingException) {
                // A refusal is the answer to what the operator did, and belongs beside it -
                // not in a dialog that has to be dismissed before the response can be fixed.
                showStatus(failure.getMessage(), "status-error");
            } else {
                showStatus(language.getString("support.recovery.error.unexpected"), "status-error");
                AllAlerts.handleError(language.getString("support.recovery.title"), failure);
            }
        });
        Thread worker = new Thread(task, "support-recovery");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisible(value);
        progress.setManaged(value);
        if (value) clearStatus();
        refresh();
    }

    private void announceSuccess(String userName) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(language.getString("support.recovery.title"));
        alert.setHeaderText(language.getString("support.recovery.success.title"));
        alert.setContentText(language.getString("support.recovery.success", userName));
        ThemeManager.apply(alert.getDialogPane());
        ChangeOrientation.sceneOrientation(alert.getDialogPane().getScene());
        alert.showAndWait();
    }

    private VBox step(String titleKey, String hintKey, Node... body) {
        Label title = new Label(language.getString(titleKey));
        title.getStyleClass().add("recovery-step");
        VBox card = new VBox(8, title);
        if (hintKey != null) {
            Label hint = new Label(language.getString(hintKey));
            hint.setWrapText(true);
            hint.getStyleClass().add("text-explain");
            card.getChildren().add(hint);
        }
        card.getChildren().addAll(body);
        card.getStyleClass().add("app-card");
        return card;
    }

    private VBox labelled(String key, Node field) {
        Label label = new Label(language.getString(key));
        label.getStyleClass().add("form-label");
        return new VBox(4, label, field);
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

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
