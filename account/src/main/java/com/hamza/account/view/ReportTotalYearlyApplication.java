package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.reports.ReportTotalByYearController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.StageDimensions;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.InputStream;

@Getter
public class ReportTotalYearlyApplication extends Application {

    public static final String YEARLY_REPORT_NAME = "تقرير سنوى";
    private final InputStream reports = new Image_Setting().reports;
    private final Pane pane;

    public ReportTotalYearlyApplication(DaoFactory daoFactory) throws Exception {
        pane = new OpenFxmlApplication(new ReportTotalByYearController(daoFactory)).getPane();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(pane);
        stage.setScene(scene);
        stage.setTitle(YEARLY_REPORT_NAME);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().reports));
        stage.setResizable(true);
//        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);
    }
}
