package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ProcessesController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.StageDimensions;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProcessorApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new OpenFxmlApplication(new ProcessesController()).getPane());
        stage.setScene(scene);
        stage.setTitle(Setting_Language.PROCESS);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().setting));
        stage.setResizable(true);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);
    }
}
