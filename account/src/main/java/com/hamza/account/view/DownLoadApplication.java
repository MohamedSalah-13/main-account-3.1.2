package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.backup.EncryptionUtil;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.Style_Sheet;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.alert.AlertSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.FontsSetting;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;

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
//        new LogApplication(daoFactory, loadDataAndList).start(new Stage());
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/BackupView.fxml"));
            Parent root = loader.load();
            com.hamza.account.backup.BackupController controller = loader.getController();
            controller.initConnection("localhost", "3306", "account_system_db", "root", "m13ido");
//            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.show();

//            EncryptionUtil.decryptFile(new File("E:\\الضريبة\\backup_20260429_132655.enc"),new File("test"), "123");
//            System.out.println("Prefs path: " + System.getProperty("java.util.prefs.userRoot"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
