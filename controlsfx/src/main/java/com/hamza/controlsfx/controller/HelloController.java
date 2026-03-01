package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.language.StringConstants;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HelloController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() throws Exception {
        welcomeText.setText(StringConstants.OK);
//        welcomeText.setText("Welcome to JavaFX Application!");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i <= 10; i++) {
                    Thread.sleep(5000);
                    updateProgress(i, 10);
                    updateMessage(" progress " + i);
                }
                return null;
            }

        };

        Thread thread = new Thread(task);
        thread.start();
//        new ProgressbarApplication<>(task, workerStateEvent -> System.out.println(true)).start(new Stage());

    }
}