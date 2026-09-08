package com.hamza.account.controller.users;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.users.PasswordChangeException;
import com.hamza.account.features.users.PasswordChangeForm;
import com.hamza.account.features.users.PasswordChangeService;
import com.hamza.account.features.users.PasswordStrength;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.geometry.NodeOrientation;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.Objects;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/** Thin JavaFX boundary for the account-owned password-change feature. */
public final class ChangePassController {

    private static final List<String> STRENGTH_STYLES = List.of(
            "strength-empty", "strength-weak", "strength-fair", "strength-good", "strength-strong");

    private final PasswordChangeService service;
    private final UserSessionContext session;
    private final int userId;
    private final String username;
    private final boolean forced;

    private Dialog<Boolean> dialog;
    private boolean busy;

    @FXML private Pane root;
    @FXML private StackPane headerIcon;
    @FXML private Label titleLabel, subtitleLabel, accountLabel, requiredBanner;
    @FXML private Label minimumStateLabel, confirmationStateLabel, strengthLabel, statusLabel;
    @FXML private PasswordField currentPassword, newPassword, confirmationPassword;
    @FXML private TextField currentPasswordVisible, newPasswordVisible, confirmationPasswordVisible;
    @FXML private ProgressBar strengthProgress;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private CheckBox showPasswords;
    @FXML private Button saveButton, cancelButton;
    @FXML private HBox actionsBar;

    public ChangePassController(PasswordChangeService service, UserSessionContext session,
                                int userId, String username, boolean forced) {
        this.service = Objects.requireNonNull(service, "service");
        this.session = Objects.requireNonNull(session, "session");
        this.userId = userId;
        this.username = username == null ? "" : username;
        this.forced = forced;
    }

    @FXML
    public void initialize() {
        configureCopy();
        configureIcons();
        configurePasswordVisibility();
        configureNavigation();
        configureActions();

        currentPassword.textProperty().addListener((observable, oldValue, newValue) -> refreshFormState());
        newPassword.textProperty().addListener((observable, oldValue, newValue) -> refreshFormState());
        confirmationPassword.textProperty().addListener((observable, oldValue, newValue) -> refreshFormState());
        refreshFormState();
    }

    public void attach(Dialog<Boolean> dialog) {
        this.dialog = Objects.requireNonNull(dialog, "dialog");
    }

    public void requestInitialFocus() {
        Platform.runLater(this::focusCurrentPassword);
    }

    public boolean isBusy() {
        return busy;
    }

    public void dispose() {
        clearSensitiveInput();
    }

    private void configureCopy() {
        titleLabel.setText(text(forced ? "password.change.required.title" : "password.change.title"));
        subtitleLabel.setText(text(forced
                ? "password.change.required.subtitle" : "password.change.subtitle"));
        accountLabel.setText(text("password.change.account", username));
        requiredBanner.setText(text("password.change.required.banner"));
        requiredBanner.setVisible(forced);
        requiredBanner.setManaged(forced);
    }

    private void configureIcons() {
        headerIcon.getChildren().setAll(AppIcon.SECURITY.graphic(30));
        saveButton.setGraphic(AppIcon.SAVE.graphic());
        cancelButton.setGraphic(AppIcon.CLOSE.graphic());
        showPasswords.setGraphic(AppIcon.SHOW.graphic());
        minimumStateLabel.setGraphic(AppIcon.WARNING.graphic());
        confirmationStateLabel.setGraphic(AppIcon.WARNING.graphic());
    }

    private void configurePasswordVisibility() {
        bindPasswordPair(currentPassword, currentPasswordVisible);
        bindPasswordPair(newPassword, newPasswordVisible);
        bindPasswordPair(confirmationPassword, confirmationPasswordVisible);

        showPasswords.selectedProperty().addListener((observable, wasShowing, isShowing) -> {
            TextInputControl target = focusedCounterpart(isShowing);
            showPasswords.setText(text(isShowing
                    ? "password.change.hide.passwords" : "password.change.show.passwords"));
            showPasswords.setGraphic((isShowing ? AppIcon.HIDE : AppIcon.SHOW).graphic());
            if (target != null) Platform.runLater(target::requestFocus);
        });
    }

    private void bindPasswordPair(PasswordField masked, TextField visible) {
        visible.textProperty().bindBidirectional(masked.textProperty());
        visible.visibleProperty().bind(showPasswords.selectedProperty());
        visible.managedProperty().bind(visible.visibleProperty());
        masked.visibleProperty().bind(showPasswords.selectedProperty().not());
        masked.managedProperty().bind(masked.visibleProperty());
    }

    private TextInputControl focusedCounterpart(boolean showing) {
        if (currentPassword.isFocused() || currentPasswordVisible.isFocused()) {
            return showing ? currentPasswordVisible : currentPassword;
        }
        if (newPassword.isFocused() || newPasswordVisible.isFocused()) {
            return showing ? newPasswordVisible : newPassword;
        }
        if (confirmationPassword.isFocused() || confirmationPasswordVisible.isFocused()) {
            return showing ? confirmationPasswordVisible : confirmationPassword;
        }
        return null;
    }

    private void configureNavigation() {
        whenEnterPressed(currentPassword, newPassword, confirmationPassword, saveButton);
        whenEnterPressed(currentPasswordVisible, newPasswordVisible, confirmationPasswordVisible, saveButton);
    }

    private void configureActions() {
        // Keep the primary action at the physical bottom-right in both locales.
        // The buttons themselves retain the active language direction.
        actionsBar.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        NodeOrientation languageDirection = LanguageManager.getInstance().getNodeOrientation();
        saveButton.setNodeOrientation(languageDirection);
        cancelButton.setNodeOrientation(languageDirection);
        saveButton.setOnAction(event -> submit());
        cancelButton.setOnAction(event -> close(false));
    }

    private void refreshFormState() {
        PasswordChangeForm form = form();
        PasswordStrength strength = form.strength();
        strengthProgress.setProgress(strength.progress());
        STRENGTH_STYLES.forEach(style -> strengthProgress.getStyleClass().remove(style));
        strengthProgress.getStyleClass().add(strength.styleClass());
        strengthLabel.setText(text("password.change.strength", text(strength.messageKey())));

        setRuleState(minimumStateLabel, form.hasMinimumLength(),
                form.hasMinimumLength() ? "password.change.minimum.met" : "password.change.minimum.pending");
        setRuleState(confirmationStateLabel, form.confirmationMatches(),
                form.confirmation().isEmpty() ? "password.change.match.pending"
                        : form.confirmationMatches() ? "password.change.match.met" : "password.change.match.failed");

        saveButton.setDisable(busy || !form.isReady());
        clearFieldErrors();
        if (!busy) clearStatus();
    }

    private void setRuleState(Label label, boolean met, String messageKey) {
        label.setText(text(messageKey));
        label.getStyleClass().removeAll("password-rule-met", "password-rule-pending");
        label.getStyleClass().add(met ? "password-rule-met" : "password-rule-pending");
        label.setGraphic((met ? AppIcon.CONFIRM : AppIcon.WARNING).graphic());
    }

    private PasswordChangeForm form() {
        return new PasswordChangeForm(currentPassword.getText(), newPassword.getText(),
                confirmationPassword.getText());
    }

    private void submit() {
        if (busy) return;
        PasswordChangeForm form = form();
        if (form.firstErrorKey().isPresent()) {
            showValidation(form.firstErrorKey().get());
            return;
        }

        setBusy(true);
        showStatus("password.change.status.saving", false);
        Task<com.hamza.account.model.domain.Users> task = new Task<>() {
            @Override
            protected com.hamza.account.model.domain.Users call() throws Exception {
                return service.changeOwnPassword(userId, form);
            }
        };
        task.setOnSucceeded(event -> {
            // Leave the guarded busy state before asking close(boolean) to close the
            // dialog. Keeping busy=true here made close(true) return immediately, so
            // the password was persisted while the screen spun forever.
            setBusy(false);
            try {
                session.updateCurrentUser(task.getValue());
                clearSensitiveInput();
                close(true);
            } catch (RuntimeException failure) {
                AllAlerts.handleError(text("password.change.operation"), failure);
                showStatus("password.change.error.unexpected", true);
            }
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            if (failure instanceof PasswordChangeException validation) {
                showValidation(validation.messageKey());
            } else {
                AllAlerts.handleError(text("password.change.operation"), failure);
                showStatus("password.change.error.unexpected", true);
            }
        });
        task.setOnCancelled(event -> setBusy(false));

        Thread thread = new Thread(task, "password-change-" + userId);
        thread.setDaemon(true);
        thread.start();
    }

    private void showValidation(String messageKey) {
        clearFieldErrors();
        if ("password.incorrect".equals(messageKey)
                || "password.change.error.current.required".equals(messageKey)) {
            currentPassword.clear();
            markInvalid(currentPassword, currentPasswordVisible);
            focusCurrentPassword();
        } else if ("password.change.error.new.required".equals(messageKey)
                || "user.password.minimum".equals(messageKey)
                || "password.change.error.reused".equals(messageKey)) {
            markInvalid(newPassword, newPasswordVisible);
            focusNewPassword();
        } else if ("password.change.error.confirmation.required".equals(messageKey)
                || "password.mismatch".equals(messageKey)) {
            markInvalid(confirmationPassword, confirmationPasswordVisible);
            focusConfirmation();
        } else if ("password.change.error.timeout".equals(messageKey)) {
            // A timed-out update has an intentionally unknown outcome: clear all
            // credentials so the operator cannot repeat it with one accidental click.
            clearSensitiveInput();
            focusCurrentPassword();
        }
        showStatus(messageKey, true);
    }

    private void markInvalid(TextInputControl... fields) {
        for (TextInputControl field : fields) {
            if (!field.getStyleClass().contains("validation-error")) {
                field.getStyleClass().add("validation-error");
            }
        }
    }

    private void clearFieldErrors() {
        for (TextInputControl field : List.of(currentPassword, currentPasswordVisible,
                newPassword, newPasswordVisible, confirmationPassword, confirmationPasswordVisible)) {
            field.getStyleClass().remove("validation-error");
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        currentPassword.setDisable(value);
        currentPasswordVisible.setDisable(value);
        newPassword.setDisable(value);
        newPasswordVisible.setDisable(value);
        confirmationPassword.setDisable(value);
        confirmationPasswordVisible.setDisable(value);
        showPasswords.setDisable(value);
        cancelButton.setDisable(value);
        busyIndicator.setVisible(value);
        busyIndicator.setManaged(value);
        saveButton.setDisable(value || !form().isReady());
    }

    private void showStatus(String messageKey, boolean error) {
        statusLabel.setText(text(messageKey));
        statusLabel.getStyleClass().removeAll("password-status-error", "password-status-info");
        statusLabel.getStyleClass().add(error ? "password-status-error" : "password-status-info");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private void clearSensitiveInput() {
        currentPassword.clear();
        newPassword.clear();
        confirmationPassword.clear();
    }

    private void focusCurrentPassword() {
        (showPasswords.isSelected() ? currentPasswordVisible : currentPassword).requestFocus();
    }

    private void focusNewPassword() {
        (showPasswords.isSelected() ? newPasswordVisible : newPassword).requestFocus();
    }

    private void focusConfirmation() {
        (showPasswords.isSelected() ? confirmationPasswordVisible : confirmationPassword).requestFocus();
    }

    private void close(boolean changed) {
        if (dialog == null || busy) return;
        if (!changed) clearSensitiveInput();
        dialog.setResult(changed);
        dialog.close();
    }

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }
}
