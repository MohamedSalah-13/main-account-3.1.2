package com.hamza.account.view;

import com.hamza.account.backup.ScheduledBackup;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.login.LoginController;
import com.hamza.account.interfaces.ActionLogin;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;

@Log4j2
public class LogApplication extends Application {
    public static final LanguageManager INSTANCE = LanguageManager.getInstance();
    public static Users usersVo;
    private final LoginController login;
    private final DaoFactory daoFactory;
    private final Scene scene;
    private boolean b = false;

    public LogApplication(DaoFactory daoFactory) throws Exception {
        this.daoFactory = daoFactory;
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("login.fxml"), LanguageManager.getInstance().getResourceBundle());
        login = new LoginController(new ActionLogin() {
            @Override
            public boolean action(String username, String password) throws Exception {
                return onEnter(username, password);
            }
        });
        fxmlLoader.setController(login);
        scene = new Scene(fxmlLoader.load());
//        Style_Sheet.changeStyle(scene);
        ThemeManager.apply(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {
        // Block the application and show progress while loading items
        if (PropertiesName.getSettingLoginShow()) {
            ChangeOrientation.sceneOrientation(scene);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setOnCloseRequest(windowEvent -> System.exit(0));
            stage.setTitle(INSTANCE.getString("common.login.screen"));
            stage.getIcons().add(new Image(new Image_Setting().tools));
            stage.show();
//            afterSuccessfulLogin(usersVo, daoFactory);
        } else {
            LogApplication.usersVo = daoFactory.usersDao().getDataById(1);
            openMainScreen();
            stage.close();
        }
    }

    protected boolean onEnter(String username, String password) throws Exception {
        Optional<Users> list = daoFactory.usersDao().getUserByNameAndPassword(username, password);

        list.ifPresentOrElse(users -> {
            try {
                usersVo = users;
                if (usersVo.getId() != 1 && !usersVo.isActive()) {
                    throw new Exception(Setting_Language.THIS_NAME_IS_INACTIVE);

                }

                new Thread(() -> {
                    // scheduled backup
                    if (ScheduledBackup.getTime() > 0)
                        ScheduledBackup.startScheduler(DownLoadApplication.loadBackupService());
                }).start();

                // load mainscreen
                openMainScreen();
                b = true;
            } catch (Exception e) {
                login.setResetAllData(true);
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        }, this::alertErrorAndResetData);
        return b;
    }

    private void alertErrorAndResetData() {
        AllAlerts.alertError(Setting_Language.NO_NAME);
        login.setResetAllData(true);
    }


    private void openMainScreen() throws Exception {
        updateData();
        var mainScreenApplication = new MainScreenApplication(daoFactory);
        mainScreenApplication.start(new Stage());
    }

    private void updateData() {
        Thread thread = new Thread(() -> {
            try {
//                saveUserSetting();
                Thread.sleep(500);
                usersVo.setUser_available(1);
                int i = daoFactory.usersDao().updateAvailable(usersVo);
                if (i == 1) log.info(Error_Text_Show.DONE_READING_FROM_FILE, "user login");
                else log.error("User not found");
            } catch (InterruptedException | DaoException e) {
                log.error(e.getMessage(), e.getCause());
            }

        });
        thread.start();
    }

}
