package com.hamza.account.view;

import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.Style_Sheet;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.alert.AlertSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.FontsSetting;
import com.hamza.controlsfx.interfaceData.DownloadTask;
import com.hamza.controlsfx.view.DownLoadScreenApplication;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import static com.hamza.account.config.Configs.IS_DOWNLOAD_TASK;

@Log4j2
public class DownLoadApplication extends Application {

    @Getter
    private final LoadDataAndList loadDataAndList;
    private final DaoFactory daoFactory;

    public DownLoadApplication() {
        FontsSetting.fontName(FontsSetting.EL_MESSIRI);
        FontsSetting.fontName(FontsSetting.GRAND_HOTEL);
        FontsSetting.fontName(FontsSetting.NEW_ROCKER);
        FontsSetting.fontName(FontsSetting.GAFATA);

        // change language
        daoFactory = getDaoFactory();
        loadDataAndList = new LoadDataAndList(daoFactory);
        AlertSetting.stylesheetPath = Style_Sheet.getStyle();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static DaoFactory getDaoFactory() {
        try {
            DaoFactory daoFactory = DaoFactory.INSTANCE;
            var connection = new ConnectionToDatabase().getDbConnection().getConnection();
            daoFactory.setConnection(connection);
//            new TrialManager(connection).checkTrialStatus();
            return daoFactory;
        } catch (DaoException e) {
            AllAlerts.alertError(e.getMessage());
            System.exit(0);
        }
        return null;
    }

    @Override
    public void start(Stage stage) throws Exception {
        if (!IS_DOWNLOAD_TASK) {
            new LogApplication(daoFactory, loadDataAndList).start(new Stage());
            return;
        }

        DownloadTask downloadTask = workerStateEvent -> {
            try {
                new LogApplication(daoFactory, loadDataAndList).start(new Stage());
            } catch (Exception e) {
                log.error(e.getMessage(), e.getCause());
            }
            Task<Void> voidTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    Thread.sleep(100);
                    return null;
                }
            };
            new Thread(voidTask).start();
        };

        DownLoadScreenApplication downLoadScreen = new DownLoadScreenApplication(loadDataAndList, downloadTask);
        downLoadScreen.getParent().getStylesheets().add(Style_Sheet.getStyle());
        downLoadScreen.setIconStage(new Image_Setting().tools);
        downLoadScreen.start(stage);

    }


}
