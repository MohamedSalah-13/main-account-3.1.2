package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.config.Style_Sheet;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.model.domain.Users_Permission;
import com.hamza.account.service.UserPermissionService;
import com.hamza.account.service.UserShiftService;
import com.hamza.account.session.ShiftContextLoader;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.view.LoginApplication;
import javafx.application.Application;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Log4j2
public class LogApplication extends Application {

    public static Users usersVo;
    public static List<Users_Permission> usersPermissionList;
    private final LoginApplication login;
    private final DaoFactory daoFactory;
    private final LoadDataAndList loadDataAndList;
    private boolean b = false;

    public LogApplication(DaoFactory daoFactory, LoadDataAndList loadDataAndList) throws Exception {
        this.daoFactory = daoFactory;
        this.loadDataAndList = loadDataAndList;
        login = new LoginApplication(this::onEnter);
        login.setStageTitle(Setting_Language.LOGIN_SCREEN);
        login.setInputStream(new Image_Setting().tools);
        Style_Sheet.changeStyle(login.getScene());
    }

    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {
        // Block the application and show progress while loading items
        if (PropertiesName.getSettingLoginShow())
            login.start(stage);
        else {
            LogApplication.usersVo = daoFactory.usersDao().getDataById(1);
            // مثال للتكامل — ضعه بعد تعيين LogApplication.usersVo
            ShiftContextLoader.loadForUser(usersVo.getId(), new UserShiftService(daoFactory));
            openMainScreen();
            stage.close();
        }
    }

    /**
     * Handles the entry attempt by a user with the given username and password.
     * Validates the user's credentials, checks activity status, and opens the main screen if authentication is successful.
     *
     * @param username the username entered by the user
     * @param password the password entered by the user
     * @return true if the user's authentication is successful and main screen is opened; false otherwise
     * @throws Exception if the username does not exist, the user is inactive, or the password is incorrect
     */
    protected boolean onEnter(String username, String password) throws Exception {
        Optional<Users> list = daoFactory.usersDao().loadAll().stream().filter(users -> users.getUsername().equals(username)).findFirst();

        list.ifPresentOrElse(users -> {
            try {
                usersVo = users;
                if (usersVo.getId() != 1 && !usersVo.isActive()) {
                    throw new Exception(Setting_Language.THIS_NAME_IS_INACTIVE);

                }
                if (!usersVo.getPasswordHash().equals(password)) {
                    throw new Exception(Setting_Language.THE_PASSWORD_IS_INCORRECT);
                }
                openMainScreen();
                b = true;
            } catch (Exception e) {
                login.getLoginController().setResetAllData(true);
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        }, this::alertErrorAndResetData);
        return b;
    }

    /**
     * Alerts the user with an error message and resets all login-related data.
     * <p>
     * This method is invoked when the system encounters an error scenario where the
     * provided username does not exist. It uses the `AllAlerts` utility to show an
     * error message specified by the `Setting_Language.NO_NAME` constant. Additionally,
     * it calls the login controller to reset all data to ensure a clean state for
     * the next login attempt.
     */
    private void alertErrorAndResetData() {
        AllAlerts.alertError(Setting_Language.NO_NAME);
        login.getLoginController().setResetAllData(true);
    }

    /**
     * Opens the main screen of the application.
     * <p>
     * This method is triggered after a successful login and performs
     * the following operations:
     * 1. Updates user data by calling the {@link #updateData()} method.
     * 2. Initializes and starts the {@link MainScreenApplication}
     * with the DAO factory and data loader.
     *
     * @throws IOException if an I/O error occurs while loading the main screen.
     */
    private void openMainScreen() throws Exception {
        updateData();
        usersPermissionList = new UserPermissionService(daoFactory).getUsersPermissionById(usersVo.getId());
        var mainScreenApplication = new MainScreenApplication(daoFactory, loadDataAndList);
        mainScreenApplication.start(new Stage());
    }

    /**
     * Updates the availability status of the current user in a separate thread.
     * <p>
     * The method changes the current user's availability status to available (1)
     * and attempts to update this change in the persistent storage using the
     * usersDao. If the update is successful, a log entry indicating successful
     * reading from the file is recorded. Otherwise, an error message is printed to
     * the console.
     * <p>
     * Handles InterruptedException and DaoException, logging any error messages.
     */
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
