package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.reports.ReportTotalByYearController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.InputStream;

@Getter
public class ReportTotalYearlyApplication extends Application {

    // A method, not a baked-in static final: the old literal was read once at class
    // load and never reflected a later language switch.
    public static String yearlyReportName() {
        return LanguageManager.getInstance().getString("report.yearly.title");
    }

    private final InputStream reports = new Image_Setting().reports;
    private final Pane pane;

    public ReportTotalYearlyApplication(DaoFactory daoFactory) throws Exception {
        pane = new OpenFxmlApplication(new ReportTotalByYearController(daoFactory)).getPane();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(pane);
        stage.setScene(scene);
        stage.setTitle(yearlyReportName());
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().reports));
        stage.setResizable(true);
//        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);
    }
}
