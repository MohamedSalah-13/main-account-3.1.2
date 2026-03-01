package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.interfaceData.DownloadTask;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.awt.*;

@Log4j2
public class ProgressPaneController<V> {

    private final Task<V> progressTask;// this use to make task of progress
    private final DownloadTask progressFinishTask; // this use to make action after progress finish
    @FXML
    private ProgressIndicator pi;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label labelProgress, labelMessage;
    @FXML
    private Button btnClose;

    protected ProgressPaneController(Task<V> progressTask, DownloadTask progressFinishTask) {
        // this task use to take database
        this.progressTask = progressTask;
        this.progressFinishTask = progressFinishTask;
    }

    @FXML
    public void initialize() {
        otherData();
    }

    private void otherData() {
        btnClose.setOnAction(actionEvent -> closeStage());
        progressBar.progressProperty().bind(progressTask.progressProperty());
        pi.progressProperty().bind(progressTask.progressProperty());
        labelProgress.textProperty().bind(progressTask.messageProperty());
        labelMessage.textProperty().bind(progressTask.titleProperty());

        progressTask.setOnFailed(wse -> log.error(wse.getSource().getException().getMessage()));
        progressTask.setOnCancelled(event -> closeStage());
        progressTask.setOnSucceeded(workerStateEvent -> {
            progressFinishTask.onSucceededProgressFinished(workerStateEvent);
            btnClose.setDisable(false);
            Toolkit.getDefaultToolkit().beep();
        });
    }

    private void closeStage() {
        Stage stage = (Stage) labelMessage.getScene().getWindow();
        stage.close();
    }

}
