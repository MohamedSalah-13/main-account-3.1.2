package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.reports.YearlyReportController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * The yearly report's window: a stage of its own rather than a dialog, so the main window stays usable
 * beside a report someone reads for a while. The sidebar button and the reports hub both open it here.
 */
public class ReportTotalYearlyApplication extends Application {

    // A method, not a baked-in static final: the old literal was read once at class
    // load and never reflected a later language switch.
    public static String yearlyReportName() {
        return LanguageManager.getInstance().getString("report.yearly.title");
    }

    private final Pane pane;

    /** The factory is not read: the report builds its own collaborators. Kept so both callers stay as they are. */
    public ReportTotalYearlyApplication(DaoFactory daoFactory) {
        pane = YearlyReportController.standard().pane();
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new SceneAll(pane);
        stage.setScene(scene);
        stage.setTitle(yearlyReportName());
        stage.getIcons().add(new Image(new Image_Setting().reports));
        stage.setResizable(true);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }
}
