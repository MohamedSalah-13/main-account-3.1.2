package com.hamza.account.controller.setting;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.backupPane.DatabaseBackup;
import com.hamza.controlsfx.controller.MaskerPaneSetting;
import com.hamza.controlsfx.interfaceData.DownloadTask;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.util.DirectoryChooserApp;
import com.hamza.controlsfx.util.FileDir;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.*;

@Log4j2
@RequiredArgsConstructor
public class BackupController<V> implements Initializable {

    public static final java.time.format.DateTimeFormatter BACKUP_TIMESTAMP_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    public static final String BACKUP_FILE_PREFIX = "database_";
    public static final String BACKUP_FILE_EXTENSION = ".sql";
    private final DatabaseBackup databaseBackup;
    private final DownloadTask progressFinishTask;
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
        String stringBackup = Error_Text_Show.BACKUP;
        textTitle.setText(stringBackup);
        btnChooseFile.setText(Error_Text_Show.CHOOSE);
        btnRestore.setText(Error_Text_Show.RECOVERY);
        btnBackup.setText(stringBackup);
        btnBackup.setMinWidth(100);

        labelHour.setText(Error_Text_Show.HOUR);
        labelSaveFolder.setText(Error_Text_Show.WHERE_TO_SAVE);
        labelDateSaveTime.setText(Error_Text_Show.SAVING_TIME);
        checkSaveBeforeClose.setText(Error_Text_Show.SAVE_BEFORE_CLOSE);
        automaticCopying.setText(Error_Text_Show.AUTOMATIC_BACKUP);

        btnChooseFile.disableProperty().bind(automaticCopying.selectedProperty().not());
        textField.disableProperty().bind(automaticCopying.selectedProperty().not());
        spinner.disableProperty().bind(automaticCopying.selectedProperty().not());

        checkSaveBeforeClose.setSelected(getBackupDatabaseSaveBeforeClose());
        checkSaveBeforeClose.selectedProperty().addListener((observable, oldValue, newValue) -> {
            setBackupDatabaseSaveBeforeClose(newValue);
        });

        automaticCopying.setSelected(getBackupDatabaseSaveAutomatic());
        automaticCopying.selectedProperty().addListener((observable, oldValue, newValue) -> {
            setBackupDatabaseSaveAutomatic(newValue);
        });


        textField.setText(getBackupDatabaseSaveFolder());
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            setBackupDatabaseSaveFolder(newValue);
        });
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
                setBackupDatabaseTimeBackup(newValue);
            }
        });
    }

    private void scheduleBackup() {
        Thread backupThread = new Thread(() -> {
            while (automaticCopying.selectedProperty().get()) {
                try {
                    performScheduledBackup();
                    // Sleep for specified hours
                    Thread.sleep(Duration.ofHours(spinner.getValue()).minusSeconds(1).toMillis());
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
        if (textField.getText() != null && !textField.getText().isEmpty()) {
            try {
                int nextBackupHour = (LocalTime.now().getHour() + spinner.getValue()) % 24;
                String filePath = textField.getText() + File.separator + backupFiles.generateBackupFileName();
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
        btnRestore.setOnAction(actionEvent -> backupFiles.restoreAction(maskerPaneSetting, new SimpleBooleanProperty()));

        btnChooseFile.setOnAction(actionEvent -> {
            File dirTo = new DirectoryChooserApp().chooseDirectory(FileDir.whereToSaveFile(""));
            if (dirTo != null) {
                textField.setText(dirTo.getPath());
            }
        });

    }

}
