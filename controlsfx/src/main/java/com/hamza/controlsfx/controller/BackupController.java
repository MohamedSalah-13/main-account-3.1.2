package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.backupPane.DatabaseBackup;
import com.hamza.controlsfx.util.FileDir;
import com.hamza.controlsfx.util.DirectoryChooserApp;
import com.hamza.controlsfx.interfaceData.DownloadTask;
import com.hamza.controlsfx.language.StringConstants;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ResourceBundle;

/**
 * The {@code BackupController} class is responsible for managing backup and restoration
 * functionalities in the application.
 * <p>
 * This class provides the ability to:
 * - Perform manual and automatic backups of a database.
 * - Restore previous backups from stored files.
 * - Configure backup-related settings such as enabling automatic backup, setting backup intervals,
 *   and specifying the backup file destination.
 * - Manage tasks related to progress tracking for backup and restore processes.
 * <p>
 * The controller handles UI interactions for backup operations by linking JavaFX components
 * (such as buttons, text fields, and checkboxes) and providing appropriate logic to control
 * backup-related behavior.
 * <p>
 * It implements {@link Initializable}, ensuring that initialization logic for JavaFX controls
 * is executed during the application startup.
 *
 * @param <V> The result type of the {@link Task} used to track progress operations.
 */
@Log4j2
@RequiredArgsConstructor
public class BackupController<V> implements Initializable {

    public static final java.time.format.DateTimeFormatter BACKUP_TIMESTAMP_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static final String BACKUP_FILE_PREFIX = "database_";
    public static final String BACKUP_FILE_EXTENSION = ".sql";

    @Getter
    private final BooleanProperty saveBeforeClose = new SimpleBooleanProperty();
    /**
     * Represents the saved file location where backups are stored.
     * This variable holds the file path as a String and is implemented as a JavaFX {@code StringProperty}
     * to allow binding and listening for changes in the file path.
     */
    private final StringProperty savedLocation = new SimpleStringProperty(""); // this use for where backup saved
    /**
     * Represents the hour of the day when an automatic backup is scheduled to occur.
     * The value is stored as an {@code IntegerProperty}, allowing for binding and observation.
     * <p>
     * Default value is set to 1, representing 1 AM.
     */
    private final IntegerProperty backupTimeHour = new SimpleIntegerProperty(1);
    /**
     * A property to manage the activation status of automatic backup functionality.
     * This BooleanProperty allows for listening and binding changes related to
     * enabling or disabling the backup feature in the application.
     */
    private final BooleanProperty activateBackup = new SimpleBooleanProperty(); // this use for automatic backup
    /**
     * Represents a boolean property that indicates the completion of a save operation.
     * This field is primarily used to determine whether an automatic backup should occur
     * after a save is performed. The property is initialized with a default value of false.
     */
    private final BooleanProperty afterSaved = new SimpleBooleanProperty(false); // this use for automatic backup
    /**
     * Represents an instance of {@link DatabaseBackup} interface used for managing database
     * backup and restore operations within the BackupController.
     *
     * <p>This variable is responsible for delegating backup and restore
     * processes to the implementation of the {@link DatabaseBackup} interface.
     * It ensures that the BackupController can utilize the defined methods to
     * perform operations such as storing a backup or restoring a database.
     *
     * <p>It is initialized as a final field, meaning its value is immutable
     * and must be assigned during construction of the containing class.
     */
    private final DatabaseBackup databaseBackup;
    /**
     * Represents the task to execute upon the successful completion of a progress operation.
     * This variable holds a reference to an implementation of {@link DownloadTask} that defines
     * the behavior to be executed once the progress operation has finished successfully.
     */
    private final DownloadTask progressFinishTask;
    /**
     * Represents a task to monitor or manage the progress of an operation.
     * This task is used within the BackupController to handle asynchronous
     * operations related to backup processes, potentially displaying progress
     * updates or managing workflow states.
     * <p>
     * The `progressTask` is typically utilized in conjunction with UI elements
     * or functional components to signify ongoing activities such as file
     * backup or restoration.
     */
    private final Task<V> progressTask;
    @FXML
    private CheckBox automaticCopying, checkSaveBeforeClose;
    @FXML
    private Text textTitle;
    @FXML
    private TextField textField;
    @FXML
    private Spinner<Integer> spinner;
    @FXML
    private Label labelHour, labelDateSaveTime, labelSaveFolder;
    @FXML
    private Button btnRestore, btnBackup, btnChooseFile;
    @FXML
    private StackPane pane;
    private MaskerPaneSetting maskerPaneSetting;
    private BackupFiles<V> backupFiles;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        backupFiles = new BackupFiles<>(databaseBackup, progressFinishTask, progressTask);
        maskerPaneSetting = new MaskerPaneSetting(pane);
        otherSetting();
        action();
        spinnerBackupTime();
        scheduleBackup();
    }

    private void otherSetting() {
        String stringBackup = StringConstants.BACKUP;
        textTitle.setText(stringBackup);
        btnChooseFile.setText(StringConstants.CHOOSE);
        btnRestore.setText(StringConstants.RECOVERY);
        btnBackup.setText(stringBackup);
        btnBackup.setMinWidth(100);

        labelHour.setText(StringConstants.HOUR);
        labelSaveFolder.setText(StringConstants.WHERE_TO_SAVE);
        labelDateSaveTime.setText(StringConstants.SAVING_TIME);
        checkSaveBeforeClose.setText(StringConstants.SAVE_BEFORE_CLOSE);
        automaticCopying.setText(StringConstants.AUTOMATIC_BACKUP);

        textField.textProperty().bindBidirectional(savedLocationProperty());
        automaticCopying.selectedProperty().bindBidirectional(activateBackupProperty());
        saveBeforeClose.bindBidirectional(checkSaveBeforeClose.selectedProperty());
        btnChooseFile.disableProperty().bind(automaticCopying.selectedProperty().not());
        textField.disableProperty().bind(automaticCopying.selectedProperty().not());
        spinner.disableProperty().bind(automaticCopying.selectedProperty().not());
    }

    private void spinnerBackupTime() {
        ObservableList<Integer> years = FXCollections.observableArrayList();
        for (int i = 1; i < 13; i++) {
            years.add(i);
        }

        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.ListSpinnerValueFactory<>(years);
        spinner.setValueFactory(valueFactory);
        spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                setBackupTimeHour(newValue);
            }
        });
    }

    private void scheduleBackup() {
        Thread backupThread = new Thread(() -> {
            while (activateBackup.get()) {
                try {
                    performScheduledBackup();
                    // Sleep for specified hours
//                    Thread.sleep((long) getBackupTimeHour() * 60 * 60 * 1000);
                    Thread.sleep(Duration.ofHours(getBackupTimeHour()).minusSeconds(1).toMillis());
                } catch (InterruptedException e) {
                    log.error("Backup thread interrupted: {}", e.getMessage(), e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        backupThread.setDaemon(true);
        backupThread.start();
    }

    private void performScheduledBackup() {
        if (getSavedLocation() != null && !getSavedLocation().isEmpty()) {
            try {
                int nextBackupHour = (LocalTime.now().getHour() + getBackupTimeHour()) % 24;
                String filePath = getSavedLocation() + File.separator + backupFiles.generateBackupFileName();
                boolean success = databaseBackup.backup(filePath);
                if (success) {
                    log.info("Scheduled backup completed successfully at hour: {}", nextBackupHour);
                    AllAlerts.alertSave();
                }
            } catch (IOException | InterruptedException e) {
                log.error("Scheduled backup failed: {}", e.getMessage(), e);
                AllAlerts.showExceptionDialog(e);
            }
        }
    }

    private void action() {
        btnBackup.setOnAction(actionEvent -> backupFiles.backupAction(maskerPaneSetting));
        btnRestore.setOnAction(actionEvent -> backupFiles.restoreAction(maskerPaneSetting, afterSaved));

        btnChooseFile.setOnAction(actionEvent -> {
            File dirTo = new DirectoryChooserApp().chooseDirectory(FileDir.whereToSaveFile(""));
            if (dirTo != null) {
                textField.setText(dirTo.getPath());
            }
        });

        activateBackup.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                scheduleBackup();
            }
        });
    }

    public boolean isActivateBackup() {
        return activateBackup.get();
    }

    public void setActivateBackup(boolean activateBackup) {
        this.activateBackup.set(activateBackup);
    }

    public BooleanProperty activateBackupProperty() {
        return activateBackup;
    }

    public String getSavedLocation() {
        return savedLocation.get();
    }

    public void setSavedLocation(String savedLocation) {
        this.savedLocation.set(savedLocation);
    }

    public StringProperty savedLocationProperty() {
        return savedLocation;
    }

    public IntegerProperty backupTimeHourProperty() {
        return backupTimeHour;
    }

    public int getBackupTimeHour() {
        return backupTimeHour.get();
    }

    public void setBackupTimeHour(int backupTimeHour) {
        this.backupTimeHour.set(backupTimeHour);
    }

    public boolean isAfterSaved() {
        return afterSaved.get();
    }

    public void setAfterSaved(boolean afterSaved) {
        this.afterSaved.set(afterSaved);
    }

    public BooleanProperty afterSavedProperty() {
        return afterSaved;
    }
}
