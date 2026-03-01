package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.interfaceData.DownloadTask;
import com.hamza.controlsfx.language.StringConstants;
import com.hamza.controlsfx.resize.Opacity_Move_Stage;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.util.ResourceBundle;

@Log4j2
public class DownLoadScreenController implements Initializable {

    private final Task<Void> task;
    private final DownloadTask downloadTask;
    @FXML
    protected ProgressBar progress;
    @FXML
    protected Text textProgress, textShowData;
    @FXML
    protected Text textStart, textCenter;
    @FXML
    protected StackPane pane;

    public DownLoadScreenController(Task<Void> task, DownloadTask downloadTask) {
        this.task = task;
        this.downloadTask = downloadTask;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
//        ResourceLanguage.setLanguage(getLanguage());
        textStart.setText(StringConstants.PLEASE_WAIT);
        textCenter.setText(StringConstants.DOWNLOAD_THE_DATABASE);
        new Opacity_Move_Stage(pane);


        progress.progressProperty().bind(task.progressProperty());
        textProgress.textProperty().bind(task.messageProperty().concat("%"));
        textShowData.textProperty().bind(task.titleProperty());
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
        task.setOnFailed(workerStateEvent -> log.error(workerStateEvent.getSource().getException().getMessage()));
        task.setOnSucceeded(workerStateEvent -> {
            downloadTask.onSucceededProgressFinished(workerStateEvent);
            Stage window = (Stage) progress.getScene().getWindow();
            window.close();
        });
    }


}
