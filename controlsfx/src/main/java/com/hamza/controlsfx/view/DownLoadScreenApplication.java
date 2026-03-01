package com.hamza.controlsfx.view;

import com.hamza.controlsfx.controller.DownLoadScreenController;
import com.hamza.controlsfx.interfaceData.DownloadTask;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;

public class DownLoadScreenApplication extends Application {

    @Getter
    private final Parent parent; // get parent of load
    @Setter
    private InputStream iconStage; // set icon for stage

    public DownLoadScreenApplication(Task<Void> task, DownloadTask downloadTask) throws IOException {
        DownLoadScreenController screenController = new DownLoadScreenController(task, downloadTask);
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("download-view.fxml"));
        fxmlLoader.setController(screenController);
        parent = fxmlLoader.load();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new Scene(parent);
        ChangeOrientation.sceneOrientation(scene);
        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);
        scene.setFill(Color.TRANSPARENT);

        if (iconStage != null) // Check the image if it exists or not
            stage.getIcons().add(new Image(iconStage));

        // Make it modal and block until the task completes
//        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
    }

}
