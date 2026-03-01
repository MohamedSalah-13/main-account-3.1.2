package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.target.TargetsController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.StageDimensions;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TargetApplication extends Application {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final String name;

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new TargetsController(daoFactory, dataPublisher, name).pane());
        stage.setScene(scene);
        stage.setTitle(Setting_Language.TARGET);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
        stage.setResizable(true);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);
    }
}
