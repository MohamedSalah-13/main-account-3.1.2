package com.hamza.account.controller.dbsetup;

import com.hamza.account.config.DatabaseConfigFiles;
import com.hamza.account.features.dbsetup.DatabaseConnectionSettings;
import com.hamza.account.features.dbsetup.DatabaseProbeResult;
import com.hamza.account.features.dbsetup.DatabaseSetupException;
import com.hamza.account.features.dbsetup.DatabaseSetupService;
import com.hamza.account.features.dbsetup.DatabaseServerProvisioningRequest;
import com.hamza.account.features.dbsetup.DatabaseServerSetupService;
import com.hamza.account.features.dbsetup.JdbcDatabaseConnectionProbe;
import com.hamza.account.features.dbsetup.JdbcDatabaseServerProvisioner;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/** Thin JavaFX adapter for the standalone database setup utility. */
public final class DatabaseSetupController {

    @FXML private RadioButton mainDevice;
    @FXML private RadioButton clientDevice;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField databaseField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private VBox serverProvisioningPane;
    @FXML private TextField administratorUsernameField;
    @FXML private PasswordField administratorPasswordField;
    @FXML private TextField provisionUsernameField;
    @FXML private PasswordField provisionPasswordField;
    @FXML private TextField allowedHostField;
    @FXML private Button testButton;
    @FXML private Button saveButton;
    @FXML private Button provisionButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label locationLabel;
    @FXML private Label statusLabel;

    private final DatabaseSetupService service =
            new DatabaseSetupService(new JdbcDatabaseConnectionProbe());
    private final DatabaseServerSetupService serverSetupService =
            new DatabaseServerSetupService(new JdbcDatabaseServerProvisioner());
    private DatabaseConnectionSettings lastTested;

    @FXML
    private void initialize() {
        portField.setText("3306");
        databaseField.setText("account_system_db");
        administratorUsernameField.setText("root");
        provisionUsernameField.setText("account_main");
        allowedHostField.setText("localhost");
        portField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,5}") ? change : null));
        locationLabel.setText(DatabaseConfigFiles.preferred().directory().toString());

        mainDevice.selectedProperty().addListener((observable, oldValue, selected) -> updateDeviceType());
        clientDevice.selectedProperty().addListener((observable, oldValue, selected) -> updateDeviceType());
        hostField.textProperty().addListener((observable, oldValue, value) -> markChanged());
        portField.textProperty().addListener((observable, oldValue, value) -> markChanged());
        databaseField.textProperty().addListener((observable, oldValue, value) -> markChanged());
        usernameField.textProperty().addListener((observable, oldValue, value) -> markChanged());
        passwordField.textProperty().addListener((observable, oldValue, value) -> markChanged());
        whenEnterPressed(hostField, portField, databaseField, usernameField, passwordField, testButton);
        whenEnterPressed(administratorUsernameField, administratorPasswordField, provisionUsernameField,
                provisionPasswordField, allowedHostField, provisionButton);
        updateDeviceType();
    }

    private void updateDeviceType() {
        boolean local = mainDevice.isSelected();
        hostField.setDisable(local);
        serverProvisioningPane.setDisable(!local);
        if (local) {
            hostField.setText("localhost");
        } else if ("localhost".equalsIgnoreCase(hostField.getText())) {
            hostField.clear();
        }
        markChanged();
    }

    private void markChanged() {
        lastTested = null;
        saveButton.setDisable(true);
        clearStatus();
    }

    @FXML
    private void testConnection() {
        DatabaseConnectionSettings settings = settingsOrShowError();
        if (settings == null) {
            return;
        }
        run(() -> service.test(settings), result -> connectionSucceeded(settings, result));
    }

    @FXML
    private void saveConfiguration() {
        DatabaseConnectionSettings settings = settingsOrShowError();
        if (settings == null) {
            return;
        }
        if (!settings.sameConnectionAs(lastTested)) {
            showStatus("dbsetup.save.test.first", "status-warning");
            saveButton.setDisable(true);
            return;
        }
        run(() -> service.save(settings), location -> {
            showStatus("dbsetup.save.success", "status-success", location.directory());
            saveButton.setDisable(true);
        });
    }

    @FXML
    private void provisionServer() {
        DatabaseServerProvisioningRequest request;
        try {
            request = serverSetupService.validate(hostField.getText(), portField.getText(),
                    databaseField.getText(), administratorUsernameField.getText(),
                    administratorPasswordField.getText(), provisionUsernameField.getText(),
                    provisionPasswordField.getText(), allowedHostField.getText());
        } catch (DatabaseSetupException invalid) {
            showStatus(invalid.messageKey(), "status-error");
            return;
        }

        run(() -> serverSetupService.provision(request), result -> {
                    if ("localhost".equalsIgnoreCase(request.allowedHost())) {
                        usernameField.setText(request.applicationUsername());
                        passwordField.setText(request.applicationPassword());
                    }
                    showStatus("dbsetup.provision.success", "status-success",
                            result.database(), result.account());
                },
                administratorPasswordField::clear);
    }

    private void connectionSucceeded(DatabaseConnectionSettings settings, DatabaseProbeResult result) {
        lastTested = settings;
        saveButton.setDisable(false);
        showStatus(result.databaseExists()
                ? "dbsetup.test.success" : "dbsetup.test.success.database.missing",
                result.databaseExists() ? "status-success" : "status-warning");
    }

    private DatabaseConnectionSettings settingsOrShowError() {
        try {
            return service.validate(hostField.getText(), portField.getText(), databaseField.getText(),
                    usernameField.getText(), passwordField.getText());
        } catch (DatabaseSetupException invalid) {
            showStatus(invalid.messageKey(), "status-error");
            return null;
        }
    }

    private <T> void run(Callable<T> operation, Consumer<T> succeeded) {
        run(operation, succeeded, () -> { });
    }

    private <T> void run(Callable<T> operation, Consumer<T> succeeded, Runnable completed) {
        setBusy(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            completed.run();
            succeeded.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            completed.run();
            Throwable failure = task.getException();
            String key = failure instanceof DatabaseSetupException setupFailure
                    ? setupFailure.messageKey() : "dbsetup.unexpected.error";
            showStatus(key, "status-error");
        });
        Thread worker = new Thread(task, "database-setup-operation");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        testButton.setDisable(busy);
        saveButton.setDisable(busy || lastTested == null);
        provisionButton.setDisable(busy || clientDevice.isSelected());
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("status-success", "status-warning", "status-error");
    }

    private void showStatus(String key, String styleClass, Object... arguments) {
        statusLabel.setText(LanguageManager.getInstance().getString(key, arguments));
        statusLabel.getStyleClass().removeAll("status-success", "status-warning", "status-error");
        statusLabel.getStyleClass().add(styleClass);
    }
}
