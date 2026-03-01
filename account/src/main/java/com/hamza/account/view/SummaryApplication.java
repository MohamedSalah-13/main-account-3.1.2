package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.reports.SummaryController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.StageDimensions;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SummaryApplication extends Application {

    private final DaoFactory daoFactory;
    private final String name;

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(new SummaryController(daoFactory, name).pane());
        stage.setScene(scene);
        stage.setTitle(name);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().reports));
        stage.setResizable(true);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);
    }
}
