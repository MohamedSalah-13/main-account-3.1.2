package com.hamza.account.view;

import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.Style_Sheet;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.alert.AlertSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.FontsSetting;
import javafx.application.Application;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

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
        new LogApplication(daoFactory, loadDataAndList).start(new Stage());
    }


}
