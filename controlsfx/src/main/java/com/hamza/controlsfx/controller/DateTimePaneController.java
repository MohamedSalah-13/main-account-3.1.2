package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.file.FileDir;
import com.hamza.controlsfx.language.StringConstants;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

public class DateTimePaneController {

    @FXML
    private Label labelDate, labelTime, labelSystem, labelNameWindows;
    @FXML
    private Text textDate, textTime, textSystem, textNameWindows;
    @FXML
    private ProgressBar progress;
    @FXML
    private Text textProgress, textShowData;

    @FXML
    private void initialize() {

        textDate.setText(String.valueOf(LocalDate.now()));
        getTime(textTime);
        textSystem.setText(FileDir.OPERATE_SYSTEM);
        textNameWindows.setText(FileDir.USER_NAME);

        labelDate.setText(StringConstants.DATE);
        labelTime.setText(StringConstants.TIME);
        labelSystem.setText(StringConstants.SYSTEM);
        labelNameWindows.setText(StringConstants.COMPUTER_NAME);
        progress.setVisible(false);
    }

    public void startPro(Task<Void> task) {
        progress.setVisible(true);
        progress.progressProperty().bind(task.progressProperty());
        textProgress.textProperty().bind(task.messageProperty().concat("%"));
        textShowData.textProperty().bind(task.titleProperty());
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        task.setOnSucceeded(workerStateEvent -> progress.setVisible(false));
    }

    @SuppressWarnings("BusyWait")
    private void getTime(Text text) {
        Thread timerThread = new Thread(() -> {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            while (true) {
                try {
                    Thread.sleep(1000); //1 second
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                final String time = simpleDateFormat.format(new Date());
                Platform.runLater(() -> text.setText(time));
            }
        });
        timerThread.start();//start the thread and its ok
    }
}
