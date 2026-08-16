package com.hamza.account.view;

import com.hamza.account.backup.ScheduledBackup;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.login.LoginController;
import com.hamza.account.controller.login.LoginResult;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.RbacService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.period.PeriodLockService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;
import java.util.function.Consumer;

/** Builds and presents the login scene; it is not a second JavaFX Application. */
@Log4j2
public final class LogApplication {

    private final DaoFactory daoFactory;
    private final Consumer<Users> onLoginSuccess;

    public LogApplication(DaoFactory daoFactory, Consumer<Users> onLoginSuccess) {
        this.daoFactory = daoFactory;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void show(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"),
                LanguageManager.getInstance().getResourceBundle());
        loader.setController(new LoginController(this::authenticate, onLoginSuccess));

        Scene scene = new Scene(loader.load());
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);

        stage.setScene(scene);
//        stage.setMinWidth(350);
//        stage.setMinHeight(350);
        stage.setResizable(true);
        stage.setTitle(LanguageManager.getInstance().getString("common.login.screen"));
        if (stage.getIcons().isEmpty()) stage.getIcons().add(new Image(new Image_Setting().tools));
        stage.setOnCloseRequest(event -> Platform.exit());
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    private LoginResult authenticate(String username, String password) throws Exception {
        Optional<Users> match = daoFactory.usersDao().getUserByNameAndPassword(username, password);
        if (match.isEmpty()) return LoginResult.invalidCredentials();

        Users user = match.get();
        if (user.getId() != 1 && !user.isActive()) return LoginResult.inactive();

        RbacService rbacService = ServiceRegistry.get(RbacService.class);
        if (rbacService == null) throw new IllegalStateException("RBAC service is not registered");

        try {
            rbacService.signIn(user);
            user.setUser_available(1);
            if (daoFactory.usersDao().updateAvailable(user) != 1) {
                throw new IllegalStateException("Signed-in user could not be marked available");
            }
            PeriodLockService.forget();

            if (ScheduledBackup.getTime() > 0) {
                try {
                    ScheduledBackup.startScheduler(DownLoadApplication.loadBackupService());
                } catch (RuntimeException e) {
                    log.error("Could not start scheduled backup after login", e);
                }
            }
            return LoginResult.success(user);
        } catch (Exception e) {
            rbacService.signOut();
            throw e;
        }
    }
}
